package dev.mosaicobot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface ClawEvent {
    data object Ready : ClawEvent
    data class Reply(val seq: Long, val text: String) : ClawEvent
}
interface ClawTransport {
    fun events(chatId: String): Flow<ClawEvent>
    suspend fun send(chatId: String, text: String)
}

/** Claw WebIM, NOT the phone model. No model credentials, no automatic POST retry. */
class ClawClient(http: OkHttpClient, address: String) : ClawTransport {
    private val base = DeviceClient.address(address)
    private val http = http.newBuilder().retryOnConnectionFailure(false).build()
    override fun events(chatId: String): Flow<ClawEvent> = callbackFlow {
        val status = JSONObject(http.fetch(Request.Builder().url(base.resolve("/api/webim/status")!!).build(), 4096))
        check(status.optBoolean("ok") && status.optBoolean("bound")) { "设备 WebIM 尚未就绪" }
        val socket = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
            .newWebSocket(Request.Builder().url(base.resolve("/ws/webim")!!).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Match the firmware WebImPage: first DATA frame registers the client.
                    if (webSocket.send(JSONObject().put("type", "hello").put("chat_id", chatId).toString()))
                        trySend(ClawEvent.Ready)
                    else close(IllegalStateException("设备会话初始化失败"))
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length > 65536) { close(IllegalStateException("设备消息过大")); return }
                    parseReply(text, chatId)?.let { if (trySend(it).isFailure) close(IllegalStateException("设备消息过快")) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    response?.close(); close(IllegalStateException("设备会话连接中断"))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); close() }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
            })
        val heartbeat = launch {
            while (isActive) {
                delay(15_000)
                if (!socket.send("{\"type\":\"ping\"}")) { close(); break }
            }
        }
        awaitClose { heartbeat.cancel(); socket.cancel() }
    }
    override suspend fun send(chatId: String, text: String) {
        require(text.isNotBlank() && text.length <= 2000)
        val body = JSONObject().put("chat_id", chatId).put("text", text).put("files", org.json.JSONArray())
        val result = JSONObject(http.fetch(Request.Builder().url(base.resolve("/api/webim/send")!!)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build(), 4096))
        check(result.optBoolean("ok")) { "设备未确认接收" }
    }
    companion object {
        internal fun parseReply(payload: String, chatId: String): ClawEvent.Reply? = runCatching {
            if (payload.length > 65536) return null
            val json = JSONObject(payload)
            if (json.optString("chat_id") != chatId || json.optString("role") != "assistant") return null
            val seq = json.getLong("seq")
            val text = json.getString("text")
            if (seq < 0 || text.isBlank()) null else ClawEvent.Reply(seq, text)
        }.getOrNull()
    }
}

data class ClawMessage(val role: String, val text: String, val delivery: String = "")
data class ClawState(
    val address: String? = null, val draft: String = "", val messages: List<ClawMessage> = emptyList(),
    val connecting: Boolean = false, val ready: Boolean = false, val sending: Boolean = false,
    val notice: String = "连接设备后，开始独立的 CLAW 会话",
)

/** Main-thread owner; drafts/history survive page switches, not process death. */
class ClawConversation(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ClawState())
    val state = mutable.asStateFlow()
    private var chatId = newId()
    private var transport: ClawTransport? = null
    private var stream: Job? = null
    private var sending: Job? = null
    private var waiting: Job? = null
    private var epoch = 0L
    private var outgoingIndex: Int? = null
    private fun newId() = "mb_" + UUID.randomUUID().toString().replace("-", "")
    fun draft(text: String) { mutable.value = mutable.value.copy(draft = text.take(2000)) }
    fun connect(address: String, next: ClawTransport) {
        disconnect()
        if (mutable.value.address != address) {
            chatId = newId()
            mutable.value = ClawState(address = address)
        }
        val token = epoch
        transport = next
        mutable.value = mutable.value.copy(connecting = true, notice = "正在连接设备 CLAW…")
        stream = scope.launch {
            val seen = mutableSetOf<Long>()
            try {
                next.events(chatId).collect { event ->
                    if (token != epoch) return@collect
                    when (event) {
                        ClawEvent.Ready -> mutable.value = mutable.value.copy(connecting = false, ready = true,
                            notice = "设备会话已连接 · 回复来自 Mosaico CLAW")
                        is ClawEvent.Reply -> if (seen.add(event.seq)) {
                            waiting?.cancel()
                            if (mutable.value.messages.size >= 200) { disconnect("本次会话达到消息上限，请重启 App 后新建会话。"); return@collect }
                            if (seen.size > 256) seen.remove(seen.first())
                            mutable.value = mutable.value.copy(messages = mutable.value.messages + ClawMessage("claw", event.text),
                                notice = "收到设备 CLAW 消息 · 不代表硬件操作已验收")
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Do not expose arbitrary server payloads. */ }
            finally { if (token == epoch) disconnect("设备会话已断开或尚未就绪，请检查设备 CLAW 配置后重连。未自动重发消息。") }
        }
    }
    fun send() {
        val current = mutable.value
        val target = transport ?: return
        if (!current.ready || current.sending || current.draft.isBlank() || current.messages.size >= 200) return
        val token = epoch
        val index = current.messages.size
        val text = current.draft.trim()
        outgoingIndex = index
        mutable.value = current.copy(draft = "", sending = true,
            messages = current.messages + ClawMessage("user", text, "发送中"), notice = "正在发送到设备 CLAW…")
        sending = scope.launch {
            try {
                target.send(chatId, text)
                if (token == epoch) {
                    mark(index, "设备已接收")
                    mutable.value = mutable.value.copy(notice = "设备已接收，回复将实时显示；接收不等于任务完成。")
                    waiting?.cancel()
                    waiting = scope.launch {
                        delay(60_000)
                        if (token == epoch) mutable.value = mutable.value.copy(notice = "暂未收到新的设备回复。请检查设备 CLAW 配置与网络；不要重复发送操作类指令。")
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == epoch) {
                    mark(index, "接收未确认")
                    mutable.value = mutable.value.copy(draft = mutable.value.draft.ifBlank { text },
                        notice = "发送未确认，设备可能已接收。请先核对，未自动重试。")
                }
            } finally { if (token == epoch) { outgoingIndex = null; mutable.value = mutable.value.copy(sending = false) } }
        }
    }
    private fun mark(index: Int, delivery: String) {
        mutable.value = mutable.value.copy(messages = mutable.value.messages.mapIndexed { i, message ->
            if (i == index) message.copy(delivery = delivery) else message
        })
    }
    fun disconnect(message: String = "会话已暂停。重连后可继续；离线期间的设备回复无法补拉，不会重发消息。") {
        epoch++; stream?.cancel(); sending?.cancel(); waiting?.cancel(); stream = null; sending = null; waiting = null; transport = null
        outgoingIndex?.let { mark(it, "接收未确认") }; outgoingIndex = null
        mutable.value = mutable.value.copy(ready = false, connecting = false, sending = false, notice = message)
    }
}
