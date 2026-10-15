package dev.mosaicobot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Muted = Color(0xFF62665F)
private val Line = Color(0xFFE3E4DD)

@Composable
internal fun ColumnScope.Creation(s: BotState, vm: BotViewModel, onSource:(Work)->Unit,onUpload:(Work)->Unit,onConfigure:()->Unit) {
    val scroll = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // The first Back while editing dismisses the IME, never the creation session.
    BackHandler(enabled=keyboardVisible) { keyboard?.hide(); focus.clearFocus() }
    LaunchedEffect(s.messages.size,s.busy,s.selected,s.error) {
        val conversationItems = (if(s.messages.isEmpty()) 1 else s.messages.size) + (if(s.busy) 1 else 0)
        // Reveal a new error in the conversation; it must not permanently consume fixed space.
        val target = if(s.error!=null || s.selected!=null) conversationItems else conversationItems-1
        if(s.messages.isNotEmpty() || s.error!=null) scroll.animateScrollToItem(target.coerceAtLeast(0))
    }
    LazyColumn(state=scroll,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=21.dp,end=21.dp,top=0.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(s.messages.isEmpty()) item {
            Column(Modifier.padding(top=8.dp)) {
                Text("MAKE SOMETHING REAL",fontFamily=FontFamily.Monospace,fontSize=9.sp,lineHeight=14.sp,letterSpacing=1.7.sp,color=Muted)
                Text("今天，让它\n变成什么？",fontSize=28.sp,lineHeight=40.sp,letterSpacing=(-0.8).sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=8.dp))
                Text("一个小游戏，一只计时钟。\n让手机 Agent 为身体创造新的本领。",color=Muted,fontSize=12.sp,lineHeight=22.sp)
                Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    listOf("生成贪食蛇" to "让方屏变成掌上游戏机", "做一个 25 分钟的专注时钟" to "把时间，放到眼前").forEach { (prompt,description) ->
                        val game = prompt.contains("蛇")
                        OutlinedCard(onClick={vm.prompt(prompt)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,Line),colors=CardDefaults.outlinedCardColors(containerColor=Color.White)) {
                            Row(Modifier.heightIn(min=62.dp).padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(36.dp).background(if(game) Color(0xFFFFF0E6) else Color(0xFFF2EFDF),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center) {
                                    Icon(if(game) WorkshopIcons.Game else WorkshopIcons.Clock,null,tint=if(game) Color(0xFFBD592B) else Color(0xFF8C7843),modifier=Modifier.size(22.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(prompt,fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold)
                                    Text(description,fontSize=10.sp,lineHeight=16.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
                                }
                                Icon(WorkshopIcons.Arrow,null,tint=Muted,modifier=Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        items(s.messages) { message ->
            if(message.role=="user") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                Surface(color=Soft,shape=RoundedCornerShape(14.dp),modifier=Modifier.widthIn(max=300.dp)) { Text(message.text,Modifier.padding(12.dp),fontSize=14.sp) }
            } else Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(WorkshopIcons.Spark,null,tint=Orange,modifier=Modifier.size(14.dp))
                    Text("手机 Agent",color=Orange,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                }
                Text(message.text,fontSize=14.sp,lineHeight=22.sp)
            }
        }
        if(s.busy) item { TaskCard(s,vm) }
        s.error?.let { error -> item(key="creation-error") { ErrorNotice(error,vm::clearError) } }
        s.selected?.let { work -> item(key="creation-result") { CreationResult(s,work,vm,onSource,onUpload) } }
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(color=Color(0xFFF6F7F3),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Color(0xFFDCE0D3)),
            modifier=Modifier.padding(horizontal=15.dp).padding(top=4.dp,bottom=if(keyboardVisible) 8.dp else 0.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp)) {
                Text(if(s.selected==null) "你想让 Mosaico 做什么？" else "接下来，让它变得更好",fontSize=10.sp,lineHeight=16.sp,color=Muted,fontWeight=FontWeight.SemiBold)
                BasicTextField(value=s.prompt,onValueChange=vm::prompt,enabled=!s.busy,maxLines=3,
                    textStyle=TextStyle(color=Ink,fontSize=14.sp,lineHeight=22.sp),
                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).padding(top=8.dp,bottom=4.dp).semantics { contentDescription="创作意图" },
                    decorationBox={ inner -> Box { if(s.prompt.isEmpty()) Text(if(s.selected==null) "说个想法，让它发生…" else "继续修改这件作品…",fontSize=14.sp,lineHeight=22.sp,color=Muted); inner() } })
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("• 手机 Agent",fontSize=10.sp,lineHeight=16.sp,color=Green,modifier=Modifier.weight(1f))
                    FilledIconButton(onClick={keyboard?.hide();focus.clearFocus();vm.generate()},enabled=!s.busy&&s.prompt.isNotBlank()&&s.configured,
                        colors=IconButtonDefaults.filledIconButtonColors(containerColor=Ink,contentColor=Color.White),
                        shape=RoundedCornerShape(13.dp),modifier=Modifier.size(48.dp)) {
                        Icon(WorkshopIcons.Send,"发送创作意图",modifier=Modifier.size(22.dp))
                    }
                }
                if(!s.configured) TextButton(onClick=onConfigure){Text("先配置手机 Agent")}
                if(s.debugProxyPort!=null) Text("开发模式 · 模型请求经 USB / PC 转发",fontSize=10.sp,lineHeight=14.sp,color=Orange)
            }
        }
        if(!keyboardVisible) Text("你负责想象，Mosaico 负责发生。",Modifier.padding(top=7.dp,bottom=8.dp),fontSize=9.sp,lineHeight=14.sp,color=Muted)
    }
}

@Composable
private fun CreationResult(s:BotState,work:Work,vm:BotViewModel,onSource:(Work)->Unit,onUpload:(Work)->Unit) {
    val runtime = s.runtime?.takeIf { it.id==work.packageId && it.address==s.device?.address }
    val running = runtime?.status=="running"
    val transition = runtime?.status in setOf("queued","stopping")
    val label = runtime?.label ?: if(work.uploadedTo==null) "已保存 · 等待部署" else "文件已传输 · 运行待确认"
    val suggestions = if(work.intent.contains("蛇")) listOf("速度慢一点","把蛇改成橙色") else listOf("字号大一点","换成深色主题")
    Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFFAFBF7),border=BorderStroke(1.dp,Line)) {
        Column {
            Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Icon(if(running) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked,null,tint=Green,modifier=Modifier.size(16.dp))
                Text(label,color=Green,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                Text(if(runtime!=null) "设备状态" else "本地记录",fontSize=10.sp,lineHeight=14.sp,color=Muted)
            }
            HorizontalDivider(color=Line)
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Surface(color=Ink,shape=RoundedCornerShape(12.dp),modifier=Modifier.size(76.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(if(work.intent.contains("蛇")) WorkshopIcons.Game else if(work.intent.contains("钟")) WorkshopIcons.Clock else Icons.Outlined.Code,null,tint=Color(0xFFC0E8B1),modifier=Modifier.size(28.dp))
                            Text("LUA 应用",color=Color(0xFFCED6CA),fontSize=9.sp,lineHeight=12.sp,modifier=Modifier.padding(top=5.dp))
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(work.title,fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold)
                        Text("在 Mosaico 屏幕上体验",fontSize=12.sp,lineHeight=18.sp,color=Muted)
                        Text("v"+work.version+" · "+work.capabilityLabel,fontSize=10.sp,lineHeight=14.sp,color=Muted)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={if(running) vm.stopWork(work) else onUpload(work)},enabled=!s.busy&&s.device!=null&&!transition,
                        shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(if(running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null,modifier=Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                        Text(if(running) "停止运行" else if(transition) "等待设备" else if(work.uploadedTo==s.device?.address) "在设备上运行" else "部署并运行",fontSize=12.sp)
                    }
                    OutlinedButton(onClick={onSource(work)},shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(Icons.Outlined.Code,null,modifier=Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("作品详情",fontSize=12.sp)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion -> OutlinedButton(onClick={vm.prompt(suggestion)},enabled=!s.busy,shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=10.dp)) { Text(suggestion,fontSize=11.sp) } }
                }
                if(s.device==null) TextButton(onClick={vm.tab(2)}){Text("连接 Mosaico 后运行")}
                s.runtimeError?.let { Text(it,fontSize=11.sp,color=Muted) }
                if(runtime?.status=="failed") Text("设备任务已失败。请查看作品源码；修复后会保存为新版本。",fontSize=11.sp,color=Color(0xFFAD3D28))
            }
            HorizontalDivider(color=Line)
            Text(if(runtime!=null) "Mosaico · " + java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date(runtime.checkedAt)) + " · 作品已自动保存"
                else "作品已自动保存 · Lua 校验通过 · 图标不是设备画面",modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp),fontSize=10.sp,lineHeight=14.sp,color=Muted)
        }
    }
}
