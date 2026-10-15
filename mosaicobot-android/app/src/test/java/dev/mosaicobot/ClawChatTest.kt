package dev.mosaicobot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClawChatTest {
    @Test fun onlyOwnAssistantEventsAreAccepted() {
        val own = "mb_test"
        fun event(id:String=own,role:String="assistant",text:String="你好") = JSONObject()
            .put("chat_id",id).put("role",role).put("text",text).put("seq",7).toString()
        assertEquals(ClawEvent.Reply(7,"你好"),ClawClient.parseReply(event(),own))
        assertNull(ClawClient.parseReply(event(id="other"),own))
        assertNull(ClawClient.parseReply(event(role="user"),own))
        assertNull(ClawClient.parseReply(event(text=""),own))
        assertNull(ClawClient.parseReply("not json",own))
        assertNull(ClawClient.parseReply("x".repeat(65537),own))
    }
    @Test fun realWebSocketReceivesReplyAndSendUsesClawContractWithoutCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true,\"bound\":true}"))
            val opened = CompletableDeferred<WebSocket>()
            val hello = CompletableDeferred<String>()
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { opened.complete(webSocket) }
                override fun onMessage(webSocket:WebSocket,text:String) { hello.complete(text) }
            }))
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            server.start()
            val client = ClawClient(OkHttpClient(),server.url("/").toString())
            val events = async { withTimeout(5000) { client.events("mb_test").take(2).toList() } }
            val socket = withTimeout(5000) { opened.await() }
            val handshake = JSONObject(withTimeout(5000) { hello.await() })
            assertEquals("hello",handshake.getString("type")); assertEquals("mb_test",handshake.getString("chat_id"))
            client.send("mb_test","你好，不要调用工具")
            socket.send("{\"chat_id\":\"somebody_else\",\"role\":\"assistant\",\"seq\":1,\"text\":\"private\"}")
            socket.send("{\"chat_id\":\"mb_test\",\"role\":\"assistant\",\"seq\":2,\"text\":\"你好\"}")
            assertEquals(listOf(ClawEvent.Ready,ClawEvent.Reply(2,"你好")),events.await())
            val status = server.takeRequest(); val ws = server.takeRequest(); val send = server.takeRequest()
            assertEquals("/api/webim/status",status.path); assertEquals("/ws/webim",ws.path)
            assertEquals("/api/webim/send",send.path); assertEquals("POST",send.method)
            val body=JSONObject(send.body.readUtf8())
            assertEquals("mb_test",body.getString("chat_id")); assertEquals("你好，不要调用工具",body.getString("text"))
            assertEquals(0,body.getJSONArray("files").length())
            listOf(status,ws,send).forEach { assertNull(it.getHeader("Authorization")); assertNull(it.getHeader("Cookie")) }
        }
    }
    @Test fun unavailableDeviceDoesNotOpenSocket() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true,\"bound\":false}")); server.start()
            try { ClawClient(OkHttpClient(),server.url("/").toString()).events("x").toList(); fail() }
            catch (_: IllegalStateException) { }
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun rejectedSendIsNotSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":false}")); server.start()
            try { ClawClient(OkHttpClient(),server.url("/").toString()).send("x","hi"); fail() }
            catch (_:IllegalStateException) { }
            assertEquals(1,server.requestCount)
        }
    }
    private class Fake : ClawTransport {
        val events = Channel<ClawEvent>(Channel.UNLIMITED)
        var sends = 0
        var id = ""
        var failure = false
        var suspendSend = false
        override fun events(chatId:String):Flow<ClawEvent> { id=chatId; return events.receiveAsFlow() }
        override suspend fun send(chatId:String,text:String) {
            sends++; assertEquals(id,chatId)
            if (suspendSend) awaitCancellation()
            if (failure) error("offline")
        }
    }
    @Test fun acknowledgmentIsNotAnAssistantReplyAndDuplicateEventsAreIgnored() = runTest {
        val fake=Fake(); val c=ClawConversation(this)
        c.connect("http://192.168.1.2",fake);runCurrent()
        c.draft("hi");c.send();runCurrent();assertEquals(0,fake.sends)
        fake.events.send(ClawEvent.Ready);runCurrent();c.send();runCurrent()
        assertEquals(1,c.state.value.messages.size)
        assertEquals("设备已接收",c.state.value.messages.first().delivery)
        fake.events.send(ClawEvent.Reply(1,"hello"));fake.events.send(ClawEvent.Reply(1,"hello"));runCurrent()
        assertEquals(2,c.state.value.messages.size);c.disconnect()
    }
    @Test fun failedPostPreservesDraftAndDoesNotRetry() = runTest {
        val fake=Fake().apply{failure=true};val c=ClawConversation(this)
        c.connect("http://192.168.1.2",fake);runCurrent();fake.events.send(ClawEvent.Ready);runCurrent()
        c.draft("do something");c.send();runCurrent()
        assertEquals("do something",c.state.value.draft)
        assertEquals("接收未确认",c.state.value.messages.first().delivery)
        assertEquals(1,fake.sends);c.disconnect()
    }
    @Test fun disconnectDuringSendMarksUnknownAndReconnectNeverReplays() = runTest {
        val old=Fake().apply{suspendSend=true};val next=Fake();val c=ClawConversation(this)
        c.connect("http://192.168.1.2",old);runCurrent();old.events.send(ClawEvent.Ready);runCurrent()
        c.draft("hi");c.send();runCurrent();c.draft("next draft");c.disconnect();runCurrent()
        assertFalse(c.state.value.ready);assertEquals("接收未确认",c.state.value.messages.first().delivery)
        c.connect("http://192.168.1.2",next);runCurrent();next.events.send(ClawEvent.Ready);runCurrent()
        old.events.send(ClawEvent.Reply(9,"late"));runCurrent()
        assertEquals(1,c.state.value.messages.size);assertEquals("next draft",c.state.value.draft)
        assertEquals(old.id,next.id);assertEquals(0,next.sends);c.disconnect()
    }
    @Test fun explicitlyOpeningAnotherDeviceCreatesSeparateConversation() = runTest {
        val first=Fake();val second=Fake();val c=ClawConversation(this)
        c.connect("http://192.168.1.2",first);runCurrent();c.draft("private draft")
        c.connect("http://192.168.1.3",second);runCurrent()
        assertNotEquals(first.id,second.id);assertTrue(c.state.value.draft.isEmpty());assertTrue(c.state.value.messages.isEmpty())
        c.disconnect()
    }
    @Test fun closedStreamCannotRemainReady() = runTest {
        val fake=Fake();val c=ClawConversation(this)
        c.connect("http://192.168.1.2",fake);runCurrent();fake.events.send(ClawEvent.Ready);runCurrent()
        fake.events.close();runCurrent();assertFalse(c.state.value.ready);assertFalse(c.state.value.connecting)
    }
}
