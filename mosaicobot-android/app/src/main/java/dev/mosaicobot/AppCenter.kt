package dev.mosaicobot

import org.json.JSONArray
import org.json.JSONObject

/** Immutable local snapshot, without a Work's intent, device address or agent config. */
data class CenterApp(
    val id: String, val title: String, val summary: String, val category: String,
    val lua: String, val version: Int = 1, val artwork: String = "lightboard",
    val controls: String = "请先查看源码和作者说明，再确认在设备上运行。",
    val sourcePackageId: String? = null,
) {
    val preset: Boolean get() = sourcePackageId == null
    val usesNetwork: Boolean get() = "capability" in ArtifactValidator.requiredModules(lua)
    fun json(): JSONObject = JSONObject().put("id",id).put("title",title).put("summary",summary)
        .put("category",category).put("lua",lua).put("version",version).put("artwork",artwork)
        .put("controls",controls).put("sourcePackageId",sourcePackageId ?: JSONObject.NULL)
    fun acquire(): Work {
        ArtifactValidator.validateLua(lua)
        return Work(title=title,summary=summary,lua=lua,version=version,
            intent="基于应用中心作品「$title」继续创作。$summary 操作方式：$controls",centerOrigin=id)
    }
    fun imported(works: List<Work>): Work? = works.find { it.centerOrigin==id }
}

object AppCenter {
    val categories = listOf("游戏", "工具", "趣玩")
    val artworks = setOf("tank", "flight", "racer", "pixelpet", "lightboard", "quiettime")
    fun snapshot(work: Work, title: String, summary: String, category: String, consent: Boolean, artwork: String = "lightboard"): CenterApp {
        require(consent) { "请确认有权分享且作品不包含密钥或其他私密信息" }
        val name=title.trim(); val description=summary.trim()
        require(name.isNotBlank() && name.length<=24 && name.toByteArray(Charsets.UTF_8).size<=48) { "名称最多 24 字符 / 48 字节（约 16 个汉字）" }
        require(description.isNotBlank() && description.length<=80) { "请填写 1–80 字符的作品介绍" }
        require(category in categories) { "请选择游戏、工具或趣玩分类" }
        require(work.version>0) { "作品版本无效" }
        ArtifactValidator.validateLua(work.lua)
        return CenterApp(id="local_"+Work.digest(work.packageId).take(32),title=name,summary=description,
            category=category,lua=work.lua,version=work.version,artwork=artwork.takeIf { it in artworks } ?: "lightboard",sourcePackageId=work.packageId)
    }
    fun encode(apps: List<CenterApp>): String = JSONArray().apply { apps.forEach { put(it.json()) } }.toString()
    fun decode(text: String): List<CenterApp> {
        val array=JSONArray(text)
        val apps=(0 until array.length()).map { i ->
            val j=array.getJSONObject(i)
            val app=CenterApp(j.getString("id"),j.getString("title"),j.getString("summary"),j.getString("category"),
                j.getString("lua"),j.getInt("version"),j.getString("artwork"),j.getString("controls"),j.getString("sourcePackageId"))
            require(app.id=="local_"+Work.digest(app.sourcePackageId!!).take(32) && app.version>0 && app.category in categories && app.artwork in artworks) { "本机应用中心记录损坏，未覆盖原文件" }
            require(app.title.isNotBlank() && app.title.length<=24 && app.title.toByteArray(Charsets.UTF_8).size<=48 && app.summary.length in 1..80) { "本机应用中心内容无效，未覆盖原文件" }
            ArtifactValidator.validateLua(app.lua)
            app
        }
        require(apps.map { it.id }.distinct().size==apps.size) { "本机应用中心存在重复记录，未覆盖原文件" }
        return apps
    }
    fun presets(read: (String)->String): List<CenterApp> {
        val common=read("app-center/common.lua")
        fun app(key:String,title:String,summary:String,category:String,controls:String): CenterApp {
            val game=key in setOf("tank","flight","racer")
            val lua=(if(game) read("app-center/game-common.lua") else common)+"\n"+read("app-center/$key.lua")
            ArtifactValidator.validateLua(lua)
            val version=if(game) 2 else 1
            val padControls=if(game) "手柄操作：A 开始 / 重试；Start 暂停 / 继续；方向键或左摇杆移动，坦克 A / R1 开火。同一可信局域网下进入手柄即可自动连接，无需设备确认；断线暂停，可用 Start 或触屏 PLAY 恢复。" else ""
            return CenterApp("seed_"+key+"_v"+version,title,summary,category,lua,version=version,artwork=key,controls=padControls+controls)
        }
        return listOf(
            app("tank","坦克大战","守住基地，击破砖墙，迎接一波波敌方坦克。","游戏","底部方向键移动并转向，按住 FIRE 开火。摧毁敌人进入下一波，基地或自身被击中则结束。顶部暂停、退出；结束后可重开。"),
            app("flight","飞机大战","指尖领航，自动射击，穿越星际弹幕。","游戏","拖动战机闪避敌机和子弹，自动发射。消灭敌机计分，三次受击后结束。顶部暂停、退出，结束后重开。"),
            app("racer","午夜赛车","三条车道，在不断加速的夜色中挑战距离。","游戏","点击屏幕左右两侧切换车道，躲开前方车辆。距离越远速度越快；顶部暂停、退出，碰撞后重开。不需要 IMU。"),
            app("pixelpet","口袋像素宠","在桌角养一只会回应触摸的像素伙伴。","趣玩","触摸宠物互动，FEED 喂食、REST 休息，RESET 重置。状态只保留在本次运行中；顶部暂停、退出。"),
            app("lightboard","光点画板","点亮 16 × 16 的小世界，画一张自己的表情。","趣玩","点击或滑动画画，底部选择四色画笔、ERASE 擦除。CLEAR 后再次点击 YES 确认清空。退出不保存画布。顶部暂停、退出。"),
            app("quiettime","一刻专注","15 / 25 / 45 分钟，把一段安静的时间放在眼前。","工具","选择时长后 START，顶部 PAUSE 暂停或继续。RESET 重置当前时长，到点仅显示屏幕提醒，不发声音；EXIT 退出。"),
        )
    }
}
