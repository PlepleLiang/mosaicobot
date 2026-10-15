package dev.mosaicobot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface DiscoveryNetwork {
    val key: String
    val label: String
    suspend fun discover(preferred: String, found: (DeviceInfo) -> Unit)
    suspend fun inspect(address: String): DeviceInfo
}

data class ConnectionState(
    val automatic: Boolean = true,
    val networkLabel: String? = null,
    val scanning: Boolean = false,
    val device: DeviceInfo? = null,
    val found: List<DeviceInfo> = emptyList(),
    val message: String = "连接 Wi-Fi 后自动寻找 Mosaico",
)

/** Main-thread owner. Epoch checks also reject late callbacks after cancellation/network changes. */
class DeviceConnections(
    private val scope: CoroutineScope,
    private var preferred: String,
    automatic: Boolean,
    private val remember: (String) -> Unit,
) {
    private val mutable = MutableStateFlow(ConnectionState(automatic = automatic))
    val state = mutable.asStateFlow()
    var network: DiscoveryNetwork? = null
        private set
    private var foreground = false
    private var job: Job? = null
    private var epoch = 0L

    private fun invalidate() { epoch++; job?.cancel(); job = null }
    fun networkChanged(next: DiscoveryNetwork?) {
        if (network?.key == next?.key) return
        invalidate()
        network = next
        mutable.value = mutable.value.copy(networkLabel = next?.label, device = null, found = emptyList(), scanning = false,
            message = when { next == null -> "未连接可用的局域网，请连接 Wi-Fi"; !mutable.value.automatic -> "自动扫描已关闭，可手动扫描或输入地址"; else -> "网络已变化，准备扫描" })
        if (foreground && mutable.value.automatic && next != null) scan(debounce = true)
    }
    fun foreground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (active) {
            if (mutable.value.automatic) scan(debounce = true)
        } else {
            invalidate()
            mutable.value = mutable.value.copy(scanning = false, device = null, message = "回到 App 后重新确认连接")
        }
    }
    fun automatic(enabled: Boolean) {
        mutable.value = mutable.value.copy(automatic = enabled)
        if (enabled && foreground) scan() else if (!enabled) stop()
    }
    fun stop() {
        invalidate()
        mutable.value = mutable.value.copy(scanning = false, message = if (mutable.value.device == null) "扫描已停止，可手动扫描或输入地址" else "扫描已停止，保留已验证连接")
    }
    private fun connected(device: DeviceInfo) {
        preferred = device.address
        remember(preferred)
        mutable.value = mutable.value.copy(device = device, message = "已连接 Mosaico · 接口验证通过")
    }
    fun scan(debounce: Boolean = false) {
        invalidate()
        val current = network
        if (!foreground || current == null) {
            mutable.value = mutable.value.copy(scanning = false, device = null, message = "请先连接与 Mosaico 相同的 Wi-Fi")
            return
        }
        val token = epoch
        mutable.value = mutable.value.copy(scanning = true, device = null, found = emptyList(), message = "正在寻找同一局域网的 Mosaico…")
        job = scope.launch {
            try {
                if (debounce) delay(700)
                current.discover(preferred) { device ->
                    if (token == epoch && foreground) {
                        val list = (mutable.value.found + device).distinctBy { it.address }
                        mutable.value = mutable.value.copy(found = list)
                        if (mutable.value.device == null) connected(device)
                    }
                }
                if (token == epoch) mutable.value = mutable.value.copy(scanning = false, message =
                    if (mutable.value.device != null) "扫描完成 · 已自动连接，可在列表切换设备"
                    else "未发现可用 Mosaico。请确认设备已开机、在同一 Wi-Fi，或手动输入地址。")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == epoch) mutable.value = mutable.value.copy(scanning = false,
                    message = "扫描未完成，请检查局域网访问权限或手动重试")
            }
        }
    }
    fun connect(address: String) {
        invalidate()
        val current = network
        if (!foreground || current == null) {
            mutable.value = mutable.value.copy(scanning = false, device = null, message = "请先连接局域网")
            return
        }
        val token = epoch
        mutable.value = mutable.value.copy(scanning = true, device = null, message = "正在验证设备状态与 Lua 能力…")
        job = scope.launch {
            try {
                val device = current.inspect(address)
                if (token == epoch && foreground) {
                    mutable.value = mutable.value.copy(found = (mutable.value.found + device).distinctBy { it.address })
                    connected(device)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == epoch) mutable.value = mutable.value.copy(message = "连接失败：地址不可达或不是受支持的 Claw 接口，请检查地址与局域网")
            } finally { if (token == epoch) mutable.value = mutable.value.copy(scanning = false) }
        }
    }
    fun close() { invalidate() }
}
