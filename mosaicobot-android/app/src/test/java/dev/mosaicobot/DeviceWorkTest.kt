package dev.mosaicobot

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceWorkTest {
    private val id="mb_1234567890abcdef_v2_01234567"
    private val work=DeviceWork(id,"测试 v2",false,"stopped")
    private fun list(vararg works:DeviceWork)=JSONObject().put("ok",true).put("protocol",1).put("items",JSONArray().apply {
        works.forEach { put(JSONObject().put("id",it.id).put("display_name",it.name).put("builtin",it.builtin).put("state",it.status)) }
    }).toString()
    private fun snapshot(state:String)=JSONObject().put("ok",true).put("protocol",1).put("id",id).put("state",state).toString()
    @Test fun presetsAndOtherIdsCannotBeDeleted() {
        assertFalse(work.copy(builtin=true).deletable)
        assertFalse(work.copy(id="dino_app").deletable)
        assertFalse(work.copy(id=id+"/..").deletable)
        assertEquals("2",work.version)
        assertEquals(2,DeviceWork.parse(list(work,DeviceWork("dino_app","Dino",true,"stopped"))).size)
        assertTrue(runCatching { DeviceWork.parse(list(work,work)) }.isFailure)
        assertTrue(runCatching { DeviceWork.parse("{\"ok\":true,\"protocol\":0,\"items\":[]}") }.isFailure)
    }
    @Test fun presetRejectedBeforeAnyRequest()=runBlocking {
        MockWebServer().use { server ->
            server.start()
            assertTrue(runCatching { DeviceClient().deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work.copy(builtin=true),{}) }.isFailure)
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun runningDeletionStopsWaitsDeletesAndReadsBackFiles()=runBlocking {
        MockWebServer().use { server ->
            listOf(list(work.copy(status="running")),snapshot("stopping"),snapshot("stopped"),snapshot("not_found"),list(),"{\"entries\":[]}").forEach { server.enqueue(MockResponse().setBody(it)) }
            server.start()
            DeviceClient(OkHttpClient()).deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{})
            assertEquals("/api/works",server.takeRequest().path)
            assertEquals("stop",JSONObject(server.takeRequest().body.readUtf8()).getString("action"))
            assertEquals("/api/works?id=$id",server.takeRequest().path)
            assertEquals("delete",JSONObject(server.takeRequest().body.readUtf8()).getString("action"))
            assertEquals("/api/works",server.takeRequest().path)
            assertEquals("/api/files?path=/",server.takeRequest().path)
        }
    }
    @Test fun failedStopNeverDeletes()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(list(work.copy(status="running"))))
            server.enqueue(MockResponse().setResponseCode(409));server.start()
            assertTrue(runCatching { DeviceClient().deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{}) }.isFailure)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun stopTimeoutNeverDeletes()=kotlinx.coroutines.test.runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(list(work.copy(status="stopping"))))
            repeat(32) { server.enqueue(MockResponse().setBody(snapshot("stopping"))) };server.start()
            assertTrue(runCatching { DeviceClient().deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{}) }.isFailure)
            repeat(server.requestCount) { assertEquals("GET",server.takeRequest().method) }
        }
    }
    @Test fun filesRemainingNeverConfirmDeletion()=runBlocking {
        MockWebServer().use { server ->
            listOf(list(work),snapshot("not_found"),list(),"{\"entries\":[{\"name\":\"apps\"}]}","{\"entries\":[{\"name\":\"$id\"}]}").forEach { server.enqueue(MockResponse().setBody(it)) };server.start()
            assertTrue(runCatching { DeviceClient().deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{}) }.isFailure)
            assertEquals(5,server.requestCount)
        }
    }
    @Test fun staleDirectoryIsNotSuccessfulDeletion()=runBlocking {
        MockWebServer().use { server ->
            listOf(list(work),snapshot("not_found"),list(work)).forEach { server.enqueue(MockResponse().setBody(it)) };server.start()
            assertTrue(runCatching { DeviceClient().deleteDeviceWork(DeviceInfo(server.url("/").toString(),"/nand",emptySet()),work,{}) }.isFailure)
            assertEquals(3,server.requestCount)
        }
    }
}
