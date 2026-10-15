package dev.mosaicobot

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

internal object PadKey {
    const val UP=1; const val DOWN=2; const val LEFT=4; const val RIGHT=8
    const val A=16; const val B=32; const val X=64; const val Y=128
    const val L1=256; const val R1=512; const val L2=1024; const val R2=2048
    const val SELECT=4096; const val START=8192; const val L3=16384; const val R3=32768
}
internal data class PadInput(val held:Int=0,val lx:Float=0f,val ly:Float=0f,val rx:Float=0f,val ry:Float=0f) {
    fun button(bit:Int,down:Boolean)=copy(held=if(down) held or bit else held and bit.inv())
    fun stick(right:Boolean,x:Float,y:Float):PadInput {
        val xx=if(x.isFinite()) x.coerceIn(-1f,1f) else 0f
        val yy=if(y.isFinite()) y.coerceIn(-1f,1f) else 0f
        return if(right) copy(rx=xx,ry=yy) else copy(lx=xx,ly=yy)
    }
    fun packet(seq:Int,token:String,run:String)=JSONObject().put("v",1).put("type","state")
        .put("seq",seq).put("token",token).put("run",run).put("held",held)
        .put("lx",lx).put("ly",ly).put("rx",rx).put("ry",ry)
}
internal data class PadState(val status:String="idle",val message:String="连接同一 Wi-Fi 后自动检查手柄",val input:PadInput=PadInput()) {
    val ready get()=status=="ready"
}
/** Main-thread owner. WS callbacks are dispatched into the ViewModel scope and epoch-checked. */
internal class GamepadSession(private val scope:CoroutineScope,private val clock:()->Long={System.nanoTime()/1_000_000}) {
    private val mutable=MutableStateFlow(PadState())
    val state=mutable.asStateFlow()
    private var socket:WebSocket?=null
    private var ticker:Job?=null
    private var epoch=0L
    private var token=""; private var run=""
    private var seq=0; private var ack=0
    private var lastReply=0L; private var lastAck=0L
    fun connect(client:OkHttpClient,address:String) {
        disconnect("正在建立手柄连接")
        val generation=epoch
        mutable.value=PadState("connecting","正在检查设备与游戏是否可用")
        val url=DeviceClient.address(address).newBuilder().encodedPath("/ws/gamepad").build()
        socket=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { scope.launch {
                if(generation!=epoch) {webSocket.cancel();return@launch}
                Log.d("MosaicoPad","opened epoch=$generation")
                lastReply=clock();lastAck=lastReply
                ticker=scope.launch {
                    while(true) {
                        if(clock()-lastReply>2500 || (token.isNotEmpty() && clock()-lastAck>2500)) {
                            disconnect("输入回执超时，已释放；正在重新连接");break
                        }
                        // Bound heartbeat traffic during delayed ACKs, reserving room for button edges.
                        if(token.isNotEmpty()) {
                            if(seq-ack<8) sendInput()
                        } else send(JSONObject().put("v",1).put("type","poll"))
                        delay(if(token.isNotEmpty()) 33 else 150)
                    }
                }
            } }
            override fun onMessage(webSocket:WebSocket,text:String) { scope.launch {
                if(generation!=epoch) return@launch
                if(text.length>1024) {disconnect("手柄响应无效，已释放");return@launch}
                try {
                    val j=JSONObject(text);require(j.getInt("v")==1)
                    lastReply=clock()
                    if(j.optString("access")!="automatic") {
                        disconnect("设备仍使用旧版确认流程，请更新 Mosaico 固件","unsupported");return@launch
                    }
                    when(j.getString("status")) {
                        "ready" -> {
                            val nextToken=j.getString("token");val nextRun=j.getString("run")
                            require(nextToken.matches(Regex("[a-f0-9]{32}"))&&nextRun.matches(Regex("[a-f0-9]{32}")))
                            val initial=token.isEmpty()
                            if(initial) {
                                require(j.getInt("seq")==0)
                                token=nextToken;run=nextRun;lastAck=clock()
                                Log.d("MosaicoPad","syncing epoch=$generation")
                                mutable.value=PadState("syncing","正在同步手柄输入")
                            } else require(token==nextToken&&run==nextRun)
                            val received=j.getInt("seq");require(received in ack..seq)
                            if(received>ack) {
                                val gap=clock()-lastAck
                                if(ack>0 && gap>350) Log.w("MosaicoPad","delayed ACK epoch=$generation gap=${gap}ms sent=$seq ack=$received")
                                if(ack==0) Log.d("MosaicoPad","ready epoch=$generation")
                                if(received/300>ack/300) Log.d("MosaicoPad","stream epoch=$generation sent=$seq ack=$received gap=${clock()-lastAck}ms")
                                ack=received;lastAck=clock()
                                mutable.value=mutable.value.copy(status="ready",message="控制已就绪")
                            }
                            if(initial) sendInput() // Zero state now; do not wait for the next polling tick.
                        }
                        "waiting_game" -> {
                            require(token.isEmpty())
                            mutable.value=PadState("waiting_game","请先在 Mosaico 运行支持手柄的游戏；运行后自动连接")
                        }
                        else -> disconnect("游戏或连接已变化，输入已释放；正在重新检查")
                    }
                } catch(_:Exception) {disconnect("会话已变化或响应无效，未继续发送输入")}
            } }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {scope.launch {
                if(generation==epoch) Log.d("MosaicoPad","failure http=${response?.code} ${t.javaClass.simpleName}")
                if(generation==epoch) when(response?.code) {
                    404 -> disconnect("固件尚不支持手柄，请更新 Mosaico","unsupported")
                    409 -> disconnect("手柄通道暂不可用或被其他手机占用，正在等待释放","busy")
                    else -> disconnect("手柄连接中断，输入已释放；正在重新连接")
                }
            }}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {webSocket.close(code,null)}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {scope.launch {if(generation==epoch) disconnect("设备已结束控制会话")}}
        })
    }
    private fun send(packet:JSONObject):Boolean {
        val ws=socket ?: return false
        if(ws.queueSize()>8192 || !ws.send(packet.toString())) {disconnect("输入积压，已释放；请重新连接");return false}
        return true
    }
    private fun sendInput() {
        if(!mutable.value.ready && mutable.value.status!="syncing") return
        if(seq-ack>=16 || seq==Int.MAX_VALUE) {disconnect("输入未及时确认，已释放；请重试");return}
        send(mutable.value.input.packet(++seq,token,run))
    }
    fun button(bit:Int,down:Boolean) {
        if(!mutable.value.ready) return
        val next=mutable.value.input.button(bit,down)
        if(bit==PadKey.START && next!=mutable.value.input) Log.d("MosaicoPad","Start ${if(down) "down" else "up"} epoch=$epoch seq=$seq")
        if(next!=mutable.value.input) {mutable.value=mutable.value.copy(input=next);sendInput()}
    }
    fun stick(right:Boolean,x:Float,y:Float) {
        if(mutable.value.ready) mutable.value=mutable.value.copy(input=mutable.value.input.stick(right,x,y))
    }
    fun zero() {mutable.value=mutable.value.copy(input=PadInput());sendInput()}
    fun disconnect(message:String="控制已释放，游戏不会被停止",status:String="idle") {
        if(socket!=null) Log.d("MosaicoPad","disconnect epoch=$epoch status=${mutable.value.status} sent=$seq ack=$ack replyAge=${clock()-lastReply} ackAge=${clock()-lastAck}: $message")
        epoch++;ticker?.cancel();ticker=null
        socket?.let {ws ->
            // A best-effort release; the device watchdog remains authoritative if the link is broken.
            ws.send(JSONObject().put("v",1).put("type","release").toString());ws.close(1000,"release")
        }
        socket=null;token="";run="";seq=0;ack=0
        mutable.value=PadState(status,message)
    }
}
