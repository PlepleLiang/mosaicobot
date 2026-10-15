package dev.mosaicobot

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DeviceClient(client: OkHttpClient = networkClient()) {
    private val client = client.newBuilder().retryOnConnectionFailure(false).build()
    companion object {
        private val phonePackageId = Regex("mb_[a-f0-9]{16}_v[1-9][0-9]*_[a-f0-9]{8}")
        fun address(value: String): HttpUrl {
            val normalized = if (value.contains("://")) value.trim() else "http://" + value.trim()
            val url = normalized.toHttpUrl()
            val octets = url.host.split('.').mapNotNull { it.toIntOrNull() }
            val privateHost = octets.size == 4 && octets.all { it in 0..255 } &&
                (octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168) || octets[0] == 127)
            require(privateHost && url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null) {
                "请输入局域网 IPv4 地址，例如 http://192.168.1.80；不接受公网、用户信息或路径"
            }
            return url
        }
    }
    private suspend fun get(base: HttpUrl, path: String, maxBytes: Int = 512 * 1024): String = client.fetch(Request.Builder().url(base.resolve(path)!!).get().build(), maxBytes)
    suspend fun inspect(value: String): DeviceInfo {
        val base = address(value)
        val status = JSONObject(get(base, "/api/status", 16 * 1024))
        require(status.has("wifi_connected") && status.has("storage_base_path")) { "不是已识别的 Claw 状态接口" }
        status.getBoolean("wifi_connected")
        val root = status.getString("storage_base_path")
        require(root.startsWith('/') && !root.contains("..") && root.length <= 256 && root.none { it.isISOControl() }) { "设备未提供有效的数据目录" }
        val modules = JSONObject(get(base, "/api/lua-modules", 256 * 1024)).getJSONArray("items")
        val ids = (0 until modules.length()).map { modules.getJSONObject(it).getString("module_id") }.toSet()
        require(ids.containsAll(setOf("display", "delay", "system"))) { "设备缺少 Mosaico 作品所需的基础模块" }
        return DeviceInfo(base.toString().trimEnd('/'), root, ids)
    }
    private suspend fun checkWorkRequirements(device: DeviceInfo, work: Work) {
        val missing = work.requiredModules - device.modules
        require(missing.isEmpty()) { "设备缺少作品需要的 Lua 模块：" + missing.joinToString(" / ") + "；未部署或启动" }
        if (!work.usesNetwork) return
        val base = address(device.address)
        val groups = JSONObject(get(base, "/api/capabilities", 64 * 1024)).getJSONArray("items")
        require((0 until groups.length()).any { groups.getJSONObject(it).optString("group_id") == "cap_http_request" }) {
            "设备未提供 HTTP 请求能力，请更新固件；未部署或启动"
        }
        // Request only these non-secret fields, never the full config, model keys or Wi-Fi password.
        val config = JSONObject(get(base, "/api/config?fields=search_http_allowlist,enabled_cap_groups,enabled_lua_modules", 4096))
        val capGroups = config.getString("enabled_cap_groups")
        val enabledModules = config.getString("enabled_lua_modules")
        require(capGroups.isBlank() || "cap_http_request" in capGroups.split(',').map { it.trim() }) {
            "设备 HTTP 能力未启用，请在 Claw 设置中启用 cap_http_request；未部署或启动"
        }
        require(enabledModules.isBlank() || enabledModules.split(',').map { it.trim() }.containsAll(work.requiredModules)) {
            "设备未启用作品需要的 Lua 模块，请在 Claw 设置中检查；未部署或启动"
        }
        val blocked = work.networkHosts.filterNot { LuaNetworkPolicy.hostAllowed(it, config.getString("search_http_allowlist")) }
        require(blocked.isEmpty()) {
            "设备 HTTP 白名单未允许：" + blocked.joinToString() + "；请在 Claw 网络请求设置中按需授权这些域名后重试，未部署或启动"
        }
    }
    private suspend fun post(base: HttpUrl, path: String, json: JSONObject) {
        val result = JSONObject(client.fetch(Request.Builder().url(base.resolve(path)!!)
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()))
        require(result.optBoolean("ok")) { "设备没有确认写入成功" }
    }
    private suspend fun upload(base: HttpUrl, path: String, content: String) {
        val url = base.resolve("/api/files/upload")!!.newBuilder().addQueryParameter("path", path).build()
        val result = JSONObject(client.fetch(Request.Builder().url(url)
            .post(content.toRequestBody("application/octet-stream".toMediaType())).build()))
        require(result.optBoolean("ok")) { "设备没有确认文件上传" }
        val readback = get(base, "/files" + path)
        require(Work.digest(readback) == Work.digest(content)) { "设备读回摘要不一致，不发布应用清单" }
    }
    suspend fun uploadWork(verified: DeviceInfo, work: Work, stage: (String) -> Unit) {
        ArtifactValidator.validateLua(work.lua)
        require(phonePackageId.matches(work.packageId)) { "非法应用标识" }
        val fresh = inspect(verified.address)
        require(fresh.storageRoot == verified.storageRoot) { "设备数据目录发生变化，请重新确认设备" }
        checkWorkRequirements(fresh, work)
        val base = address(verified.address)
        // Check all existing DATA app IDs before creating a unique directory. Never touch SYSTEM.
        val rootListing = JSONObject(get(base, "/api/files?path=/")).getJSONArray("entries")
        val hasApps = (0 until rootListing.length()).any { rootListing.getJSONObject(it).optString("name") == "apps" }
        if (hasApps) {
            val entries = JSONObject(get(base, "/api/files?path=/apps")).getJSONArray("entries")
            if ((0 until entries.length()).any { entries.getJSONObject(it).optString("name") == work.packageId }) {
                stage("发现设备已有此版本，核对文件后复用，不重复上传")
                verifyWork(fresh, work)
                return
            }
        }
        val dir = "/apps/" + work.packageId
        stage("创建独立作品目录，不修改系统应用")
        post(base, "/api/files/mkdir", JSONObject().put("path", dir + "/scripts").put("recursive", true))
        stage("传输 Lua 并读回校验 SHA-256")
        upload(base, dir + "/scripts/main.lua", work.lua)
        stage("最后写入 launcher.json 并读回校验")
        upload(base, dir + "/launcher.json", work.manifest())
        // Publication and execution are separate explicit commands, never an upload side effect.
    }
    suspend fun verifyWork(device: DeviceInfo, work: Work) {
        if (work.usesNetwork) {
            ArtifactValidator.validateLua(work.lua)
            val fresh = inspect(device.address)
            require(fresh.storageRoot == device.storageRoot) { "设备数据目录发生变化，请重新确认设备" }
            checkWorkRequirements(fresh, work)
        }
        val base = address(device.address)
        val dir = "/apps/" + work.packageId
        require(Work.digest(get(base, "/files" + dir + "/scripts/main.lua")) == work.sha256 &&
            Work.digest(get(base, "/files" + dir + "/launcher.json")) == Work.digest(work.manifest())) {
            "设备作品与手机版本不一致，未启动也未覆盖，请核对文件"
        }
    }
    internal suspend fun deviceWorks(device: DeviceInfo): List<DeviceWork> = try {
        DeviceWork.parse(get(address(device.address), "/api/works", 128 * 1024))
    } catch (e: HttpStatusException) {
        if(e.statusCode !in setOf(400,404,405)) throw e
        throw IllegalStateException("设备 Works 列表接口不兼容，请更新固件；不使用通用文件删除")
    }
    internal suspend fun hasDeviceWorkDirectory(device: DeviceInfo, id: String): Boolean {
        require(phonePackageId.matches(id))
        val base=address(device.address)
        val root=JSONObject(get(base,"/api/files?path=/")).getJSONArray("entries")
        if((0 until root.length()).none { root.getJSONObject(it).getString("name")=="apps" }) return false
        val entries=JSONObject(get(base,"/api/files?path=/apps")).getJSONArray("entries")
        return (0 until entries.length()).any { entries.getJSONObject(it).getString("name")==id }
    }
    suspend fun runtime(device: DeviceInfo, work: Work): WorkRuntime = runtimeById(device, work.packageId)
    internal suspend fun runtimeById(device: DeviceInfo, id: String): WorkRuntime {
        require(phonePackageId.matches(id)) { "非法手机作品标识" }
        return WorkRuntime.parse(device.address, id, get(address(device.address), "/api/works?id=" + id, 4096))
    }
    // Read the actual DATA directory, including apps no longer listed on this phone.
    // Never target firmware/native works; no unaudited IDs from remote data enter commands.
    internal suspend fun activePhoneWorks(device: DeviceInfo, exceptId: String): List<WorkRuntime> {
        val entries = JSONObject(get(address(device.address), "/api/files?path=/apps")).getJSONArray("entries")
        val ids = (0 until entries.length()).map { entries.getJSONObject(it).optString("name") }
            .filter { phonePackageId.matches(it) && it != exceptId }.distinct()
        require(ids.size <= 128) { "设备作品过多，请在设备上停止当前作品后重试" }
        return ids.map { runtimeById(device, it) }.filter { it.active }
    }
    suspend fun command(device: DeviceInfo, work: Work, action: String): WorkRuntime = commandById(device, work.packageId, action)
    internal suspend fun commandById(device: DeviceInfo, id: String, action: String): WorkRuntime {
        require(validPhoneWorkId(id) && action in setOf("publish", "start", "stop", "delete"))
        val body = JSONObject().put("id", id).put("action", action)
        val json = try {
            client.fetch(Request.Builder().url(address(device.address).resolve("/api/works")!!)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build(), 4096)
        } catch (e: HttpStatusException) {
            if (e.statusCode != 409) throw e
            throw IllegalStateException(when (action) {
                "start" -> "设备暂不能启动作品（409）：可能仍有其他作品运行或正在停止。请在 Mosaico Works 中确认当前任务后重试；未重复部署。"
                "publish" -> "设备暂不能发布作品（409），请检查设备 Works 注册状态；已传输文件保留，未覆盖或重复上传。"
                "delete" -> "设备暂不能删除此版本（409）：可能仍在运行或清理，或为只读作品。文件删除未确认，请刷新后重试。"
                else -> "设备暂不能停止该作品（409），未继续启动其他作品；请在 Mosaico 上检查当前任务。"
            })
        }
        return WorkRuntime.parse(device.address, id, json)
    }
}
