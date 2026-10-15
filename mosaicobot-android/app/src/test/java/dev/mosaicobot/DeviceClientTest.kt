package dev.mosaicobot

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DeviceClientTest {
    private val status = """{"wifi_connected":true,"storage_base_path":"/nand"}"""
    private val modules = """{"items":[{"module_id":"display"},{"module_id":"delay"},{"module_id":"system"}]}"""
    private fun MockWebServer.body(value:String) { enqueue(MockResponse().setBody(value)) }
    @Test fun onlyExplicitLanAddressesAccepted() {
        listOf("192.168.1.30", "http://10.2.3.4:8080", "http://172.16.4.2", "http://127.0.0.1:5432").forEach { DeviceClient.address(it) }
        listOf("https://example.com", "http://8.8.8.8", "http://172.32.1.1", "http://192.168.1.2/path", "http://user:pass@192.168.1.2", "http://192.168.1.2?q=x").forEach {
            assertThrows(IllegalArgumentException::class.java) { DeviceClient.address(it) }
        }
    }
    @Test fun capabilityInspectionHasNoCredentialHeader() = runBlocking {
        MockWebServer().use { server ->
            server.body(status);server.body(modules)
            val info=DeviceClient().inspect(server.url("/").toString())
            assertEquals("/nand",info.storageRoot);assertTrue(info.modules.contains("display"))
            repeat(2) { assertNull(server.takeRequest().getHeader("Authorization")) }
        }
    }
    @Test fun unrelatedHttpHostIsRejectedBeforeModules() = runBlocking {
        MockWebServer().use { server ->
            server.body("{\"ok\":true}")
            assertTrue(runCatching { DeviceClient().inspect(server.url("/").toString()) }.isFailure)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun recognizableStatusWithoutWorksModulesIsNotAutoConnectable() = runBlocking {
        MockWebServer().use { server ->
            server.body(status);server.body("{\"items\":[{\"module_id\":\"system\"}]}")
            assertTrue(runCatching { DeviceClient().inspect(server.url("/").toString()) }.isFailure)
            assertEquals(2,server.requestCount)
            repeat(2) { assertEquals("GET",server.takeRequest().method) }
        }
    }
    @Test fun writesSourceBeforeManifestAndVerifiesBoth() = runBlocking {
        MockWebServer().use { server ->
            val work=ArtifactTest.work()
            server.body(status);server.body(modules);server.body("""{"entries":[]}""")
            server.body("""{"ok":true}""");server.body("""{"ok":true}""");server.body(work.lua)
            server.body("""{"ok":true}""");server.body(work.manifest())
            DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{})
            val requests=(0 until 8).map { server.takeRequest() }
            assertEquals("GET",requests[0].method)
            assertEquals("/api/files/mkdir",requests[3].path)
            assertTrue(requests[4].requestUrl!!.queryParameter("path")!!.endsWith("scripts/main.lua"))
            assertTrue(requests[5].path!!.contains("/files/apps/mb_"))
            assertTrue(requests[6].requestUrl!!.queryParameter("path")!!.endsWith("launcher.json"))
            assertTrue(requests.all { it.getHeader("Authorization")==null })
            assertTrue(requests.none { it.path!!.contains("system") || it.path!!.contains("start") })
        }
    }
    @Test fun corruptedReadbackDoesNotPublishManifest() = runBlocking {
        MockWebServer().use { server ->
            server.body(status);server.body(modules);server.body("""{"entries":[]}""")
            server.body("""{"ok":true}""");server.body("""{"ok":true}""");server.body("corrupt")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),ArtifactTest.work(),{}) }
            assertTrue(result.isFailure);assertEquals(6,server.requestCount)
        }
    }
    @Test fun existingPackageIsNeverOverwritten() = runBlocking {
        MockWebServer().use { server ->
            val work=ArtifactTest.work()
            server.body(status);server.body(modules);server.body("""{"entries":[{"name":"apps","is_dir":true}]}""")
            server.body("{\"entries\":[{\"name\":\""+work.packageId+"\"}]}")
            server.body("different source")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{}) }
            assertTrue(result.isFailure);assertEquals(5,server.requestCount)
            repeat(5) { assertEquals("GET",server.takeRequest().method) }
        }
    }
    @Test fun unsupportedHardwareFailsBeforeWriting() = runBlocking {
        MockWebServer().use { server ->
            server.body(status);server.body("""{"items":[]}""")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),ArtifactTest.work(),{}) }
            assertTrue(result.isFailure);assertEquals(2,server.requestCount)
        }
    }
    private val networkModules = """{"items":[{"module_id":"display"},{"module_id":"delay"},{"module_id":"system"},{"module_id":"capability"},{"module_id":"json"}]}"""
    private fun MockWebServer.networkPreflight(allowlist: String, capGroups: String = "", luaModules: String = "") {
        body(status); body(networkModules)
        body("""{"items":[{"group_id":"cap_http_request","default_llm_visible":false}]}""")
        body(org.json.JSONObject().put("search_http_allowlist", allowlist).put("enabled_cap_groups", capGroups)
            .put("enabled_lua_modules", luaModules).toString())
    }
    @Test fun networkModulesMissingFailsBeforeFileWrites() = runBlocking {
        MockWebServer().use { server ->
            server.body(status); server.body(modules)
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),LuaNetworkPolicyTest.work(),{}) }
            assertTrue(result.exceptionOrNull()?.message?.contains("capability") == true)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun blockedWeatherDomainFailsBeforeFileWritesAndWithoutConfigMutation() = runBlocking {
        MockWebServer().use { server ->
            server.networkPreflight("example.com")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),LuaNetworkPolicyTest.work(),{}) }
            assertTrue(result.exceptionOrNull()?.message?.contains("白名单未允许：api.open-meteo.com") == true)
            assertEquals(4,server.requestCount)
            val requests=(0 until 4).map { server.takeRequest() }
            assertTrue(requests.all { it.method=="GET" && it.getHeader("Authorization")==null })
            assertEquals("/api/config?fields=search_http_allowlist,enabled_cap_groups,enabled_lua_modules", requests.last().path)
        }
    }
    @Test fun compiledButDisabledHttpCapabilityIsNotEnough() = runBlocking {
        MockWebServer().use { server ->
            server.networkPreflight("*", "cap_system")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),LuaNetworkPolicyTest.work(),{}) }
            assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 能力未启用") == true)
            assertEquals(4,server.requestCount)
        }
    }
    @Test fun compiledButDisabledLuaModuleIsNotEnough() = runBlocking {
        MockWebServer().use { server ->
            server.networkPreflight("*", "cap_http_request", "display,delay,system")
            val result=runCatching { DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),LuaNetworkPolicyTest.work(),{}) }
            assertTrue(result.exceptionOrNull()?.message?.contains("未启用作品需要的 Lua 模块") == true)
            assertEquals(4,server.requestCount)
        }
    }
    @Test fun allowedNetworkWorkUploadsUsingExistingFirmwarePackageFormat() = runBlocking {
        MockWebServer().use { server ->
            val work=LuaNetworkPolicyTest.work()
            server.networkPreflight("api.open-meteo.com", "cap_system, cap_http_request", "display,delay,system,json,capability")
            server.body("""{"entries":[]}""")
            server.body("""{"ok":true}""");server.body("""{"ok":true}""");server.body(work.lua)
            server.body("""{"ok":true}""");server.body(work.manifest())
            DeviceClient().uploadWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{})
            assertEquals(10,server.requestCount)
            val requests=(0 until 10).map { server.takeRequest() }
            assertTrue(requests.take(5).all { it.method=="GET" })
            assertEquals("/api/files/mkdir",requests[5].path)
            assertTrue(requests[8].requestUrl!!.queryParameter("path")!!.endsWith("launcher.json"))
            assertTrue(requests.all { it.getHeader("Authorization")==null })
        }
    }
    @Test fun previouslyUploadedWorkRechecksRevokedNetworkAccessBeforeStart() = runBlocking {
        MockWebServer().use { server ->
            server.networkPreflight("")
            val result=runCatching { DeviceClient().verifyWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),LuaNetworkPolicyTest.work()) }
            assertTrue(result.exceptionOrNull()?.message?.contains("白名单") == true)
            assertEquals(4,server.requestCount)
            repeat(4) { assertEquals("GET",server.takeRequest().method) }
        }
    }
    @Test fun errorResponseDoesNotEchoSensitiveBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("secret-test-value"))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build()) }
            assertTrue(result.isFailure);assertFalse(result.exceptionOrNull()!!.message!!.contains("secret-test-value"))
        }
    }
    @Test fun redirectIsNotFollowed() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","http://8.8.8.8/"))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build()) }
            assertTrue(result.isFailure);assertEquals(1,server.requestCount)
        }
    }
    @Test fun responseSizeIsBounded() = runBlocking {
        MockWebServer().use { server ->
            server.body("x".repeat(30))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build(),10) }
            assertTrue(result.isFailure)
        }
    }
}
