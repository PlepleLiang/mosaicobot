package dev.mosaicobot

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArtifactTest {
    companion object {
        val lua = """
            -- interaction: app
            local display = require("display")
            local delay = require("delay")
            local system = require("system")
            local screen = display.open()
            local start = system.millis()
            screen:begin({clear="#111713"})
            screen:text(20,20,"Hello")
            screen:present()
            delay.delay_ms(33)
            screen:close()
        """.trimIndent()
        fun response(code: String = lua) = JSONObject().put("title", "屏幕时钟").put("summary", "一个可运行的作品").put("lua", code).toString()
        fun rawResponse(code: String = lua) = "-- MOSAICOBOT_APP_V1\n-- title: 屏幕时钟\n-- summary: 一个可运行的作品\n" + code + "\n-- MOSAICOBOT_END"
        fun work() = ArtifactValidator.parse(response(), "生成时钟")
    }
    @Test fun validArtifactCompilesWithoutExecuting() { assertEquals("屏幕时钟", work().title) }
    @Test fun fencedJsonAccepted() {
        val fence=96.toChar().toString().repeat(3)
        assertEquals(1,ArtifactValidator.parse(fence+"json\n"+response()+"\n"+fence,"x").version)
    }
    @Test fun malformedJsonRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.parse("not-json", "x") } }
    @Test fun trailingSecondObjectRejected() {
        assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.parse(response()+response(),"x") }
    }
    @Test fun trailingTruncatedDataRejected() {
        assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.parse(response()+"{\"lua\":", "x") }
    }
    @Test fun incompleteSourceStringRejected() {
        val full=response()
        assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.parse(full.take(full.length/2),"x") }
    }
    @Test fun refusalIsNotReportedAsSuccess() { assertThrows(UnsupportedOperationException::class.java) { ArtifactValidator.parse("""{"refusal":"相机未授权"}""", "x") } }
    @Test fun invalidSyntaxRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\nif then") } }
    @Test fun lua54OnlySyntaxRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\nlocal x=7//2") } }
    @Test fun unsupportedModuleRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\nlocal c=require(\"camera\")") } }
    @Test fun dynamicImportRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\nlocal c=require(args.module)") } }
    @Test fun globalAccessRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\n_G.foo=1") } }
    @Test fun missingYieldRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua.replace("delay.delay_ms(33)", "")) } }
    @Test fun missingCloseRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua.replace("screen:close()", "")) } }
    @Test fun largeSourceRejected() { assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.validateLua(lua+"\n--"+"x".repeat(65536)) } }
    @Test fun versionRetainsIdentityAndChangesPackage() {
        val first=work();val second=ArtifactValidator.parse(response(),"慢一点",first)
        assertEquals(first.id,second.id);assertEquals(2,second.version);assertNotEquals(first.packageId,second.packageId)
    }
    @Test fun manifestMatchesRealClawContract() {
        val work=work();val manifest=JSONObject(work.manifest())
        assertEquals(work.packageId,manifest.getString("id"));assertEquals("scripts/main.lua",manifest.getString("entry"))
        assertEquals(1,manifest.getInt("schema_version"));assertFalse(manifest.has("runtime"))
        assertTrue(work.packageId.length<=63);assertTrue(work.manifest().toByteArray().size<=4096)
    }
    @Test fun diskRoundtripPreservesContent() { val work=work();assertEquals(work,Work.fromJson(work.json())) }
    @Test fun twoAlbumsAreDistinct() {
        assertEquals(14,builtinApps.size);assertEquals(2,builtinApps.count { it.name=="Album" })
        assertEquals(2,builtinApps.filter { it.name=="Album" }.map { it.origin }.toSet().size)
    }
}
