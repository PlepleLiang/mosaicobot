package dev.mosaicobot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Secondary = Color(0xFF62665F)

@Composable
internal fun DeviceSettings(s: BotState, vm: BotViewModel, onConfigure: () -> Unit) {
    var manage by rememberSaveable { mutableStateOf(false) }
    var agent by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp, vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("YOUR PHYSICAL COMPANION",fontFamily=FontFamily.Monospace,fontSize=10.sp,color=Green)
            Text("我的 Mosaico",fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text(if(s.device!=null) "它在这里，等待下一个想法。" else "连接你的 Mosaico，让想法有个身体。",fontSize=14.sp,color=Secondary)
        }
        Surface(color=Soft,shape=MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.DeveloperBoard,null,tint=Green,modifier=Modifier.size(36.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Mosaico",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                        Text(when { s.device!=null -> "已连接 / 局域网"; s.connection.scanning -> "正在寻找同一网络的设备…"; else -> "尚未连接 / 可先开始创作" },fontSize=13.sp,color=if(s.device!=null) Green else Secondary)
                    }
                }
                if(s.connection.scanning) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
                OutlinedButton(onClick={manage=true},modifier=Modifier.fillMaxWidth()) { Text("管理连接") }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("这副身体能做什么",fontWeight=FontWeight.SemiBold,fontSize=16.sp)
            val caps=listOf("480 × 480" to "方形触摸屏", "IMU" to "感知姿态", "Audio" to "麦克风与扬声器", "Camera" to "视觉输入")
            caps.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, description) -> Surface(color=Soft,shape=MaterialTheme.shapes.medium,modifier=Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                        Text(description,fontSize=12.sp,color=Secondary)
                    }
                } }
            } }
            Text("硬件规格概览 · 当前手机生成仅接入屏幕 / 触摸",fontSize=11.sp,color=Secondary)
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("准备好后自动运行",fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                    Text("暂未开放 · 当前每个版本确认后运行",fontSize=12.sp,color=Secondary)
                }
                Switch(checked=false,onCheckedChange=null,enabled=false,modifier=Modifier.semantics { contentDescription="准备好后自动运行，暂未开放" })
            }
            Text("相机、录音和外部执行器需要单独授权。当前连接仅验证接口，不等于安全配对。",fontSize=12.sp,color=Secondary,lineHeight=18.sp)
        }
        OutlinedButton(onClick=vm::openClaw,enabled=s.device!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Icon(Icons.Outlined.ChatBubbleOutline,null,modifier=Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("和设备 Claw 对话")
        }
        if(s.device==null) Text("同一 Wi-Fi 下会自动连接。仍未找到？在管理连接中扫描或手填地址。",fontSize=12.sp,color=Secondary)
        HorizontalDivider()
        TextButton(onClick={agent=!agent},modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AutoAwesome,null,tint=Orange)
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text("手机 Agent",color=Ink,fontWeight=FontWeight.SemiBold)
                Text(if(s.configured) "已配置 · 负责创作，不是设备 CLAW" else "配置模型，开始创作",fontSize=11.sp,color=Secondary)
            }
            Icon(if(agent) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(agent) "收起手机 Agent 设置" else "展开手机 Agent 设置")
        }
        if(agent) {
            Text(s.model,fontFamily=FontFamily.Monospace,fontSize=13.sp)
            Text(s.baseUrl,fontSize=12.sp,color=Secondary)
            Text(if(s.configured) "Key 已配置 · Android Keystore 加密保存" else "尚未配置 Key",fontSize=12.sp,color=Green)
            if(s.debugProxyPort!=null) TextButton(onClick=vm::disableDebugProxy,enabled=!s.busy){Text("关闭 USB 通道，改为手机直连")}
            OutlinedButton(onClick=onConfigure,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("模型与接口设置")}
            OutlinedButton(onClick=vm::checkAgent,enabled=!s.busy&&s.configured,modifier=Modifier.fillMaxWidth()){Text("测试模型连接（发送最小请求）")}
            if(s.agentChecked) Text("模型已回复 · 本次连接测试通过",fontSize=12.sp,color=Green)
        }
        if(s.busy) TaskCard(s,vm)
        Text("MosaicoBot 0.1 · 创作在手机，行为在身体",fontSize=11.sp,color=Secondary)
    }
    if(manage) ConnectionDialog(s,vm,onDismiss={manage=false})
}

@Composable
private fun ConnectionDialog(s: BotState, vm: BotViewModel, onDismiss: () -> Unit) {
    var manual by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("管理连接")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动发现并连接",fontWeight=FontWeight.SemiBold)
                    Text("打开 App 或网络变化时扫描",fontSize=12.sp,color=Secondary)
                }
                Switch(checked=s.connection.automatic,onCheckedChange=vm::automaticScan,enabled=!s.busy,
                    modifier=Modifier.semantics { contentDescription="自动发现并连接" })
            }
            Text(s.connection.networkLabel?.let { "当前网络 · " + it } ?: "请连接与 Mosaico 相同的 Wi-Fi",fontSize=12.sp)
            if(s.connection.scanning) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
            Text(s.connection.message,fontSize=13.sp,color=if(s.device!=null) Green else Ink)
            Button(onClick={if(s.connection.scanning) vm.stopScan() else vm.scanDevices()},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) {
                Text(if(s.connection.scanning) "停止扫描" else "扫描并自动连接")
            }
            s.connection.found.forEach { device ->
                OutlinedCard(onClick={vm.connectDevice(device.address)},enabled=!s.busy&&!s.connection.scanning,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Mosaico / Claw · " + if(s.device?.address==device.address) "已连接" else "点此连接",fontWeight=FontWeight.SemiBold)
                        Text(device.address,fontSize=12.sp)
                    }
                }
            }
            s.device?.let { Text("已验证 DATA " + it.storageRoot + " · " + it.modules.size + " 个 Lua 模块",fontSize=12.sp,color=Green) }
            TextButton(onClick={manual=!manual}){Text(if(manual) "收起手动地址" else "找不到设备？手动输入地址")}
            if(manual) {
                OutlinedTextField(value=s.address,onValueChange=vm::address,label={Text("设备局域网地址")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick=vm::inspect,enabled=!s.busy&&s.address.isNotBlank()&&!s.connection.scanning,modifier=Modifier.fillMaxWidth()){Text("连接此地址")}
            }
            Text("扫描只读当前局域网，不上传或运行作品。Claw 接口尚无安全配对，仅在可信网络使用。",fontSize=11.sp,color=Secondary)
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
}

@Composable
internal fun ClawChatScreen(s: ClawState, vm: BotViewModel) {
    BackHandler { vm.closeClaw() }
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val compact = maxHeight < 520.dp
    LaunchedEffect(s.messages.size, compact, maxHeight) { if(s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.size + if(compact) 1 else 0) }
    Scaffold(modifier=Modifier.fillMaxSize(),topBar={
        Column(Modifier.statusBarsPadding().padding(horizontal=16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=vm::closeClaw){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回设备页")}
                Column(Modifier.weight(1f).padding(vertical=if(compact) 4.dp else 12.dp)) {
                    Text("设备 CLAW",fontSize=if(compact) 16.sp else 20.sp,fontWeight=FontWeight.Bold)
                    if(!compact) Text(s.address ?: "未连接",fontSize=11.sp,color=Secondary)
                }
                Icon(Icons.Outlined.SmartToy,null,tint=Green)
            }
            if(!compact) Text("Mosaico 上的 Agent · 与手机创作会话独立",fontSize=12.sp,color=Green,modifier=Modifier.padding(start=12.dp,bottom=12.dp))
            HorizontalDivider()
        }
    },bottomBar={
        Surface(color=Color.White,shadowElevation=2.dp) {
            Column(Modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=if(compact) 4.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(s.connecting) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Green)
                if(!compact) Text(s.notice,fontSize=12.sp,color=Secondary,lineHeight=18.sp)
                if(!compact&&!s.ready&&!s.connecting) OutlinedButton(onClick=vm::reconnectClaw,modifier=Modifier.fillMaxWidth()){Text("重新连接设备会话")}
                Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(value=s.draft,onValueChange=vm.claw::draft,label={Text("对设备 CLAW 说点什么")},
                    modifier=Modifier.weight(1f),maxLines=if(compact) 1 else 4,trailingIcon={
                        IconButton(onClick=vm.claw::send,enabled=s.ready&&!s.sending&&s.draft.isNotBlank()&&s.messages.size<200) { Icon(Icons.Outlined.ArrowUpward,"发送给设备 CLAW") }
                    })
                if(compact&&!s.ready&&!s.connecting) IconButton(onClick=vm::reconnectClaw) { Icon(Icons.Outlined.Refresh,"重新连接设备会话") }
                }
                if(!compact&&s.messages.size>=200) Text("本次会话已达消息上限。请退出并重启 App 后新建会话。",fontSize=11.sp,color=Secondary)
            }
        }
    }) { padding ->
        LazyColumn(state=list,modifier=Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(horizontal=24.dp,vertical=if(compact) 8.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Surface(color=Soft,shape=MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("和这副身体聊聊",fontWeight=FontWeight.SemiBold)
                    Text("消息发给设备自身的 CLAW，使用设备已有的模型与工具配置。不会使用或同步手机模型 Key，也不会把回复当作创作作品。",fontSize=13.sp,lineHeight=20.sp)
                    Text("指令可能调用设备能力；相机、录音等敏感行为需先确认权限。仅在可信局域网使用，不要发送密钥。会话仅在本次 App 进程保留，离线回复无法补拉。",fontSize=11.sp,color=Secondary,lineHeight=17.sp)
                    if(s.messages.isEmpty()) TextButton(onClick={vm.claw.draft("你好，请只用一句话介绍你自己，不要调用工具或操作硬件。")}) { Text("先打个招呼") }
                }
            } }
            items(s.messages) { message ->
                Column(Modifier.fillMaxWidth(),horizontalAlignment=if(message.role=="user") Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(message.role=="user") "你" else "设备 CLAW",fontSize=11.sp,color=if(message.role=="user") Secondary else Green)
                    Surface(color=if(message.role=="user") Soft else Color(0xFFEDF4ED),shape=MaterialTheme.shapes.medium) {
                        SelectionContainer { Text(message.text,Modifier.padding(14.dp),fontSize=15.sp,lineHeight=23.sp) }
                    }
                    if(message.delivery.isNotEmpty()) Text(message.delivery,fontSize=11.sp,color=Secondary)
                }
            }
            if(compact) item { Text(s.notice,fontSize=12.sp,color=Secondary,lineHeight=18.sp) }
        }
    }
    }
}
