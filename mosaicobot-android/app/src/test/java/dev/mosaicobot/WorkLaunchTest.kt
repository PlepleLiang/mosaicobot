package dev.mosaicobot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkLaunchTest {
    private val work = ArtifactTest.work()
    private val other = work.copy(id="1111111111111111")
    private fun state(value: String, id: String = work.packageId) = JSONObject().put("ok",true).put("protocol",1).put("id",id).put("state",value).toString()
    private fun listing(vararg ids: String) = JSONObject().put("entries",JSONArray(ids.map { JSONObject().put("name",it) })).toString()
    private fun MockWebServer.body(value: String) { enqueue(MockResponse().setBody(value)) }
    private fun MockWebServer.verified(value: String = "stopped") { body(state(value)); body(work.lua); body(work.manifest()) }
    private fun device(server: MockWebServer) = DeviceInfo(server.url("/").toString().trimEnd('/'),"/nand",setOf("display","delay","system"))
    private fun actions(server: MockWebServer): List<String> = (0 until server.requestCount).map { server.takeRequest() }.filter { it.method=="POST" }.map { JSONObject(it.body.readUtf8()).getString("action") }

    @Test fun installedWorkStartsWithoutUploadOrPublishEvenWhenPhoneLostUploadedFlag() = runBlocking {
        MockWebServer().use { server ->
            server.verified(); server.body(listing(work.packageId)); server.body(state("queued")); server.body(state("running"))
            val d=device(server)
            var prepared: Work?=null
            val result=DeviceClient().launchWork(d,work,{}, {prepared=it})
            assertEquals("running",result?.status)
            assertEquals(d.address,prepared?.uploadedTo)
            assertEquals(6,server.requestCount)
            assertEquals(listOf("start"),actions(server))
        }
    }
    @Test fun alreadyRunningVersionDoesNotSendAnyWriteOrStop() = runBlocking {
        MockWebServer().use { server ->
            server.verified("running")
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            assertEquals(3,server.requestCount)
            assertTrue(actions(server).isEmpty())
        }
    }
    @Test fun alreadyQueuedVersionOnlyWaitsForRunning() = runBlocking {
        MockWebServer().use { server ->
            server.verified("queued");server.body(state("running"))
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            assertEquals(4,server.requestCount)
            assertTrue(actions(server).isEmpty())
        }
    }
    @Test fun switchStopsActualDeviceWorkAndWaitsBeforeStartingTarget() = runBlocking {
        MockWebServer().use { server ->
            server.verified()
            server.body(listing(other.packageId,work.packageId,"dino","../bad","mb_invalid",other.packageId))
            server.body(state("running",other.packageId)); server.body(state("stopping",other.packageId)); server.body(state("stopped",other.packageId))
            server.body(state("queued"));server.body(state("running"))
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            val requests=(0 until server.requestCount).map { server.takeRequest() }
            assertEquals(9,requests.size)
            assertEquals(other.packageId,JSONObject(requests[5].body.readUtf8()).getString("id"))
            assertEquals("GET",requests[6].method)
            assertEquals("start",JSONObject(requests[7].body.readUtf8()).getString("action"))
            assertTrue(requests.all { it.getHeader("Authorization")==null })
            assertFalse(requests.any { it.path!!.contains("dino") || it.path!!.contains("mb_invalid") })
        }
    }
    @Test fun alreadyStoppingPreviousWorkIsWaitedWithoutDuplicateStop() = runBlocking {
        MockWebServer().use { server ->
            server.verified();server.body(listing(other.packageId));server.body(state("stopping",other.packageId));server.body(state("stopped",other.packageId))
            server.body(state("queued"));server.body(state("running"))
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            assertEquals(listOf("start"),actions(server))
        }
    }
    @Test fun stoppedTargetWaitsForItsOwnPendingStopBeforeRestarting() = runBlocking {
        MockWebServer().use { server ->
            server.verified("stopping");server.body(state("stopped"));server.body(listing());server.body(state("queued"));server.body(state("running"))
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            assertEquals(listOf("start"),actions(server))
        }
    }
    @Test fun stopConflictNeverStartsTargetOrEchoesResponsePayload() = runBlocking {
        MockWebServer().use { server ->
            server.verified();server.body(listing(other.packageId));server.body(state("running",other.packageId))
            server.enqueue(MockResponse().setResponseCode(409).setBody("private-error"))
            val result=runCatching { DeviceClient().launchWork(device(server),work,{},{} ) }
            assertTrue(result.exceptionOrNull()?.message?.contains("未继续启动其他作品") == true)
            assertFalse(result.exceptionOrNull()?.message?.contains("private-error") == true)
            assertEquals(listOf("stop"),actions(server))
        }
    }
    @Test fun nativeOrRacingWorkConflictIsActionableAndNotBlindlyRetried() = runBlocking {
        MockWebServer().use { server ->
            server.verified();server.body(listing("dino"))
            server.enqueue(MockResponse().setResponseCode(409).setBody("private-error"))
            val result=runCatching { DeviceClient().launchWork(device(server),work,{},{} ) }
            assertTrue(result.exceptionOrNull()?.message?.contains("其他作品运行或正在停止") == true)
            assertEquals(5,server.requestCount)
            assertEquals(listOf("start"),actions(server))
        }
    }
    @Test fun cancellationBeforeSwitchNeverStopsOrStartsAnything() = runBlocking {
        MockWebServer().use { server ->
            server.verified();server.body(listing(other.packageId));server.body(state("running",other.packageId))
            val result=runCatching { DeviceClient().launchWork(device(server),work,{ if(it.startsWith("停止之前")) throw CancellationException("cancel") },{} ) }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(actions(server).isEmpty())
        }
    }
    @Test fun changedInstalledSourceFailsBeforeAnyStopOrWrite() = runBlocking {
        MockWebServer().use { server ->
            server.body(state("stopped"));server.body("different source")
            assertTrue(runCatching { DeviceClient().launchWork(device(server),work,{},{} ) }.isFailure)
            assertEquals(2,server.requestCount)
            assertTrue(actions(server).isEmpty())
        }
    }
    @Test fun filesTransferredButNotRegisteredAreReusedAndPublishedOnce() = runBlocking {
        MockWebServer().use { server ->
            server.body(state("not_found"))
            server.body("""{"wifi_connected":true,"storage_base_path":"/nand"}""")
            server.body("""{"items":[{"module_id":"display"},{"module_id":"delay"},{"module_id":"system"}]}""")
            server.body(listing("apps"));server.body(listing(work.packageId));server.body(work.lua);server.body(work.manifest())
            server.body(state("stopped"));server.body(listing(work.packageId));server.body(state("queued"));server.body(state("running"))
            assertEquals("running",DeviceClient().launchWork(device(server),work,{},{} )?.status)
            assertEquals(11,server.requestCount)
            assertEquals(listOf("publish","start"),actions(server))
        }
    }
    @Test fun idleWaitHasDeadlineAndDoesNotMistakeNotFoundForStopped() = runTest {
        assertFalse(awaitWorkIdle { WorkRuntime("x",work.packageId,"stopping") })
        assertTrue(runCatching { awaitWorkIdle { WorkRuntime("x",work.packageId,"not_found") } }.isFailure)
        assertTrue(awaitWorkIdle { WorkRuntime("x",work.packageId,"failed") })
    }
}
