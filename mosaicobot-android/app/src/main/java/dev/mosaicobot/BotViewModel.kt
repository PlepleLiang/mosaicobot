package dev.mosaicobot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BotState(
    val tab: Int = 0, val prompt: String = "", val messages: List<Message> = emptyList(),
    val works: List<Work> = emptyList(), val selected: Work? = null,
    val busy: Boolean = false, val stage: String = "", val error: String? = null,
    val configured: Boolean = false, val agentChecked: Boolean = false, val baseUrl: String = AgentConfig().baseUrl,
    val model: String = AgentConfig().model, val debugProxyPort: Int? = null, val address: String = "",
    val connection: ConnectionState = ConnectionState(),
    val clawOpen: Boolean = false, val gamepadOpen: Boolean = false,
    val appsGroup: Int = 0, val centerApps: List<CenterApp> = emptyList(), val centerDetail: String? = null,
    val centerUploadOpen: Boolean = false, val centerNotice: String? = null,
    val libraryReady: Boolean = false, val centerReady: Boolean = false,
    val foreground: Boolean = false, val runtime: WorkRuntime? = null, val runtimeError: String? = null,
    val deviceWorks: List<DeviceWork> = emptyList(), val deviceWorksAddress: String? = null,
    val deviceWorksLoading: Boolean = false, val deviceWorksError: String? = null,
) { val device: DeviceInfo? get() = connection.device }
class BotViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val agent = AgentClient()
    private val mutable = MutableStateFlow(BotState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var uploading = false
    private var deviceWorksEpoch = 0L
    val claw = ClawConversation(viewModelScope)
    internal val gamepad = GamepadSession(viewModelScope)
    private val connections = DeviceConnections(viewModelScope, store.address(), store.automaticScan(), store::saveAddress)
    private val networkMonitor = LanNetworkMonitor(application) { network ->
        if (connections.network?.key != network?.key) claw.disconnect("局域网已变化。对话已保留，请重新连接设备会话；不会自动重发。")
        if (connections.network?.key != network?.key) gamepad.disconnect("局域网已变化，输入已释放")
        if (connections.network?.key != network?.key && uploading) task?.cancel()
        connections.networkChanged(network)
    }
    init {
        viewModelScope.launch {
            state.map { Triple(it.device?.address,it.foreground && it.tab==1 && it.appsGroup==2 && !it.busy && !it.gamepadOpen,it.connection.scanning) }.distinctUntilChanged().collectLatest { (address,active,_) ->
                if(address!=null && active) loadDeviceWorks()
            }
        }
        viewModelScope.launch {
            state.map { Triple(it.selected, it.device?.address, it.foreground && !it.busy && !it.gamepadOpen) }.distinctUntilChanged().collectLatest { (work, address, active) ->
                mutable.update { it.copy(runtime=null, runtimeError=null) }
                if (work != null && address != null && active) {
                    val network = connections.network as? AndroidLan ?: return@collectLatest
                    val device = connections.state.value.device ?: return@collectLatest
                    while (true) {
                        try {
                            val status = network.client.runtime(device, work)
                            mutable.update { it.copy(runtime=status, runtimeError=null) }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { mutable.update { it.copy(runtime=null, runtimeError="无法确认运行状态，请检查连接与设备固件。") } }
                        delay(2500)
                    }
                }
            }
        }
        viewModelScope.launch { connections.state.collect { connection ->
            if(mutable.value.device?.address!=connection.device?.address) gamepad.disconnect("设备连接已变化，输入已释放")
            mutable.update { if(it.device?.address!=connection.device?.address) it.copy(connection=connection,deviceWorks=emptyList(),deviceWorksAddress=null,deviceWorksError=null) else it.copy(connection=connection) }
        } }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                store.importDebugBootstrap()
                val c = store.config()
                val works = store.readWorks()
                val address = store.address()
                mutable.update { it.copy(configured = c.apiKey.isNotBlank(), model = c.model,
                    baseUrl = c.baseUrl, debugProxyPort = c.debugProxyPort, works = works, address = address, libraryReady=true) }
            } catch (_: Exception) { mutable.update { it.copy(error = "本地数据或密钥读取失败；没有清空已有作品，请检查应用存储") } }
            try {
                val presets=AppCenter.presets { path -> application.assets.open(path).bufferedReader().use { it.readText() } }
                val local=store.readCenter()
                mutable.update { it.copy(centerApps=presets+local,centerReady=true) }
            } catch (_: Exception) { mutable.update { it.copy(error="应用中心读取失败，原文件已保留；请重新打开 App 或检查存储，未清空上传。") } }
        }
    }
    fun openGamepad() { if(!mutable.value.busy) mutable.update { it.copy(gamepadOpen=true) } }
    fun closeGamepad() { gamepad.disconnect();mutable.update { it.copy(gamepadOpen=false) } }
    fun connectGamepad() {
        val address=mutable.value.device?.address ?: return
        val network=connections.network as? AndroidLan ?: return
        if(mutable.value.gamepadOpen && mutable.value.foreground) network.gamepad(gamepad,address)
    }
    fun tab(index: Int) { mutable.value = mutable.value.copy(tab = index) }
    fun prompt(value: String) { mutable.value = mutable.value.copy(prompt = value) }
    fun address(value: String) { mutable.value = mutable.value.copy(address = value) }
    fun foreground(active: Boolean) {
        mutable.update { it.copy(foreground=active, runtime=if(active) it.runtime else null) }
        if(!active) gamepad.disconnect("App 已离开前台，输入已释放")
        connections.foreground(active)
        if (active) networkMonitor.start() else networkMonitor.stop()
    }
    fun scanDevices() { if (!uploading) connections.scan() }
    fun stopScan() = connections.stop()
    fun automaticScan(enabled: Boolean) {
        store.saveAutomaticScan(enabled)
        connections.automatic(enabled)
    }
    fun connectDevice(address: String) { if (!uploading) connections.connect(address) }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun newCreation() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(tab=0, selected=null, messages=emptyList(), prompt="", error=null)
    }
    fun open(work: Work) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(tab=0, selected=work, messages=listOf(Message("user",work.intent),Message("agent",work.summary)), error=null)
    }
    fun configure(base: String, model: String, key: String) = execute("保存手机 Agent 配置") {
        val previous = store.config()
        val next = AgentConfig(base.trim().trimEnd('/'), model.trim(), key.trim().ifBlank { previous.apiKey })
        require(next.baseUrl.startsWith("https://") && next.model.isNotBlank() && next.apiKey.isNotBlank()) { "请输入 HTTPS Base URL、模型和 Key" }
        withContext(Dispatchers.IO) { store.saveConfig(next) }
        mutable.value = mutable.value.copy(configured=true,agentChecked=false,baseUrl=next.baseUrl,model=next.model,debugProxyPort=next.debugProxyPort)
    }
    fun disableDebugProxy() = execute("切换为手机直接联网") {
        withContext(Dispatchers.IO) { store.saveConfig(store.config().copy(debugProxyPort=null)) }
        mutable.value = mutable.value.copy(debugProxyPort=null,agentChecked=false)
    }
    fun checkAgent() = execute("用最小请求检查模型连接") {
        mutable.value = mutable.value.copy(agentChecked=false)
        val config = withContext(Dispatchers.IO) { store.config() }
        agent.checkConnection(config)
        mutable.value = mutable.value.copy(agentChecked=true)
    }
    fun generate() {
        val initial = mutable.value
        if (initial.busy || !initial.libraryReady) return
        val intent = initial.prompt.trim()
        if (intent.isBlank()) return
        mutable.value = initial.copy(prompt="",tab=0,messages=initial.messages+Message("user",intent),error=null)
        execute("准备模型请求", onFailure={ mutable.value = mutable.value.copy(prompt=intent) }) {
            val config = withContext(Dispatchers.IO) { store.config() }
            val contract = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("agent-contract.txt").bufferedReader().use { it.readText() } }
            val generated = withContext(Dispatchers.IO) { agent.generate(config, contract, intent, initial.selected, ::stage) }
            val work = generated.copy(version = (mutable.value.works.filter { it.id == generated.id }.maxOfOrNull { it.version } ?: 0) + 1)
            val works = listOf(work) + mutable.value.works
            withContext(Dispatchers.IO) { store.saveWorks(works) }
            mutable.value = mutable.value.copy(works=works,selected=work,
                messages=mutable.value.messages+Message("agent",work.summary+"\n已通过本地语法检查并保存，尚未在硬件运行。"))
        }
    }
    fun appsGroup(group: Int) { mutable.update { it.copy(appsGroup=group,centerDetail=null) } }
    fun centerDetail(id: String?) { mutable.update { it.copy(centerDetail=id) } }
    fun centerUpload(open: Boolean) { mutable.update { it.copy(centerUploadOpen=open,error=null) } }
    fun clearCenterNotice() { mutable.update { it.copy(centerNotice=null) } }
    fun acquireCenter(app: CenterApp) = execute("添加应用到我的创作") {
        require(mutable.value.libraryReady && mutable.value.centerReady) { "本地数据尚未读取完成，未写入作品" }
        val existing=app.imported(mutable.value.works)
        if(existing!=null) {
            mutable.update { it.copy(appsGroup=0,centerDetail=null,centerNotice="已添加过此版本，可在我的创作中查看；未重复获取。") }
        } else {
            val work=withContext(Dispatchers.IO) { app.acquire() }
            val works=listOf(work)+mutable.value.works
            withContext(Dispatchers.IO) { store.saveWorks(works) }
            mutable.update { it.copy(works=works,centerNotice="已添加到我的创作；未部署或启动设备。") }
        }
    }
    fun publishCenter(work: Work, title:String, summary:String, category:String, consent:Boolean) = execute("保存到本机应用中心") {
        require(mutable.value.libraryReady && mutable.value.centerReady) { "本地数据尚未读取完成，未覆盖原文件" }
        require(mutable.value.works.any { it.packageId==work.packageId }) { "该作品版本已不在手机，请重新选择" }
        val origin=mutable.value.centerApps.find { it.id==work.centerOrigin }
        val artwork=origin?.artwork ?: if(work.title.contains("时钟")||work.title.contains("专注")) "quiettime" else "lightboard"
        val snapshot=withContext(Dispatchers.IO) {
            val saved=AppCenter.snapshot(work,title,summary,category,consent,artwork)
            if(origin!=null) saved.copy(controls=origin.controls) else saved
        }
        val existing=mutable.value.centerApps.find { it.id==snapshot.id }
        val apps=if(existing!=null) mutable.value.centerApps else mutable.value.centerApps+snapshot
        if(existing==null) withContext(Dispatchers.IO) { store.saveCenter(apps.filterNot { it.preset }) }
        mutable.update { it.copy(centerApps=apps,appsGroup=1,centerDetail=snapshot.id,centerUploadOpen=false,
            centerNotice=if(existing!=null) "这个版本已上传，未重复添加或覆盖。" else "已上传到本机应用中心，没有公开到网络。") }
    }
    fun removeCenter(app:CenterApp) = execute("移除本机上传") {
        require(mutable.value.centerReady && !app.preset) { "不能移除预置应用，或本地目录尚未读取完成" }
        val apps=mutable.value.centerApps.filterNot { it.id==app.id }
        withContext(Dispatchers.IO) { store.saveCenter(apps.filterNot { it.preset }) }
        mutable.update { it.copy(centerApps=apps,centerDetail=null,centerNotice="已移除本机上传；我的创作与设备文件不受影响。") }
    }
    fun refreshDeviceWorks() { if(!mutable.value.busy && !mutable.value.deviceWorksLoading) viewModelScope.launch { loadDeviceWorks() } }
    private suspend fun loadDeviceWorks() {
        val device=connections.state.value.device ?: return
        val network=connections.network as? AndroidLan ?: return
        val token=++deviceWorksEpoch
        mutable.update { it.copy(deviceWorksLoading=true,deviceWorksError=null) }
        try {
            val items=network.client.deviceWorks(device)
            if(token==deviceWorksEpoch && connections.network===network && connections.state.value.device?.address==device.address)
                mutable.update { it.copy(deviceWorks=items,deviceWorksAddress=device.address,deviceWorksError=null) }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            if(token==deviceWorksEpoch && connections.network===network && connections.state.value.device?.address==device.address)
                mutable.update { it.copy(deviceWorksError=(e.message ?: "设备作品同步失败，请重试").take(200)) }
        } finally { if(token==deviceWorksEpoch) mutable.update { it.copy(deviceWorksLoading=false) } }
    }
    fun deleteDeviceWork(work:DeviceWork,address:String) = execute("核对设备作品，准备删除") {
        val device=connections.state.value.device ?: error("请先连接原设备")
        val network=connections.network as? AndroidLan ?: error("局域网已断开")
        require(device.address==address && mutable.value.deviceWorksAddress==address && work.deletable) { "目标设备已变化或作品不可删除，请刷新" }
        require(!mutable.value.gamepadOpen) { "请先退出手柄再删除作品" }
        connections.stop(); uploading=true
        fun checkTarget() { check(connections.network===network && connections.state.value.device?.address==address && mutable.value.foreground) { "连接已变化，删除结果待同步；请重新连接原设备刷新" } }
        try {
            network.client.deleteDeviceWork(device,work,::stage,::checkTarget)
            val works=mutable.value.works.map { if(it.packageId==work.id && it.uploadedTo==address) it.copy(uploadedTo=null) else it }
            withContext(Dispatchers.IO) { store.saveWorks(works) }
            checkTarget()
            mutable.update { it.copy(works=works,selected=it.selected?.let { selected -> works.find { w -> w.packageId==selected.packageId } ?: selected },
                deviceWorks=it.deviceWorks.filterNot { w -> w.id==work.id },runtime=it.runtime?.takeUnless { r -> r.id==work.id && r.address==address },
                centerNotice="已从设备删除「${work.name}」；手机创作、源码和其他版本保留。") }
        } finally { uploading=false }
    }
    fun inspect() = connectDevice(mutable.value.address)
    fun openClaw() {
        val device = connections.state.value.device ?: return
        val network = connections.network as? AndroidLan ?: return
        mutable.value = mutable.value.copy(clawOpen = true)
        claw.connect(device.address, network.claw(device.address))
    }
    fun reconnectClaw() {
        if (connections.state.value.device?.address != claw.state.value.address) {
            claw.disconnect("原会话设备尚未连接。请返回设备页确认目标，不能将消息自动发往另一台设备。")
            return
        }
        openClaw()
    }
    fun closeClaw() { claw.disconnect(); mutable.value = mutable.value.copy(clawOpen = false) }
    fun upload(work: Work) = execute("检查设备与作品") {
        val device = connections.state.value.device ?: error("请先在设备页连接并核对地址")
        val network = connections.network as? AndroidLan ?: error("局域网已断开，请重新连接")
        connections.stop()
        uploading = true
        try {
        val result = network.client.launchWork(device, work, ::stage) { uploaded ->
            val works = mutable.value.works.map { if(it.id==work.id && it.version==work.version) uploaded else it }
            withContext(Dispatchers.IO) { store.saveWorks(works) }
            mutable.value = mutable.value.copy(works=works,selected=uploaded)
        }
        mutable.value = mutable.value.copy(runtime=result, messages=mutable.value.messages+Message("agent",
            if(result != null) "已经在 Mosaico 上启动。请在设备屏幕上体验，还想改点什么？" else "启动请求已提交，但暂未确认运行。请核对设备，不要重复发送；可刷新状态。"))
        } finally { uploading = false }
    }
    fun stopWork(work: Work) = execute("请求停止设备作品") {
        val device = connections.state.value.device ?: error("请先连接原设备")
        val network = connections.network as? AndroidLan ?: error("局域网已断开")
        require(work.uploadedTo == device.address) { "当前设备不是此版本的传输目标" }
        uploading = true
        try {
            network.client.command(device, work, "stop")
            val result = awaitWorkState({ network.client.runtime(device, work) }, "stopped")
            mutable.value = mutable.value.copy(runtime=result, messages=mutable.value.messages+Message("agent",
                if(result != null) "设备已停止这件作品。你可以继续修改，或再次运行。" else "停止请求已提交，设备尚未确认结束，请在设备上检查。"))
        } finally { uploading = false }
    }
    fun delete(work: Work) = execute("删除手机本地版本") {
        require(mutable.value.libraryReady) { "本地作品尚未读取完成" }
        val works = mutable.value.works.filterNot { it.id==work.id && it.version==work.version }
        withContext(Dispatchers.IO) { store.saveWorks(works) }
        mutable.value = mutable.value.copy(works=works,selected=mutable.value.selected?.takeUnless { it.id==work.id && it.version==work.version })
    }
    fun cancel() { task?.cancel() }
    override fun onCleared() { gamepad.disconnect(); claw.disconnect(); networkMonitor.stop(); connections.close(); super.onCleared() }
    private fun stage(value: String) { mutable.update { it.copy(stage=value) } }
    private fun execute(label: String, onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy=true,stage=label,error=null)
        task = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { onFailure(); mutable.value = mutable.value.copy(error="已停止等待。若请求已送达，设备可能已发布、启动或删除；运行状态与作品目录需重新查询，没有自动重试、停止或删除。"); throw e }
            catch (e: Exception) { onFailure(); mutable.value = mutable.value.copy(error=(e.message ?: "操作失败，请重试").take(350)) }
            finally { mutable.value = mutable.value.copy(busy=false,stage="") }
        }
    }
}
