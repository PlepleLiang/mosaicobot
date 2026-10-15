package dev.mosaicobot

import org.json.JSONObject

data class DeviceWork(val id:String,val name:String,val builtin:Boolean,val status:String) {
    val deletable:Boolean get() = !builtin && validPhoneWorkId(id)
    val active:Boolean get() = status in setOf("queued","running","stopping")
    val version:String? get() = Regex("_v([1-9][0-9]*)_").find(id)?.groupValues?.get(1)
    val label:String get() = when(status) {
        "running" -> "正在运行"
        "queued" -> "正在启动"
        "stopping" -> "正在停止"
        "failed" -> "运行失败"
        "not_found" -> "残留文件 · 未注册"
        else -> "已安装"
    }
    companion object {
        fun parse(json:String):List<DeviceWork> {
            val root=JSONObject(json)
            require(root.optBoolean("ok") && root.optInt("protocol")==1) { "设备作品管理接口不兼容，请更新固件" }
            val items=root.getJSONArray("items")
            require(items.length()<=1024) { "设备作品目录过大，未读取完整列表" }
            val result=(0 until items.length()).map { i ->
                val item=items.getJSONObject(i)
                val id=item.getString("id"); val name=item.getString("display_name"); val status=item.getString("state")
                require(id.length in 1..63 && id.all { it.isLetterOrDigit() && it.code<128 || it in "_-" } && name.length<=256 && name.none { it.isISOControl() }) { "设备作品目录包含无效记录" }
                require(status in setOf("stopped","queued","running","stopping","failed","not_found")) { "设备作品状态未知" }
                DeviceWork(id,name.ifBlank { id },item.getBoolean("builtin"),status)
            }
            require(result.map { it.id }.distinct().size==result.size) { "设备作品目录包含重复标识" }
            return result
        }
    }
}
internal fun validPhoneWorkId(id:String):Boolean = id.length<=63 && Regex("mb_[a-f0-9]{16}_v[1-9][0-9]*_[a-f0-9]{8}").matches(id)

internal suspend fun DeviceClient.deleteDeviceWork(device:DeviceInfo,work:DeviceWork,stage:(String)->Unit,checkTarget:()->Unit = {}) {
    require(work.deletable) { "预装与系统作品不可删除" }
    checkTarget()
    val current=deviceWorks(device).find { it.id==work.id }
    require(current==null || current.deletable) { "设备确认此作品不可删除" }
    if(current?.active==true) {
        stage("停止此版本，等待屏幕与任务清理")
        checkTarget()
        if(current.status!="stopping") commandById(device,work.id,"stop")
        check(awaitWorkIdle { checkTarget();runtimeById(device,work.id) }) { "作品尚未停止，未删除文件；请稍后重试" }
    }
    stage("从设备删除此版本的文件")
    checkTarget()
    require(commandById(device,work.id,"delete").status=="not_found") { "设备尚未确认删除，列表需重新同步" }
    checkTarget()
    require(deviceWorks(device).none { it.id==work.id } && !hasDeviceWorkDirectory(device,work.id)) { "设备仍有此版本文件，未确认删除成功；请刷新后重试" }
    checkTarget()
}
