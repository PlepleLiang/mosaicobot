package dev.mosaicobot

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppCenterTest {
    private val work=ArtifactTest.work().copy(intent="private-intent",uploadedTo="http://192.168.1.9")
    private fun snapshot(w:Work=work)=AppCenter.snapshot(w,"我的作品","一个小游戏","游戏",true)
    @Test fun presetsAreSixDistinctOfflineLuaPackages() {
        val apps=AppCenter.presets { File("src/main/assets",it).readText() }
        assertEquals(6,apps.size);assertEquals(6,apps.map {it.id}.distinct().size)
        assertEquals(setOf("坦克大战","飞机大战","午夜赛车","口袋像素宠","光点画板","一刻专注"),apps.map{it.title}.toSet())
        apps.filter { it.category=="游戏" }.forEach { assertEquals(2,it.version);assertTrue(it.id.endsWith("_v2"));assertFalse(it.lua.contains("ALLOW")) }
        apps.forEach { assertTrue(it.preset);assertFalse(it.usesNetwork);assertEquals(if(it.category=="游戏") setOf("display","delay","system","gamepad") else setOf("display","delay","system"),ArtifactValidator.requiredModules(it.lua));ArtifactValidator.validateLua(it.lua) }
    }
    @Test fun snapshotRoundtripPreservesSourceButExcludesPrivateContext() {
        val app=snapshot();val encoded=AppCenter.encode(listOf(app))
        assertFalse(encoded.contains("private-intent"));assertFalse(encoded.contains("192.168.1.9"))
        assertEquals(listOf(app),AppCenter.decode(encoded));assertEquals(work.lua,app.lua)
        assertFalse(app.json().has("intent"));assertFalse(app.json().has("uploadedTo"))
    }
    @Test fun sameSourceVersionHasStableUploadIdentityEvenAfterMetadataEdit() {
        assertEquals(snapshot().id,AppCenter.snapshot(work,"改名","新介绍","工具",true).id)
    }
    @Test fun newSourceOrVersionGetsNewUploadIdentity() {
        assertNotEquals(snapshot().id,snapshot(work.copy(version=2)).id)
        assertNotEquals(snapshot().id,snapshot(work.copy(lua=work.lua+"\n-- changed")).id)
    }
    @Test fun acquireProducesIndependentUninstalledWorkWithTraceableOrigin() {
        val app=snapshot();val a=app.acquire();val b=app.acquire()
        assertNotEquals(a.id,work.id);assertNotEquals(a.id,b.id);assertNull(a.uploadedTo)
        assertEquals(app.id,a.centerOrigin);assertEquals(work.lua,a.lua);assertFalse(a.intent.contains("private-intent"))
        assertEquals(a,app.imported(listOf(a)));assertNull(app.imported(emptyList()))
        assertTrue(a.packageId.matches(Regex("mb_[a-f0-9]{16}_v[1-9][0-9]*_[a-f0-9]{8}")))
    }
    @Test fun acquiringVersionTwoKeepsVersionAndDoesNotOverwriteSnapshot() {
        val app=snapshot(work.copy(version=2));val acquired=app.acquire();val edited=acquired.copy(lua="changed",version=3)
        assertEquals(2,acquired.version);assertEquals(work.lua,app.lua);assertNotEquals(edited.lua,app.lua)
    }
    @Test fun worksStorageRemainsBackwardCompatible() {
        val legacy=work.json().apply { remove("centerOrigin") }
        assertEquals(work,Work.fromJson(legacy))
        val acquired=snapshot().acquire();assertEquals(acquired,Work.fromJson(acquired.json()))
    }
    @Test fun uploadRequiresExplicitConsentAndValidMetadata() {
        assertThrows(IllegalArgumentException::class.java){AppCenter.snapshot(work,"作品","介绍","游戏",false)}
        assertThrows(IllegalArgumentException::class.java){AppCenter.snapshot(work," ","介绍","游戏",true)}
        assertThrows(IllegalArgumentException::class.java){AppCenter.snapshot(work,"中".repeat(17),"介绍","游戏",true)}
        assertThrows(IllegalArgumentException::class.java){AppCenter.snapshot(work,"作品"," ","游戏",true)}
        assertThrows(IllegalArgumentException::class.java){AppCenter.snapshot(work,"作品","介绍","未知",true)}
    }
    @Test fun invalidSourceIsRejectedBeforeSavingOrAcquiring() {
        assertThrows(IllegalArgumentException::class.java){snapshot(work.copy(lua="not lua"))}
        assertThrows(IllegalArgumentException::class.java){snapshot().copy(lua="bad").acquire()}
    }
    @Test fun corruptedOrDuplicateRecordsAreNotSilentlyCleared() {
        assertThrows(Exception::class.java){AppCenter.decode("{")}
        assertThrows(Exception::class.java){AppCenter.decode("["+snapshot().json().put("id","bad")+"]")}
        assertThrows(IllegalArgumentException::class.java){AppCenter.decode(AppCenter.encode(listOf(snapshot(),snapshot())))}
    }
    @Test fun customArtFallsBackWithoutChangingCode() {
        assertEquals("lightboard",AppCenter.snapshot(work,"作品","介绍","游戏",true,"../bad").artwork)
    }
    @Test fun cloudAndDeploymentFieldsAreAbsent() {
        assertEquals(setOf("id","title","summary","category","lua","version","artwork","controls","sourcePackageId"),snapshot().json().keys().asSequence().toSet())
    }
}
