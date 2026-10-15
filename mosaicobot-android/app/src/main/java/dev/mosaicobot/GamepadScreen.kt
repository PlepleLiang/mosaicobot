package dev.mosaicobot

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.math.roundToInt

private object PadColors {
    val background=androidx.compose.ui.graphics.Color(0xFF202623)
    val panel=androidx.compose.ui.graphics.Color(0xFF2C352E)
    val line=androidx.compose.ui.graphics.Color(0xFF718069)
    val text=androidx.compose.ui.graphics.Color(0xFFF1F2E8)
    val muted=androidx.compose.ui.graphics.Color(0xFFBEC8B6)
    val active=androidx.compose.ui.graphics.Color(0xFFD2E8B3)
    val orange=androidx.compose.ui.graphics.Color(0xFFEFA076)
}
@Composable
internal fun GamepadEntry(onClick:()->Unit,enabled:Boolean) {
    Surface(onClick=onClick,enabled=enabled,color=androidx.compose.ui.graphics.Color(0xFFEDF1E8),shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,AppLine),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AppArtwork(WorkshopIcons.Game)
            Column(Modifier.weight(1f)) {
                Text("让手机，成为手柄",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                Text("横屏游戏模式 · 在 Mosaico 上玩",fontSize=11.sp,color=AppSecondary,modifier=Modifier.padding(top=5.dp))
            }
            Icon(WorkshopIcons.Arrow,null,modifier=Modifier.size(18.dp))
        }
    }
}
@Composable
internal fun GamepadScreen(bot:BotState,vm:BotViewModel) {
    val state by vm.gamepad.state.collectAsStateWithLifecycle()
    var menu by remember {mutableStateOf(false)}
    var help by remember {mutableStateOf(false)}
    var exit by remember {mutableStateOf(false)}
    val activity=LocalContext.current as Activity
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val ready by rememberUpdatedState(state.ready)
    var panelSize by remember {mutableStateOf<androidx.compose.ui.unit.IntSize?>(null)}
    DisposableEffect(activity,view,lifecycle) {
        val previousOrientation=activity.requestedOrientation
        val keepAwake=view.keepScreenOn
        val bars=WindowCompat.getInsetsController(activity.window,view)
        val previousBehavior=bars.systemBarsBehavior
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bars.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars());view.keepScreenOn=true
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_PAUSE) {vm.gamepad.disconnect("已离开控制前台，输入已释放");menu=true}
        }
        lifecycle.addObserver(observer)
        val displays=activity.getSystemService(DisplayManager::class.java)
        var rotation=view.display?.rotation
        val rotationListener=object:DisplayManager.DisplayListener {
            override fun onDisplayAdded(id:Int)=Unit
            override fun onDisplayRemoved(id:Int)=Unit
            override fun onDisplayChanged(id:Int) {
                val next=view.display?.rotation
                if(next!=rotation) {rotation=next;vm.gamepad.zero();if(ready) menu=true}
            }
        }
        displays.registerDisplayListener(rotationListener,Handler(Looper.getMainLooper()))
        val focusListener=android.view.ViewTreeObserver.OnWindowFocusChangeListener {focused ->
            if(!focused) {vm.gamepad.zero();if(ready) menu=true}
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            vm.gamepad.disconnect();lifecycle.removeObserver(observer)
            displays.unregisterDisplayListener(rotationListener)
            if(view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            activity.requestedOrientation=previousOrientation;view.keepScreenOn=keepAwake
            bars.systemBarsBehavior=previousBehavior;bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(bot.device?.address,bot.foreground,state.status,menu,help,exit) {
        if(bot.foreground && bot.device!=null && !menu && !help && !exit && state.status in listOf("idle","busy")) {
            delay(if(state.status=="busy") 2000 else 700)
            vm.connectGamepad()
        }
    }
    fun openMenu() {vm.gamepad.zero();menu=true}
    BackHandler { if(help) help=false else if(exit) exit=false else openMenu() }
    val enabled=state.ready&&!menu&&!help&&!exit
    CompositionLocalProvider(LocalContentColor provides PadColors.text) {
        BoxWithConstraints(Modifier.fillMaxSize().background(PadColors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=16.dp,vertical=8.dp)
            .onSizeChanged {size -> if(panelSize!=null && panelSize!=size) {vm.gamepad.zero();if(ready) menu=true};panelSize=size}) {
            val enoughSpace=maxWidth>=600.dp && maxHeight>=300.dp
            if(!enoughSpace) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("横过来，双手开玩",fontSize=23.sp,color=PadColors.text)
                    Text("请横放手机并退出分屏，保留完整手柄操作空间。",fontSize=12.sp,color=PadColors.muted,modifier=Modifier.padding(16.dp))
                    Button(onClick=vm::closeGamepad) {Text("返回应用")}
                }
                return@BoxWithConstraints
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if(state.ready) "● 控制已就绪" else "○ "+if(bot.device==null) "未连接设备" else "等待控制",fontSize=11.sp,color=PadColors.active)
                        Text(bot.device?.address?.removePrefix("http://") ?: "同一 Wi-Fi 自动发现",fontSize=9.sp,color=PadColors.muted,maxLines=1)
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("M O S A I C O",fontFamily=FontFamily.Monospace,fontSize=12.sp)
                        Text("WIRELESS GAMEPAD",fontFamily=FontFamily.Monospace,fontSize=9.sp,color=PadColors.muted)
                    }
                    Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) {
                        OutlinedButton(onClick={openMenu()},border=BorderStroke(1.dp,PadColors.line),contentPadding=PaddingValues(horizontal=14.dp),modifier=Modifier.heightIn(min=48.dp)) {
                            Text("手柄菜单",fontSize=12.sp,color=PadColors.text)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PadButton("L2",PadKey.L2,state,enabled,vm,Modifier.width(72.dp))
                    PadButton("L1",PadKey.L1,state,enabled,vm,Modifier.width(72.dp))
                    Text("标准输入 · 游戏决定按键用途",fontSize=10.sp,color=PadColors.muted,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    PadButton("R1",PadKey.R1,state,enabled,vm,Modifier.width(72.dp))
                    PadButton("R2",PadKey.R2,state,enabled,vm,Modifier.width(72.dp))
                }
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val stickSize=if(maxWidth<700.dp) 104.dp else 124.dp
                    val space=if(maxWidth<640.dp) 4.dp else 8.dp
                    val clusterSize=if(maxHeight<216.dp || maxWidth<640.dp) 144.dp else 160.dp
                    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(space)) {
                        Column(Modifier.align(Alignment.Bottom),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            PadStick(false,state,enabled,vm,Modifier.size(stickSize))
                            PadButton("L3",PadKey.L3,state,enabled,vm,Modifier.width(64.dp),round=true)
                        }
                        PadCluster(false,state,enabled,vm,clusterSize)
                        Spacer(Modifier.weight(1f))
                        PadCluster(true,state,enabled,vm,clusterSize)
                        Column(Modifier.align(Alignment.Bottom),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            PadStick(true,state,enabled,vm,Modifier.size(stickSize))
                            PadButton("R3",PadKey.R3,state,enabled,vm,Modifier.width(64.dp),round=true)
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PadButton("SELECT",PadKey.SELECT,state,enabled,vm,Modifier.width(88.dp),round=true)
                        PadButton("START",PadKey.START,state,enabled,vm,Modifier.width(88.dp),round=true)
                    }
                }
            }
            if(!state.ready||menu||help||exit) {
                Box(Modifier.fillMaxSize().background(PadColors.background.copy(alpha=0.9f)),contentAlignment=Alignment.Center) {
                    Surface(color=androidx.compose.ui.graphics.Color(0xFFF5F6EE),contentColor=Ink,shape=RoundedCornerShape(22.dp),
                        border=BorderStroke(1.dp,PadColors.line),modifier=Modifier.widthIn(max=480.dp).padding(12.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("WIRELESS GAMEPAD",fontFamily=FontFamily.Monospace,fontSize=10.sp,color=Green)
                            Text(when {exit->"退出游戏模式？";help->"一只通用手柄";menu->"手柄菜单";bot.device==null->"连接你的 Mosaico";state.status=="waiting_game"->"等待游戏运行";state.status=="busy"->"手柄暂不可用";state.status=="unsupported"->"需要更新固件";else->"正在连接手柄"},fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                            Text(when {
                                exit->"仅释放手柄，不会停止或卸载游戏。游戏可能继续，请先用游戏内的 Start 暂停或保存。"
                                help->"同一可信局域网内，进入手柄自动连接，无需编号或设备确认。固定方向键、双摇杆、ABXY、肩键、L3/R3、Start/Select。当前三款 v2/v3 游戏：A 开始/重试，方向键或 LS 移动；坦克 A/R1 开火；Start 暂停/继续。原触屏保留，其他旧游戏需接入 gamepad。"
                                menu->"输入已归零，菜单不等于游戏暂停。继续控制不会自动启动、停止或切换游戏。"
                                bot.device==null->bot.connection.message
                                else->state.message
                            },fontSize=12.sp,lineHeight=20.sp,color=AppSecondary)
                            if(!menu&&!exit&&!help&&state.status=="waiting_game") Text("先从我的创作运行坦克 / 飞机 / 赛车 v2 或 v3；没有游戏可到应用中心获取。不会自动替换或启动作品。",fontSize=12.sp,lineHeight=20.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                when {
                                    exit -> {Button(onClick=vm::closeGamepad){Text("退出手柄")};OutlinedButton(onClick={exit=false}){Text("返回")}}
                                    help -> Button(onClick={help=false;menu=true}){Text("知道了")}
                                    menu -> {
                                        Button(onClick={menu=false;if(!state.ready) vm.connectGamepad()}){Text(if(state.ready) "继续控制" else "返回手柄")}
                                        OutlinedButton(onClick={help=true}){Text("使用帮助")}
                                        TextButton(onClick={exit=true}){Text("退出")}
                                    }
                                    bot.device==null -> {
                                        Button(onClick=vm::scanDevices,enabled=!bot.connection.scanning){Text(if(bot.connection.scanning) "扫描中" else "扫描设备")}
                                        TextButton(onClick={vm.closeGamepad();vm.tab(2)}){Text("手动连接")}
                                        TextButton(onClick={exit=true}){Text("退出")}
                                    }
                                    else -> {
                                        if(state.status=="waiting_game") {
                                            Button(onClick=vm::closeGamepad){Text("返回选择游戏")}
                                        } else if(state.status=="unsupported") {
                                            Button(onClick=vm::connectGamepad){Text("重新检查")}
                                        }
                                        TextButton(onClick={openMenu()}){Text("菜单 / 退出")}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun PadButton(label:String,bit:Int,state:PadState,enabled:Boolean,vm:BotViewModel,modifier:Modifier=Modifier,round:Boolean=false) {
    val pressed=state.input.held and bit != 0
    val tone=when {pressed->PadColors.text;bit==PadKey.A->PadColors.active;bit==PadKey.B->PadColors.orange;else->PadColors.panel}
    val shape=if(round) CircleShape else RoundedCornerShape(12.dp)
    Box(modifier.height(48.dp).alpha(if(enabled) 1f else 0.3f).background(tone,shape).border(1.dp,PadColors.line,shape)
        .semantics {contentDescription=label;role=Role.Button;stateDescription=if(pressed) "按下" else "松开";if(!enabled) disabled()
            onClick {if(enabled) {vm.gamepad.button(bit,true);vm.gamepad.button(bit,false)};enabled}}
        .pointerInput(bit,enabled) {
            if(enabled) detectTapGestures(onPress={
                vm.gamepad.button(bit,true)
                try {tryAwaitRelease()} finally {vm.gamepad.button(bit,false)}
            })
        },contentAlignment=Alignment.Center) {
        Text(label,fontSize=if(label.length>2) 11.sp else 18.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold,
            color=if(pressed||bit==PadKey.A||bit==PadKey.B) PadColors.background else PadColors.text)
    }
}
@Composable
private fun PadCluster(face:Boolean,state:PadState,enabled:Boolean,vm:BotViewModel,size:androidx.compose.ui.unit.Dp) {
    val labels=if(face) listOf("Y","X","B","A") else listOf("↑","←","→","↓")
    val bits=if(face) listOf(PadKey.Y,PadKey.X,PadKey.B,PadKey.A) else listOf(PadKey.UP,PadKey.LEFT,PadKey.RIGHT,PadKey.DOWN)
    Box(Modifier.size(size)) {
        val alignments=listOf(Alignment.TopCenter,Alignment.CenterStart,Alignment.CenterEnd,Alignment.BottomCenter)
        bits.forEachIndexed {i,bit -> PadButton(labels[i],bit,state,enabled,vm,Modifier.width(48.dp).align(alignments[i]),round=face)}
        if(!face) Text("+",color=PadColors.muted,fontSize=16.sp,modifier=Modifier.align(Alignment.Center))
    }
}
@Composable
private fun PadStick(right:Boolean,state:PadState,enabled:Boolean,vm:BotViewModel,modifier:Modifier) {
    val x=if(right) state.input.rx else state.input.lx
    val y=if(right) state.input.ry else state.input.ly
    BoxWithConstraints(modifier.alpha(if(enabled) 1f else 0.3f).background(PadColors.panel,CircleShape).border(1.dp,PadColors.line,CircleShape)
        .semantics {contentDescription=if(right) "右模拟摇杆 RS" else "左模拟摇杆 LS";if(!enabled) disabled()}
        .pointerInput(right,enabled) {
            if(enabled) awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                fun move(position:androidx.compose.ui.geometry.Offset) {
                    val dx=(position.x-size.width/2f)/(size.width*0.32f)
                    val dy=(position.y-size.height/2f)/(size.height*0.32f)
                    val length=sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
                    vm.gamepad.stick(right,dx/length,dy/length)
                }
                try {
                    down.consume();move(down.position)
                    do {
                        val event=awaitPointerEvent()
                        val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
                        if(!pointer.pressed) break
                        pointer.consume();move(pointer.position)
                    } while(true)
                } finally {vm.gamepad.stick(right,0f,0f)}
            }
        },contentAlignment=Alignment.Center) {
        Box(Modifier.size(maxWidth*0.75f).border(1.dp,PadColors.line,CircleShape))
        val density=androidx.compose.ui.platform.LocalDensity.current
        val radius=with(density) {(maxWidth*0.25f).toPx()}
        Box(Modifier.offset {IntOffset((x*radius).roundToInt(),(y*radius).roundToInt())}.size(maxWidth*0.52f)
            .background(androidx.compose.ui.graphics.Color(0xFF46563E),CircleShape).border(1.dp,PadColors.muted,CircleShape),contentAlignment=Alignment.Center) {
            Text(if(right) "RS" else "LS",fontFamily=FontFamily.Monospace,fontSize=11.sp,color=PadColors.active)
        }
    }
}
