package dev.mosaicobot

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import java.net.Inet4Address
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Observe LAN networks, not Internet validation: a local-only Wi-Fi is still useful. */
class LanNetworkMonitor(context: Context, private val changed: (AndroidLan?) -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var selected: AndroidLan? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh(network)
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }
    private fun refresh(lost: Network? = null) {
        handler.post {
            if (!registered) return@post
            val candidates = cm.allNetworks.filter { it != lost }.mapNotNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@mapNotNull null
                if ((!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                val props = cm.getLinkProperties(net) ?: return@mapNotNull null
                val subnets = props.linkAddresses.filter { it.address is Inet4Address }
                    .map { LanSubnet(it.address.hostAddress!!, it.prefixLength) }.filter { it.valid }.sortedBy { it.label }
                if (subnets.isEmpty()) null else Triple(net, subnets, props.interfaceName)
            }
            val picked = candidates.firstOrNull { it.first == cm.activeNetwork }
                ?: candidates.firstOrNull { it.first == selected?.androidNetwork } ?: candidates.firstOrNull()
            val key = picked?.let { "${it.first.networkHandle}:${it.third}:${it.second.joinToString { subnet -> subnet.label }}" }
            if (selected?.key != key) {
                val previous = selected
                selected = picked?.let { AndroidLan(it.first, it.second, key!!, nsd, handler) }
                changed(selected)
                previous?.close()
            }
        }
    }
    fun start() {
        if (registered) return
        registered = true
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), callback)
            refresh()
        } catch (_: SecurityException) { registered = false; changed(null) }
    }
    fun stop() {
        if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
        changed(null)
        selected?.close()
        selected = null
    }
}

class AndroidLan(
    val androidNetwork: Network,
    private val subnets: List<LanSubnet>,
    override val key: String,
    private val nsd: NsdManager,
    private val handler: Handler,
) : DiscoveryNetwork {
    override val label = subnets.joinToString { it.label }
    // Device sockets bind only to this LAN. No process-wide binding or model proxy changes.
    private val http = networkClient().newBuilder().socketFactory(androidNetwork.socketFactory)
        .proxy(Proxy.NO_PROXY).connectionPool(ConnectionPool())
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    val client = DeviceClient(http)
    fun claw(address: String): ClawTransport {
        require(allowed(DeviceClient.address(address).host)) { "设备不在当前局域网" }
        return ClawClient(http, address)
    }
    internal fun gamepad(session: GamepadSession, address: String) {
        require(allowed(DeviceClient.address(address).host)) { "设备不在当前局域网" }
        session.connect(http, address)
    }
    private val probe = DeviceClient(http.newBuilder().connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS).callTimeout(2200, TimeUnit.MILLISECONDS).build())
    private fun allowed(host: String) = subnets.any { it.contains(host) }
    override suspend fun inspect(address: String): DeviceInfo {
        require(allowed(DeviceClient.address(address).host)) { "地址不在当前局域网内" }
        return client.inspect(address)
    }
    override suspend fun discover(preferred: String, found: (DeviceInfo) -> Unit) {
        withTimeoutOrNull(22_000) {
            val seen = mutableSetOf<String>()
            suspend fun verify(address: String) {
                try { found(probe.inspect(address)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Unrelated or offline hosts are not connection errors. */ }
            }
            val previous = runCatching { DeviceClient.address(preferred) }.getOrNull()
            if (previous != null && allowed(previous.host)) {
                seen.add(previous.toString().trimEnd('/'))
                verify(previous.toString())
            }
            coroutineScope {
                val hosts = Channel<String>(Channel.UNLIMITED)
                fun offer(address: String) {
                    val url = runCatching { DeviceClient.address(address) }.getOrNull() ?: return
                    val normalized = url.toString().trimEnd('/')
                    if (allowed(url.host) && seen.add(normalized)) hosts.trySend(normalized)
                }
                val workers = List(12) { launch { for (host in hosts) verify(host) } }
                subnets.flatMap { it.nearbyHosts() }.distinct().take(254).forEach { offer("http://" + it) }
                try { withTimeoutOrNull(4_000) { mdnsCandidates { offer("http://" + it) } } }
                finally { hosts.close() }
                workers.joinAll()
            }
        }
    }
    @Suppress("DEPRECATION")
    private suspend fun mdnsCandidates(found: (String) -> Unit) {
        val services = Channel<NsdServiceInfo>(32)
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        try {
            for (type in listOf("_esp-iris._tcp.", "_http._tcp.")) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(type: String) = Unit
                    override fun onDiscoveryStopped(type: String) = Unit
                    override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
                    override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
                    override fun onServiceLost(service: NsdServiceInfo) = Unit
                    override fun onServiceFound(service: NsdServiceInfo) { services.trySend(service) }
                }
                try {
                    if (Build.VERSION.SDK_INT >= 33) nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD,
                        androidNetwork, { command -> handler.post(command) }, listener)
                    else nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    listeners.add(listener)
                } catch (_: RuntimeException) { /* IPv4 fallback remains available. */ }
            }
            for (service in services) {
                val host = withTimeoutOrNull(750) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        val resolver = object : NsdManager.ResolveListener {
                            override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                            override fun onServiceResolved(service: NsdServiceInfo) {
                                if (continuation.isActive) continuation.resume((service.host as? Inet4Address)?.hostAddress)
                            }
                        }
                        try { nsd.resolveService(service, resolver) }
                        catch (_: RuntimeException) { if (continuation.isActive) continuation.resume(null) }
                        // Older Android has no stopServiceResolution; bounded callbacks are ignored after cancel.
                    }
                }
                if (host != null && allowed(host)) found(host)
            }
        } finally {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            services.close()
        }
    }
    fun close() { http.dispatcher.cancelAll(); http.connectionPool.evictAll() }
}
