package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test

class ArtifactEnvelopeTest {
    private val fence = 96.toChar().toString().repeat(3)
    @Test fun rawLuaPreservesQuotesBackslashesNewlinesAndUnicodeExactly() {
        val source = ArtifactTest.lua + """

            local message = 'He said "Hello"'
            local pattern = "^HTTP (%d+)\n(.*)$"
            local text = [=[{"city":"上海","value":"\\n"}]=]
        """.trimIndent() + "\n"
        val work = ArtifactValidator.parse(ArtifactTest.rawResponse(source), "生成完整应用")
        assertEquals(source, work.lua)
        assertEquals("屏幕时钟", work.title)
        assertEquals(work, Work.fromJson(work.json()))
    }
    @Test fun weatherLuaNoLongerNeedsJsonStringEscaping() {
        val work = ArtifactValidator.parse(ArtifactTest.rawResponse(LuaNetworkPolicyTest.lua), "天气")
        assertEquals(LuaNetworkPolicyTest.lua, work.lua)
        assertTrue(work.usesNetwork)
    }
    @Test fun completeSingleOuterLuaFenceIsAccepted() {
        val output = fence + "lua\n" + ArtifactTest.rawResponse() + "\n" + fence
        assertEquals(ArtifactTest.lua, ArtifactValidator.parse(output, "x").lua)
    }
    @Test fun crlfAndBomDoNotChangeTheSource() {
        val source = ArtifactTest.lua.replace("\n", "\r\n")
        val output = "\uFEFF" + ArtifactTest.rawResponse().replace("\n", "\r\n")
        assertEquals(source, ArtifactValidator.parse(output, "x").lua)
    }
    @Test fun missingEndMarkerCannotSaveEvenSyntacticallyValidLua() {
        val output = ArtifactTest.rawResponse().removeSuffix("\n-- MOSAICOBOT_END")
        val error = assertThrows(ArtifactFormatException::class.java) { ArtifactValidator.parse(output, "x") }
        assertTrue(error.incomplete)
        assertTrue(error.message!!.contains("结束标记"))
    }
    @Test fun unclosedOuterFenceCannotSaveAnOtherwiseCompleteApp() {
        val error = assertThrows(ArtifactFormatException::class.java) {
            ArtifactValidator.parse(fence + "lua\n" + ArtifactTest.rawResponse(), "x")
        }
        assertTrue(error.incomplete)
    }
    @Test fun duplicateAppsMarkersAndTrailingSourceAreRejected() {
        val full = ArtifactTest.rawResponse()
        listOf(full + "\n" + full, full + "\n-- MOSAICOBOT_END", full + "\nprint('extra')",
            full + "\n" + ArtifactTest.response(), full + "\n-- MOSAICOBOT_APP_V1").forEach {
            assertThrows(ArtifactFormatException::class.java) { ArtifactValidator.parse(it, "x") }
        }
    }
    @Test fun badHeaderAndEmptyTitleAreRejected() {
        listOf(ArtifactTest.rawResponse().replace("-- title:", "title:"),
            ArtifactTest.rawResponse().replace("-- title: 屏幕时钟", "-- title: ")).forEach {
            assertThrows(IllegalArgumentException::class.java) { ArtifactValidator.parse(it, "x") }
        }
    }
    @Test fun finalMarkerDoesNotBypassLuaValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactValidator.parse(ArtifactTest.rawResponse(ArtifactTest.lua + "\nif then"), "x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactValidator.parse(ArtifactTest.rawResponse(ArtifactTest.lua + "\nlocal a=require('camera')"), "x")
        }
    }
    @Test fun malformedLegacyJsonGivesFormatErrorWithoutEchoingSource() {
        val invalid = """{"title":"时钟","summary":"说明","lua":"local x = "private-source-value""}"""
        val error = assertThrows(ArtifactFormatException::class.java) { ArtifactValidator.parse(invalid, "x") }
        assertFalse(error.incomplete)
        assertTrue(error.message!!.contains("JSON 包装损坏"))
        assertFalse(error.message!!.contains("private-source-value"))
    }
    @Test fun legacyJsonAndRefusalRemainSupported() {
        assertEquals(ArtifactTest.lua, ArtifactValidator.parse(ArtifactTest.response(), "x").lua)
        assertThrows(UnsupportedOperationException::class.java) {
            ArtifactValidator.parse("""{"refusal":"不支持此能力"}""", "x")
        }
    }
    @Test fun framingMetadataIsNotWrittenIntoLuaOrItsDigest() {
        val old = ArtifactTest.work()
        val current = ArtifactValidator.parse(ArtifactTest.rawResponse(), "x", old)
        assertEquals(old.id, current.id)
        assertEquals(2, current.version)
        assertEquals(old.sha256, current.sha256)
    }
}
