package dev.mosaicobot

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class AgentConfig(val baseUrl: String = "https://models-proxy.stepfun-inc.com/v1", val model: String = "step-5-preview:stepfun", val apiKey: String = "", val debugProxyPort: Int? = null)
data class Message(val role: String, val text: String)
data class Work(
    val id: String = UUID.randomUUID().toString().replace("-", "").take(16),
    val version: Int = 1,
    val title: String,
    val summary: String,
    val lua: String,
    val intent: String,
    val createdAt: Long = System.currentTimeMillis(),
    val uploadedTo: String? = null,
    val centerOrigin: String? = null,
) {
    val requiredModules: Set<String> get() = ArtifactValidator.requiredModules(lua)
    val usesNetwork: Boolean get() = "capability" in requiredModules
    val networkHosts: Set<String> get() = if (usesNetwork) LuaNetworkPolicy.hosts(lua) else emptySet()
    val capabilityLabel: String get() = "屏幕 + 触摸" + (if ("gamepad" in requiredModules) " + 手柄" else "") + (if (usesNetwork) " + 联网" else "")
    val sha256: String get() = digest(lua)
    // Unique immutable package per version: never shadow firmware or overwrite a running app.
    val packageId: String get() = "mb_" + id + "_v" + version + "_" + sha256.take(8)
    fun manifest(): String = JSONObject().put("schema_version", 1).put("id", packageId)
        .put("display_name", title.take(36) + " v" + version).put("entry", "scripts/main.lua")
        .put("args", JSONObject()).put("order", 100).put("visible", true).toString(2)
    fun json(): JSONObject = JSONObject().put("id", id).put("version", version).put("title", title)
        .put("summary", summary).put("lua", lua).put("intent", intent).put("createdAt", createdAt)
        .put("uploadedTo", uploadedTo ?: JSONObject.NULL).put("centerOrigin", centerOrigin ?: JSONObject.NULL)
    companion object {
        fun fromJson(j: JSONObject) = Work(j.getString("id"), j.getInt("version"), j.getString("title"),
            j.getString("summary"), j.getString("lua"), j.getString("intent"), j.getLong("createdAt"),
            if (j.isNull("uploadedTo")) null else j.getString("uploadedTo"),
            if (j.isNull("centerOrigin")) null else j.getString("centerOrigin"))
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
data class DeviceInfo(val address: String, val storageRoot: String, val modules: Set<String>, val checkedAt: Long = System.currentTimeMillis())
data class BuiltinApp(val name: String, val label: String, val origin: String)
val builtinApps = listOf(
    BuiltinApp("Camera", "相机", "Claw 主屏"), BuiltinApp("CLAW", "设备 Agent", "Claw 主屏"),
    BuiltinApp("Works", "设备作品启动器", "Claw 主屏"), BuiltinApp("Settings", "系统设置", "Claw 主屏"),
    BuiltinApp("IMU", "姿态感知", "Claw 主屏"), BuiltinApp("Album", "系统相册", "Claw 主屏"),
    BuiltinApp("Music", "音乐播放器", "Claw 主屏"), BuiltinApp("Bricks", "打砖块", "Claw 主屏"),
    BuiltinApp("Weather", "天气", "Claw 主屏"), BuiltinApp("Interaction", "模块交互", "Claw 主屏"),
    BuiltinApp("Partner", "设备伙伴", "Claw 主屏"), BuiltinApp("Dino", "预装跑酷", "Works 预装"),
    BuiltinApp("Flappy Bird", "预装飞行游戏", "Works 预装"), BuiltinApp("Album", "Works 相册", "Works 预装"),
)
