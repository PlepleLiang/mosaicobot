package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test

class LuaNetworkPolicyTest {
    companion object {
        val lua = """
            local capability = require("capability")
            local json = require("json")
            local url = "https://api.open-meteo.com/v1/forecast?latitude=39.90&longitude=116.40&current=temperature_2m,weather_code"
            local ok, out, err = capability.call("http_request", {url=url, method="GET", timeout_ms=8000, max_body_bytes=16384}, {max_output_bytes=17408})
            local status, body
            if ok and type(out) == "string" then status, body = out:match("^HTTP (%d+)\n(.*)$") end
            local parsed, data = false, nil
            if status and tonumber(status) >= 200 and tonumber(status) < 300 then parsed, data = pcall(json.decode, body) end
        """.trimIndent() + "\n" + ArtifactTest.lua.replace("local start = system.millis()",
            "local start = system.millis()\nlocal epoch = system.time()\nlocal clock = system.date(\"%H:%M:%S\")")
        fun work() = ArtifactValidator.parse(ArtifactTest.response(lua), "生成天气时钟")
    }
    @Test fun realWeatherAndClockPassSyntaxAndDeclareCapabilities() {
        val work = work()
        assertTrue(work.usesNetwork)
        assertEquals(setOf("display", "delay", "system", "json", "capability"), work.requiredModules)
        assertEquals(setOf("api.open-meteo.com"), work.networkHosts)
        assertTrue(work.capabilityLabel.contains("联网"))
        assertEquals(work.networkHosts, Work.fromJson(work.json()).networkHosts)
    }
    @Test fun existingScreenAppsKeepTheirPermissionsAndManifest() {
        val work = ArtifactTest.work()
        assertFalse(work.usesNetwork)
        assertTrue(work.networkHosts.isEmpty())
        assertEquals("屏幕 + 触摸", work.capabilityLabel)
        assertFalse(work.manifest().contains("permission")) // firmware has no per-app permission field
    }
    @Test fun jsonWithoutNetworkingIsAllowed() {
        val work = ArtifactValidator.parse(ArtifactTest.response("local json = require('json')\n" + ArtifactTest.lua), "解析数据")
        assertFalse(work.usesNetwork)
        assertTrue("json" in work.requiredModules)
    }
    @Test fun unrelatedClawCapabilitiesAreRejected() {
        listOf("system_restart", "lua_execute", "file_write", "qq_send_message").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua.replace("http_request", name)) }
        }
    }
    @Test fun aliasesAndDynamicCapabilityNamesAreRejected() {
        listOf(lua.replace("capability.call(\"http_request\"", "capability.call(name"),
            lua.replace("capability.call", "capability['call']"),
            lua + "\nlocal invoke = capability.call",
            lua.replace("local capability =", "local cap =").replace("capability.call", "cap.call")).forEach {
            assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(it) }
        }
    }
    @Test fun mutatingHttpOrFileDownloadPayloadsAreRejected() {
        listOf(lua.replace("method=\"GET\"", "method=\"POST\""),
            lua.replace("method=\"GET\"", "method=verb"),
            lua.replace("max_body_bytes=16384}", "max_body_bytes=16384, save_path=\"/nand/x\"}"),
            lua.replace("max_body_bytes=16384}", "max_body_bytes=16384, headers={Authorization=\"secret\"}}"),
            lua.replace("timeout_ms=8000", "timeout_ms=120000")).forEach {
            assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(it) }
        }
    }
    @Test fun plainHttpAndUndeclaredHostsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua.replace("https://", "http://")) }
        assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua.replace("https://", "")) }
    }
    @Test fun exactAndSubdomainAllowlistRulesMatchFirmware() {
        assertTrue(LuaNetworkPolicy.hostAllowed("api.open-meteo.com", "other.com, API.OPEN-METEO.COM "))
        assertTrue(LuaNetworkPolicy.hostAllowed("api.open-meteo.com", "*.open-meteo.com"))
        assertTrue(LuaNetworkPolicy.hostAllowed("open-meteo.com", ".open-meteo.com"))
        assertTrue(LuaNetworkPolicy.hostAllowed("api.open-meteo.com", "*"))
        assertFalse(LuaNetworkPolicy.hostAllowed("api.open-meteo.com", ""))
        assertFalse(LuaNetworkPolicy.hostAllowed("api.open-meteo.com", "open-meteo.com"))
        assertFalse(LuaNetworkPolicy.hostAllowed("open-meteo.com.evil.test", "*.open-meteo.com"))
        assertFalse(LuaNetworkPolicy.hostAllowed("fakeopen-meteo.com", ".open-meteo.com"))
    }
}
