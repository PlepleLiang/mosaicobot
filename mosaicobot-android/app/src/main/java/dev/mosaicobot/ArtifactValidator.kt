package dev.mosaicobot

import org.luaj.vm2.compiler.LuaC
import java.io.ByteArrayInputStream

object ArtifactValidator {
    private val moduleImport = Regex("""require\s*\(\s*["']([a-z_]+)["']\s*\)""")
    fun requiredModules(lua: String): Set<String> = moduleImport.findAll(lua).map { it.groupValues[1] }.toSet()
    fun parse(output: String, intent: String, previous: Work? = null): Work {
        val j = ArtifactEnvelope.decode(output)
        if (j.has("refusal")) throw UnsupportedOperationException(j.optString("refusal").take(300))
        val title = j.getString("title").trim()
        val lua = j.getString("lua")
        require(title.isNotBlank() && title.toByteArray().size <= 48) { "应用名称为空或过长" }
        validateLua(lua)
        return Work(id = previous?.id ?: Work(title="",summary="",lua="",intent="").id,
            version = (previous?.version ?: 0) + 1, title = title,
            summary = j.getString("summary").take(1000), lua = lua, intent = intent)
    }
    fun validateLua(lua: String) {
        require(lua.toByteArray().size in 40..65536) { "Lua 源码大小须为 40–65536 字节" }
        // Compile for syntax only. Never execute a model response on the phone.
        try { LuaC.instance.compile(ByteArrayInputStream(lua.toByteArray()), "mosaicobot") }
        catch (_: Exception) { throw IllegalArgumentException("Lua 语法检查失败；只接受 Lua 5.2/5.4 公共语法子集") }
        // Conservative lint, NOT a security sandbox. Device-side permission enforcement is still required.
        val forbidden = Regex("""\b(io|os|debug|package|load|loadfile|loadstring|dofile|_G|_ENV|rawget|rawset|setmetatable|getmetatable|collectgarbage)\b""")
        require(!forbidden.containsMatchIn(lua)) { "源码包含不允许的动态执行、全局访问或文件操作" }
        val imports = requiredModules(lua)
        require(imports.containsAll(setOf("display", "delay", "system")) && imports.all { it in setOf("display", "delay", "system", "json", "capability", "gamepad") }) { "必须包含 display、delay、system；仅额外支持 json、gamepad 和受限 HTTPS capability 调用" }
        if ("capability" in imports) LuaNetworkPolicy.validate(lua)
        val withoutImports = lua.replace(moduleImport, "")
        require(!Regex("""\brequire\b""").containsMatchIn(withoutImports)) { "不允许动态模块导入" }
        require(Regex("""\.millis\s*\(""").containsMatchIn(lua) && lua.contains("delay_ms")) { "应用必须使用时钟并主动让出执行时间" }
        require(lua.contains(":present(") && lua.contains(":close(") && lua.contains(":begin(")) { "缺少完整画面提交或资源释放" }
    }
}
