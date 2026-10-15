package dev.mosaicobot

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkRuntimeTest {
    private val work = Work(id="1234567890abcdef", title="Test", summary="Screen", lua="-- test", intent="clock")
    private fun payload(status:String,id:String=work.packageId) = JSONObject().put("ok",true).put("protocol",1).put("id",id).put("state",status).toString()
    @Test fun queuedIsNotRunning() {
        val state=WorkRuntime.parse("http://192.168.1.2",work.packageId,payload("queued"))
        assertEquals("设备正在启动",state.label)
        assertTrue(state.active)
    }
    @Test fun rejectsWrongWorkAndUnknownState() {
        for(json in listOf(payload("running","another"),payload("invented"),"{\"ok\":true}")) {
            assertTrue(runCatching { WorkRuntime.parse("x",work.packageId,json) }.isFailure)
        }
    }
    @Test fun explicitCommandHasNoCredentialsAndAckStillNeedsPoll() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload("queued"))); server.enqueue(MockResponse().setBody(payload("running"))); server.start()
            val device=DeviceInfo(server.url("/").toString().trimEnd('/'),"/nand",emptySet())
            val client=DeviceClient(OkHttpClient())
            assertEquals("queued",client.command(device,work,"start").status)
            assertEquals("running",client.runtime(device,work).status)
            val post=server.takeRequest(); assertEquals("/api/works",post.path)
            val body=JSONObject(post.body.readUtf8());assertEquals("start",body.getString("action"));assertEquals(work.packageId,body.getString("id"))
            assertNull(post.getHeader("Authorization"));assertNull(post.getHeader("Cookie"))
            assertEquals("/api/works?id="+work.packageId,server.takeRequest().path)
        }
    }
    @Test fun mismatchedExistingFilesAreNeverStartedOrOverwritten() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("changed")); server.start()
            val device=DeviceInfo(server.url("/").toString(),"/nand",emptySet())
            assertTrue(runCatching { DeviceClient(OkHttpClient()).verifyWork(device,work) }.isFailure)
            assertEquals(1,server.requestCount);assertEquals("GET",server.takeRequest().method)
        }
    }
    @Test fun existingUploadedVersionCanBeVerifiedWithoutWrites() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(work.lua));server.enqueue(MockResponse().setBody(work.manifest()));server.start()
            DeviceClient(OkHttpClient()).verifyWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work)
            assertEquals(2,server.requestCount);repeat(2){assertEquals("GET",server.takeRequest().method)}
        }
    }
    @Test fun waitsForRunningInsteadOfTreatingQueuedAsSuccess() = runTest {
        var reads=0
        val result=awaitWorkState({ WorkRuntime("x",work.packageId,if(++reads<3) "queued" else "running") },"running")
        assertEquals(3,reads);assertEquals("running",result?.status)
    }
    @Test fun timeoutCannotBecomeSuccessfulRun() = runTest {
        assertNull(awaitWorkState({WorkRuntime("x",work.packageId,"queued")},"running"))
    }
    @Test fun immediateFailureOrExitCannotBecomeSuccessfulRun() = runTest {
        for(status in listOf("failed","stopped")) assertTrue(runCatching { awaitWorkState({WorkRuntime("x",work.packageId,status)},"running") }.isFailure)
    }
}
