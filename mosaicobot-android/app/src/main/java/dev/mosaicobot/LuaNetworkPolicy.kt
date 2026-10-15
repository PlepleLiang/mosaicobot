package dev.mosaicobot

/** Conservative source lint for generated apps, NOT a Lua sandbox or a runtime permission boundary. */
object LuaNetworkPolicy {
    // Constrain normal generated calls to the documented read-only shape; this is not
    // runtime enforcement. Queries can be built in a local URL variable.
    private val binding = Regex("""\blocal\s+capability\s*=\s*require\s*\(\s*["']capability["']\s*\)""")
    private val request = Regex("""\bcapability\s*\.\s*call\s*\(\s*["']http_request["']\s*,\s*\{\s*url\s*=\s*(?:[A-Za-z_]\w*|"[^"\r\n]*"|'[^'\r\n]*')\s*,\s*method\s*=\s*["']GET["']\s*,\s*timeout_ms\s*=\s*8000\s*,\s*max_body_bytes\s*=\s*16384\s*\}\s*,\s*\{\s*max_output_bytes\s*=\s*17408\s*\}\s*\)""")
    private val httpsHost = Regex("""https://([A-Za-z0-9.-]+)(?=[/?"'])""")

    fun hosts(lua: String): Set<String> = httpsHost.findAll(lua).map { it.groupValues[1].lowercase() }.toSet()

    fun validate(lua: String) {
        require(binding.findAll(lua).count() == 1 && request.containsMatchIn(lua)) {
            "联网须使用 local capability = require(\"capability\") 和约定的 HTTPS GET 调用格式"
        }
        val remaining = lua.replace(binding, "").replace(request, "")
        require(!Regex("""\bcapability\b""").containsMatchIn(remaining)) {
            "仅允许约定格式的 capability.call(\"http_request\",...)；禁止其他能力、别名或动态调用"
        }
        require(!Regex("http://", RegexOption.IGNORE_CASE).containsMatchIn(lua) && hosts(lua).isNotEmpty()) {
            "联网应用须使用源码中明确声明域名的 HTTPS 地址，不允许明文 HTTP"
        }
    }

    // Mirror Claw's existing CSV allowlist semantics without changing its configuration.
    fun hostAllowed(host: String, allowlist: String): Boolean = allowlist.split(',').any { entry ->
        val token = entry.trim().lowercase()
        when {
            token == "*" -> true
            token.startsWith("*.") || token.startsWith(".") -> {
                val domain = token.removePrefix("*").removePrefix(".")
                domain.isNotEmpty() && (host.lowercase() == domain || host.lowercase().endsWith(".$domain"))
            }
            else -> token.isNotEmpty() && host.equals(token, ignoreCase = true)
        }
    }
}
