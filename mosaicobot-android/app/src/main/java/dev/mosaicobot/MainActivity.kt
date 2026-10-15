package dev.mosaicobot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

internal val Ink = Color(0xFF262824)
internal val Orange = Color(0xFFFF4C01)
internal val Soft = Color(0xFFF5F6F2)
internal val Green = Color(0xFF367655)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Ink, secondary=Green, tertiary=Orange,
                background=Color.White,surface=Color.White,onSurface=Ink,surfaceContainer=Soft,
                secondaryContainer=Color(0xFFE5EDE4),onSecondaryContainer=Green,
                outline=Color(0xFF797E75),outlineVariant=Color(0xFFDDE1D8))) {
                BotApp()
            }
        }
    }
}

@Composable
private fun BotApp(vm: BotViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.foreground(true)
            if (event == Lifecycle.Event.ON_STOP) vm.foreground(false)
        }
        lifecycle.addObserver(observer)
        vm.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); vm.foreground(false) }
    }
    val applicationsScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    var configDialog by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<Work?>(null) }
    var upload by remember { mutableStateOf<Work?>(null) }
    var delete by remember { mutableStateOf<Work?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    if (s.gamepadOpen) {
        GamepadScreen(s,vm)
        return
    }
    if (s.clawOpen) {
        val chat by vm.claw.state.collectAsStateWithLifecycle()
        ClawChatScreen(chat, vm)
        return
    }
    val showConnection = s.tab != 2 && !(s.tab == 1 && s.appsGroup == 1)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier=Modifier.fillMaxSize().imePadding(),
        topBar={ Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=21.dp).padding(top=12.dp,bottom=13.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("MosaicoBot",fontSize=21.sp,lineHeight=27.sp,letterSpacing=(-0.8).sp,fontWeight=FontWeight.Bold)
                        Icon(WorkshopIcons.Spark,null,tint=Orange,modifier=Modifier.padding(start=5.dp).size(19.dp))
                    }
                    Text("随身 AI 硬件工坊",fontSize=10.sp,lineHeight=14.sp,letterSpacing=0.7.sp,color=Color(0xFF62665F),modifier=Modifier.padding(top=3.dp))
                }
                Spacer(Modifier.weight(1f))
                OutlinedIconButton(onClick={ if(s.prompt.isNotBlank() || s.selected!=null) newDialog=true else vm.newCreation() },enabled=!s.busy,border=BorderStroke(1.dp,Color(0xFFE3E4DD)),shape=RoundedCornerShape(14.dp),modifier=Modifier.size(48.dp)) {
                    Icon(WorkshopIcons.Plus,"开始新创作",modifier=Modifier.size(22.dp))
                }
            }
            if (showConnection && !keyboardVisible) Surface(onClick={vm.tab(2)},color=Color(0xFFFAFBF7),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Color(0xFFE3E4DD)),modifier=Modifier.padding(horizontal=17.dp).fillMaxWidth()) {
                Row(Modifier.heightIn(min=62.dp).padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).background(Color(0xFFE9EEE3),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                        Icon(WorkshopIcons.Device,null,tint=Color(0xFF4D6646),modifier=Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if(s.device!=null) "我的 Mosaico" else "连接你的 Mosaico",fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold)
                        Text(when { s.device!=null -> "同一 Wi-Fi · 已连接"; s.connection.scanning -> "正在扫描同一网络…"; s.connection.automatic -> "同一 Wi-Fi 下自动连接"; else -> "自动扫描已关闭 · 点此连接" },fontSize=10.sp,lineHeight=14.sp,color=Color(0xFF62665F),modifier=Modifier.padding(top=1.dp))
                    }
                    Text(if(s.device!=null) "• 在线" else "未连接",fontSize=10.sp,lineHeight=14.sp,color=Green)
                    Icon(WorkshopIcons.Arrow,null,tint=Color(0xFF62665F),modifier=Modifier.size(13.dp))
                }
            }
            if (showConnection && !keyboardVisible) Spacer(Modifier.height(8.dp))
        }},
        bottomBar={ if(!keyboardVisible) CompactNavigation(s.tab,vm::tab) },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if(s.tab == 2) s.error?.let { ErrorNotice(it,vm::clearError,Modifier.padding(horizontal=20.dp,vertical=4.dp)) }
            when(s.tab) {
                0 -> Creation(s,vm,onSource={source=it},onUpload={upload=it},onConfigure={configDialog=true})
                1 -> Applications(s,vm,onSource={source=it},onRun={upload=it},onDelete={delete=it},listState=applicationsScroll)
                2 -> DeviceSettings(s,vm,onConfigure={configDialog=true})
            }
        }
    }
    if(newDialog) AlertDialog(onDismissRequest={newDialog=false},title={Text("开始一个新想法？")},
        text={Text("未发送的输入将清空，已保存的作品和设备上的应用不受影响。")},
        confirmButton={TextButton(onClick={newDialog=false;vm.newCreation()}){Text("开始新创作")}},
        dismissButton={TextButton(onClick={newDialog=false}){Text("继续当前创作")}})
    if(configDialog) ConfigDialog(s,onDismiss={configDialog=false},onSave={base,model,key->vm.configure(base,model,key);configDialog=false})
    source?.let { work -> SourceDialog(work,onDismiss={source=null}) }
    upload?.let { work -> AlertDialog(onDismissRequest={upload=null},title={Text("在 Mosaico 上运行？")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(s.device?.address ?: "尚未连接设备")
            Text("运行「"+work.title+"」v"+work.version+"。设备已有同版本时核对文件后直接启动，不重复上传或发布；仅缺少时部署，不覆盖已有文件。")
            Text("若有其他手机生成作品占用屏幕，将先请求停止并等待释放，再运行此作品。不会自动停止系统/预装应用；停止失败则取消切换。",fontSize=12.sp)
            Text("请先查看源码。语法检查不等于安全沙箱；模型代码可能出错。Claw 当前文件接口未提供安全配对，仅在你信任的局域网使用。")
            if (work.usesNetwork) Text("此作品将由 Mosaico 直接联网读取数据，源码声明域名："+work.networkHosts.joinToString()+"。第三方会收到请求与设备出口 IP；请求期间界面可能短暂停顿。不会自动修改设备白名单。",fontSize=12.sp)
            Text("本次允许运行此版本。启动后手机读取设备任务状态；设备任务运行不等于程序行为已验收。",fontWeight=FontWeight.SemiBold)
        }},
        confirmButton={TextButton(enabled=s.device!=null&&!s.busy,onClick={upload=null;vm.upload(work)}){Text("确认运行 / 切换")}},
        dismissButton={TextButton(onClick={upload=null}){Text("取消")}}) }
    delete?.let { work -> AlertDialog(onDismissRequest={delete=null},title={Text("删除手机上的这个版本？")},
        text={Text("仅删除「"+work.title+"」v"+work.version+" 的手机记录。不会停止或卸载设备应用，也不影响系统应用。")},
        confirmButton={TextButton(onClick={delete=null;vm.delete(work)}){Text("删除本地版本")}},
        dismissButton={TextButton(onClick={delete=null}){Text("保留")}}) }
}


/** Matches the design: 55dp targets + 4dp top inset, separate from the OS gesture area. */
@Composable
private fun CompactNavigation(tab:Int,onTab:(Int)->Unit) {
    Surface(color=Color.White) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color=Color(0xFFEEEFE9))
            Row(Modifier.fillMaxWidth().padding(start=25.dp,end=25.dp,top=4.dp).selectableGroup()) {
                val names=listOf("创作","应用","设备")
                val icons=listOf(WorkshopIcons.Spark,WorkshopIcons.Grid,WorkshopIcons.Device)
                names.forEachIndexed { i,name ->
                    Column(Modifier.weight(1f).heightIn(min=55.dp)
                        .selectable(selected=tab==i,onClick={onTab(i)},role=Role.Tab)
                        .padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Box(Modifier.size(28.dp).background(if(tab==i) Color(0xFFFFE9DC) else Color.Transparent,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                            Icon(icons[i],null,tint=if(tab==i) Ink else Color(0xFF62665F),modifier=Modifier.size(21.dp))
                        }
                        Text(name,fontSize=10.sp,lineHeight=16.sp,color=if(tab==i) Ink else Color(0xFF62665F),modifier=Modifier.padding(top=2.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ErrorNotice(message:String,onDismiss:()->Unit,modifier:Modifier=Modifier) {
    Surface(color=Color(0xFFFFEEE7),modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(message,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.weight(1f))
            IconButton(onClick=onDismiss,modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Close,"关闭提示")}
        }
    }
}

@Composable
internal fun TaskCard(s:BotState,vm:BotViewModel) {
    Surface(color=Soft,shape=MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Orange)
        Text(s.stage,fontSize=14.sp)
        TextButton(onClick=vm::cancel){Text("取消当前任务")}
    } }
}

@Composable
private fun ConfigDialog(s:BotState,onDismiss:()->Unit,onSave:(String,String,String)->Unit) {
    var base by remember { mutableStateOf(s.baseUrl) }
    var model by remember { mutableStateOf(s.model) }
    var key by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("手机 Agent 设置")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("沿用 PC StepGo 的模型代理地址；模型必须在当前 Key 的授权范围内。",fontSize=13.sp)
        OutlinedTextField(base,{base=it},label={Text("Base URL（包含 /v1）")},singleLine=true)
        OutlinedTextField(model,{model=it},label={Text("模型")},singleLine=true)
        OutlinedTextField(key,{key=it},label={Text(if(s.configured) "新 Key（留空保持原值）" else "API Key")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        Text("不写入 APK、源码或日志。应用卸载后需重新配置。",fontSize=12.sp)
    }},confirmButton={TextButton(onClick={onSave(base,model,key);key=""},enabled=base.isNotBlank()&&model.isNotBlank()&&(s.configured||key.isNotBlank())){Text("保存")}},dismissButton={TextButton(onClick={key="";onDismiss()}){Text("取消")}})
}

@Composable
internal fun SourceDialog(work:Work,onDismiss:()->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.padding(20.dp)) {
                Text(work.title+" · v"+work.version,fontWeight=FontWeight.Bold,fontSize=20.sp)
                Text("Lua 语法检查通过 · "+work.capabilityLabel+" · "+work.lua.lines().size+" 行",fontSize=12.sp,color=Green,modifier=Modifier.padding(top=10.dp))
                Text("SHA-256 "+work.sha256,fontSize=10.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.padding(vertical=8.dp))
                Text("本地生成源码，不是设备画面或运行证据。",fontSize=11.sp,color=Green,modifier=Modifier.padding(bottom=10.dp))
                SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(work.manifest()+"\n\n-- scripts/main.lua\n"+work.lua,fontFamily=FontFamily.Monospace,fontSize=11.sp,lineHeight=17.sp)
                }
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)){Text("返回创作")}
            }
        }
    }
}
