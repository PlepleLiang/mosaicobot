package dev.mosaicobot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ApplicationTabs(s:BotState,vm:BotViewModel) {
    Row(Modifier.fillMaxWidth().background(Soft,RoundedCornerShape(12.dp)).padding(4.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("我的创作" to s.works.size,"应用中心" to null,"设备应用" to (builtinApps.count { it.origin=="Claw 主屏" }+s.deviceWorks.size)).forEachIndexed { i,(name,count)->
            Row(Modifier.weight(1f).heightIn(min=48.dp).background(if(s.appsGroup==i) Color.White else Color.Transparent,RoundedCornerShape(9.dp))
                .selectable(selected=s.appsGroup==i,onClick={vm.appsGroup(i)},role=Role.Tab),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                Text(name,fontSize=12.sp,lineHeight=18.sp,fontWeight=if(s.appsGroup==i) FontWeight.SemiBold else FontWeight.Normal,color=if(s.appsGroup==i) Ink else AppSecondary)
                count?.let { Text(it.toString(),fontFamily=FontFamily.Monospace,fontSize=10.sp,color=AppSecondary,modifier=Modifier.padding(start=4.dp)) }
            }
        }
    }
}

@Composable
internal fun CenterNotice(message:String,onDismiss:()->Unit) {
    Surface(color=Color(0xFFF1F4EB),shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(message,fontSize=11.sp,lineHeight=18.sp,color=Color(0xFF4E6043),modifier=Modifier.weight(1f))
            IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,"关闭提示",modifier=Modifier.size(16.dp)) }
        }
    }
}
@Composable
private fun LocalBadge() {
    Text("本地模拟 · 仅本机可见",fontSize=10.sp,lineHeight=15.sp,color=Color(0xFF4E6043),modifier=Modifier.background(Color(0xFFF1F4EB),RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=5.dp))
}

@Composable
internal fun AppCenterScreen(s:BotState,vm:BotViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    val listState=rememberLazyListState()
    val detail=s.centerApps.find { it.id==s.centerDetail }
    BackHandler(enabled=detail!=null) { vm.centerDetail(null) }
    if(detail!=null) { CenterDetail(s,vm,detail);return }
    val entries=s.centerApps.filter { (filter=="全部" || if(filter=="我的上传") !it.preset else it.category==filter) && (it.title+it.summary).contains(query.trim(),ignoreCase=true) }
    LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=22.dp)) {
        item {
            Text("应用",fontSize=25.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(14.dp));ApplicationTabs(s,vm);Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                LocalBadge()
                TextButton(onClick={vm.centerUpload(true)},enabled=!s.busy&&s.centerReady&&s.libraryReady) { Icon(Icons.Outlined.FileUpload,null,modifier=Modifier.size(16.dp));Text("上传作品",fontSize=11.sp) }
            }
            Surface(onClick={s.centerApps.firstOrNull { it.artwork=="tank"&&it.preset }?.let { vm.centerDetail(it.id) }},color=Color(0xFF202A26),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min=100.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start=16.dp,top=14.dp,bottom=14.dp)) {
                        Text("别人的灵感，\n你的新起点。",fontSize=18.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFF2F2E7))
                        Text("从一场坦克大战开始 ↗",fontSize=10.sp,lineHeight=16.sp,color=Color(0xFFD0DFBB),modifier=Modifier.padding(top=7.dp))
                    }
                    CenterArtwork("tank",Modifier.width(145.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color=Soft,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,AppLine)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Search,null,tint=AppSecondary,modifier=Modifier.size(17.dp))
                    BasicTextField(value=query,onValueChange={query=it.take(80)},singleLine=true,textStyle=TextStyle(fontSize=12.sp,lineHeight=20.sp,color=Ink),
                        modifier=Modifier.weight(1f).semantics{contentDescription="搜索应用名称或玩法"},decorationBox={field->if(query.isEmpty())Text("搜索一个新玩法",fontSize=12.sp,color=AppSecondary);field()})
                    if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"清除搜索",modifier=Modifier.size(16.dp))}
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=9.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("全部","游戏","工具","趣玩","我的上传").forEach { label->
                    Box(Modifier.weight(1f).heightIn(min=48.dp).background(if(filter==label) Color(0xFFEDF1E5) else Color.Transparent,RoundedCornerShape(9.dp))
                        .selectable(selected=filter==label,onClick={filter=label},role=Role.Tab),contentAlignment=Alignment.Center) {
                        Text(label,fontSize=11.sp,lineHeight=16.sp,color=if(filter==label) Color(0xFF405E2C) else AppSecondary,fontWeight=if(filter==label) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(filter=="我的上传") "我的上传" else "灵感货架",fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text(entries.size.toString()+" 个应用",fontSize=10.sp,color=AppSecondary)
            }
        }
        s.error?.let { item { ErrorNotice(it,vm::clearError);Spacer(Modifier.height(12.dp)) } }
        s.centerNotice?.let { item { CenterNotice(it,vm::clearCenterNotice);Spacer(Modifier.height(12.dp)) } }
        if(s.busy) item { TaskCard(s,vm);Spacer(Modifier.height(12.dp)) }
        if(entries.isEmpty()) item {
            Surface(color=Soft,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(if(!s.centerReady) "正在读取本机目录" else if(query.isNotBlank()) "没找到这个玩法" else "还没有上传作品",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(!s.centerReady) "若读取失败，原文件会保留；可重新打开 App。" else "换个关键词，或从我的创作中选择一个版本。",fontSize=11.sp,lineHeight=18.sp,color=AppSecondary)
                    TextButton(onClick={query="";filter="全部"}){Text("查看全部应用",fontSize=12.sp)}
                }
            }
        }
        items(entries.chunked(2),key={it.first().id}) { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { app->CenterCard(app,app.imported(s.works)!=null,!s.busy&&s.libraryReady&&s.centerReady,{vm.centerDetail(app.id)},{vm.acquireCenter(app)},Modifier.weight(1f)) }
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        item { Text("先带走一个想法，再把它变成你的。\n封面是插画，不是设备实时画面。",fontSize=10.sp,lineHeight=18.sp,color=AppSecondary,modifier=Modifier.padding(vertical=16.dp)) }
    }
}

@Composable
private fun CenterCard(app:CenterApp,imported:Boolean,enabled:Boolean,onDetail:()->Unit,onGet:()->Unit,modifier:Modifier) {
    Surface(modifier=modifier,color=Color.White,shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,AppLine)) {
        Column {
            Column(Modifier.clickable(onClickLabel="查看"+app.title+"详情",role=Role.Button,onClick=onDetail)) {
                CenterArtwork(app.artwork,Modifier.fillMaxWidth())
                Column(Modifier.padding(start=11.dp,end=11.dp,top=11.dp)) {
                    Text(app.category+" / "+if(app.preset) "内置 Lua" else "本机上传",fontSize=8.sp,lineHeight=13.sp,color=AppSecondary)
                    Text(app.title,fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(vertical=4.dp))
                    Text(app.summary,fontSize=10.sp,lineHeight=16.sp,color=AppSecondary,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start=11.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(app.preset) "Mosaico Lab" else "我 · 本机",fontSize=8.sp,lineHeight=13.sp,color=AppSecondary,modifier=Modifier.weight(1f))
                TextButton(onClick=onGet,enabled=enabled,contentPadding=PaddingValues(horizontal=5.dp),modifier=Modifier.defaultMinSize(minWidth=48.dp,minHeight=48.dp)) {
                    Text(if(imported) "已添加" else "获取 +",fontSize=10.sp,color=if(enabled) Color(0xFF405D2E) else AppSecondary)
                }
            }
        }
    }
}

@Composable
private fun CenterDetail(s:BotState,vm:BotViewModel,app:CenterApp) {
    var remove by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(false) }
    val imported=app.imported(s.works)
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={vm.centerDetail(null)},contentPadding=PaddingValues(0.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,null,modifier=Modifier.size(17.dp));Text("应用中心",fontSize=12.sp) }
            Text("本地预览",fontSize=10.sp,color=AppSecondary)
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)) {
            item {
                Box { CenterArtwork(app.artwork,Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)));Text("封面示意 · 非设备实况",fontSize=8.sp,color=Color(0xFFD8E2CE),modifier=Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color(0xE8202A26)).padding(4.dp)) }
                Text("BUILT TO BE REMIXED",fontSize=8.sp,lineHeight=13.sp,fontFamily=FontFamily.Monospace,color=AppSecondary,modifier=Modifier.padding(top=20.dp))
                Text(app.title,fontSize=25.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=6.dp))
                Text(app.summary,fontSize=12.sp,lineHeight=19.sp,color=AppSecondary)
                Text(if(app.preset) "Mosaico Lab · 离线预置" else "我 · 本机上传",fontSize=11.sp,lineHeight=17.sp,modifier=Modifier.padding(top=18.dp))
                Text("v"+app.version+" · "+app.category+" · 无云端同步",fontSize=9.sp,lineHeight=15.sp,color=AppSecondary,modifier=Modifier.padding(top=2.dp,bottom=18.dp))
                HorizontalDivider(color=AppLine)
                Row(Modifier.fillMaxWidth().padding(vertical=13.dp)) {
                    listOf("480 × 480" to "方形屏幕",(if(app.usesNetwork) "触摸 + 联网" else "触摸交互") to (if(app.usesNetwork) "检查域名权限" else "无需外设"),"本地副本" to "可继续创作").forEach {(top,bottom)->
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {Text(top,fontSize=11.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold);Text(bottom,fontSize=9.sp,lineHeight=15.sp,color=AppSecondary,modifier=Modifier.padding(top=4.dp))}
                    }
                }
                HorizontalDivider(color=AppLine)
                Text("怎么玩",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=22.dp,bottom=9.dp))
                Text(app.controls,fontSize=12.sp,lineHeight=21.sp,color=AppSecondary)
                TextButton(onClick={source=true}){Icon(Icons.Outlined.Code,null,modifier=Modifier.size(18.dp));Text("查看 Lua 源码",fontSize=12.sp)}
                Surface(color=Soft,shape=RoundedCornerShape(10.dp)) { Text("目录与上传仅保存在本机。Lua 已通过本地语法检查；这不是安全沙箱，也不代表设备画面或交互已验收。首次运行请先查看源码。",fontSize=10.sp,lineHeight=18.sp,color=AppSecondary,modifier=Modifier.padding(12.dp)) }
                if(!app.preset) TextButton(onClick={remove=true},enabled=!s.busy) {Text("移除这条本机上传",fontSize=11.sp,color=MaterialTheme.colorScheme.error)}
            }
            s.centerNotice?.let {item {Spacer(Modifier.height(12.dp));CenterNotice(it,vm::clearCenterNotice)}}
            s.error?.let {item {ErrorNotice(it,vm::clearError)}}
            if(s.busy)item {TaskCard(s,vm)}
        }
        HorizontalDivider(color=AppLine)
        Button(onClick={vm.acquireCenter(app)},enabled=!s.busy&&s.libraryReady&&s.centerReady,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(top=12.dp).heightIn(min=48.dp)) {
            Icon(if(imported!=null) Icons.Outlined.Check else Icons.Outlined.Add,null,modifier=Modifier.size(18.dp));Text(if(imported!=null) "已添加 · 查看我的创作" else "添加到我的创作",fontSize=12.sp)
        }
        Text("添加不会部署或启动 Mosaico，运行仍需单独确认。",fontSize=9.sp,lineHeight=15.sp,color=AppSecondary,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp,bottom=14.dp))
    }
    if(source) SourceDialog(imported ?: Work(title=app.title,summary=app.summary,lua=app.lua,intent="应用中心源码预览",version=app.version),onDismiss={source=false})
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text("移除这条本机上传？")},text={Text("只从应用中心移除「"+app.title+"」。已获取的创作副本和设备应用不受影响。")},
        confirmButton={TextButton(onClick={remove=false;vm.removeCenter(app)},enabled=!s.busy){Text("确认移除")}},dismissButton={TextButton(onClick={remove=false}){Text("保留")}})
}
