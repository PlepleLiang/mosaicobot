package dev.mosaicobot

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class WorkRuntime(val address: String, val id: String, val status: String, val checkedAt: Long = System.currentTimeMillis()) {
    val active: Boolean get() = status in setOf("queued", "running", "stopping")
    val label: String get() = when(status) {
        "running" -> "已在 Mosaico 上运行"
        "queued" -> "设备正在启动"
        "stopping" -> "设备正在停止"
        "stopped" -> "已停止 · 可以再次运行"
        "failed" -> "设备运行失败"
        "not_found" -> "尚未发布到 Works"
        else -> "运行状态待确认"
    }
    companion object {
        fun parse(address: String, id: String, json: String): WorkRuntime {
            val root = JSONObject(json)
            require(root.optBoolean("ok") && root.optInt("protocol") == 1 && root.optString("id") == id) { "设备运行接口不兼容或未确认操作" }
            val status = root.getString("state")
            require(status in setOf("not_found", "stopped", "queued", "running", "stopping", "failed")) { "未知设备运行状态" }
            return WorkRuntime(address, id, status)
        }
    }
}

internal suspend fun awaitWorkState(read: suspend () -> WorkRuntime, expected: String): WorkRuntime? = withTimeoutOrNull(20_000) {
    var result = read()
    while(result.status != expected) {
        check(result.status != "failed" && !(expected == "running" && result.status == "stopped")) { "作品已退出或启动失败，请检查设备和 Lua 源码" }
        delay(700)
        result = read()
    }
    result
}
