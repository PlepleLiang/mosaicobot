package dev.mosaicobot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val AppLine = Color(0xFFE0E3D9)
internal val AppSecondary = Color(0xFF62665F)

@Composable
internal fun Applications(s: BotState, vm: BotViewModel, onSource: (Work) -> Unit, onRun: (Work) -> Unit, onDelete: (Work) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState) {
    val deviceGroup = s.appsGroup == 2
    var builtin by remember { mutableStateOf<BuiltinApp?>(null) }
    var deleting by remember(s.device?.address) { mutableStateOf<DeviceWork?>(null) }
    // Dimensions follow styles.css: 20px gutter, 25px heading, 56px segments,
    // 44px artwork, 14px card padding, 48px actions. Text follows system font scaling.
    if (s.appsGroup == 1) AppCenterScreen(s,vm) else LazyColumn(Modifier.fillMaxSize(), state=listState, contentPadding=PaddingValues(start=20.dp,end=20.dp,top=22.dp,bottom=20.dp)) {
        item {
            Text("ONE BODY, MORE POSSIBILITIES",fontFamily=FontFamily.Monospace,fontSize=8.sp,lineHeight=12.sp,letterSpacing=1.4.sp,color=AppSecondary)
            Text("应用",fontSize=25.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=8.dp))
            Text("原来的本领，加上你的新想法。",fontSize=11.sp,lineHeight=17.sp,color=AppSecondary)
            Spacer(Modifier.height(23.dp))
            ApplicationTabs(s,vm)
            Spacer(Modifier.height(22.dp))
        }
        // Feedback scrolls with this page rather than consuming a fixed strip above its heading.
        s.error?.let { error -> item { ErrorNotice(error,vm::clearError); Spacer(Modifier.height(12.dp)) } }
        if(s.busy) item { TaskCard(s,vm); Spacer(Modifier.height(12.dp)) }
        s.centerNotice?.let { notice -> item { CenterNotice(notice,vm::clearCenterNotice); Spacer(Modifier.height(12.dp)) } }
        if (deviceGroup) {
            item { Text("原有入口 · 请在 Mosaico 上打开",fontSize=13.sp,fontWeight=FontWeight.SemiBold); Spacer(Modifier.height(12.dp)) }
            items(builtinApps.filter { it.origin=="Claw 主屏" }.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { app ->
                        Surface(onClick={builtin=app},modifier=Modifier.weight(1f),color=Color.White,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,AppLine)) {
                            Column(Modifier.heightIn(min=131.dp).padding(horizontal=8.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                AppArtwork(builtinIcon(app))
                                Text(app.name,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                                Text(app.label,fontSize=10.sp,color=AppSecondary)
                                Text(app.origin,fontSize=9.sp,color=AppSecondary)
                            }
                        }
                    }
                    repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { Text("依据 Claw 源码保留的目录，不代表设备实际安装清单。除 Claw 对话外，手机尚不支持远程打开这些系统应用。",fontSize=10.sp,lineHeight=16.sp,color=AppSecondary,modifier=Modifier.padding(top=8.dp)) }
            item {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Works 作品",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    TextButton(onClick=vm::refreshDeviceWorks,enabled=s.device!=null&&!s.busy&&!s.deviceWorksLoading) { Text(if(s.deviceWorksLoading) "同步中…" else "刷新",fontSize=11.sp) }
                }
                Text("设备上的预装作品与已部署版本。预装不可删除；删除设备版本，手机创作仍保留。",fontSize=10.sp,lineHeight=16.sp,color=AppSecondary)
                Spacer(Modifier.height(12.dp))
                if(s.device==null) Text("设备离线 · 连接后读取实际 Works 列表",fontSize=11.sp,color=AppSecondary)
                s.deviceWorksError?.let { Text(it,fontSize=11.sp,lineHeight=17.sp,color=Color(0xFFAA4936)) }
                if(s.deviceWorksAddress!=null && s.deviceWorks.isEmpty()) Text("设备上暂无 Works 作品",fontSize=11.sp,color=AppSecondary)
            }
            items(s.deviceWorks,key={"device-"+it.id}) { work ->
                DeviceWorkRow(work,s.device!=null&&s.deviceWorksAddress==s.device?.address&&!s.busy&&!s.deviceWorksLoading&&s.deviceWorksError==null&&!s.gamepadOpen) { deleting=work }
            }
        } else {
            item {
                GamepadEntry(vm::openGamepad,enabled=!s.busy)
                Spacer(Modifier.height(12.dp))
                Surface(onClick={vm.appsGroup(1)},color=Color(0xFFF1F4EB),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,AppLine),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        AppArtwork(WorkshopIcons.Plus)
                        Column(Modifier.weight(1f)) { Text("让好想法，彼此接力",fontSize=12.sp,fontWeight=FontWeight.SemiBold); Text("应用中心 · 发现、带走、再创造",fontSize=10.sp,color=AppSecondary,modifier=Modifier.padding(top=4.dp)) }
                        Icon(WorkshopIcons.Arrow,null,modifier=Modifier.size(18.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("保存在这台手机",fontSize=10.sp,color=AppSecondary,modifier=Modifier.weight(1f))
                    TextButton(onClick={vm.centerUpload(true)},enabled=!s.busy&&s.libraryReady&&s.centerReady) { Icon(Icons.Outlined.FileUpload,null,modifier=Modifier.size(16.dp)); Text("上传作品",fontSize=11.sp) }
                }
            }
            if(s.works.isEmpty()) item {
                Surface(color=Soft,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal=20.dp,vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(WorkshopIcons.Spark,null,tint=AppSecondary,modifier=Modifier.size(32.dp))
                        Text("第一个想法，就从这里开始",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                        Text("生成的应用与版本会自动保存在手机上。",fontSize=11.sp,color=AppSecondary)
                        Button(onClick=vm::newCreation,enabled=!s.busy,shape=RoundedCornerShape(12.dp)) { Text("创造第一件作品",fontSize=12.sp) }
                    }
                }
            }
            items(s.works,key={it.packageId}) { work ->
                val runtime=s.runtime?.takeIf { it.id==work.packageId && it.address==s.device?.address }
                val running=runtime?.status=="running"
                Surface(modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,AppLine)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable(onClickLabel="查看源码",role=Role.Button) { onSource(work) },
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            AppArtwork(if(work.title.contains("蛇")||work.intent.contains("游戏")) WorkshopIcons.Game else if(work.title.contains("时")||work.title.contains("日历")) WorkshopIcons.Clock else WorkshopIcons.Grid)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(work.title,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
                                Text("v"+work.version+" · "+libraryWorkStatus(work,s.device,s.runtime),fontSize=10.sp,lineHeight=15.sp,color=AppSecondary)
                            }
                        }
                        Spacer(Modifier.height(13.dp))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={vm.open(work)},enabled=!s.busy,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFD4DACB)),
                                contentPadding=PaddingValues(horizontal=15.dp,vertical=10.dp),modifier=Modifier.heightIn(min=48.dp)) { Text("继续创作",fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold) }
                            Button(onClick={if(running) vm.stopWork(work) else onRun(work)},enabled=!s.busy&&s.device!=null&&runtime?.status !in setOf("queued","stopping"),
                                shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=15.dp,vertical=10.dp),modifier=Modifier.defaultMinSize(minWidth=52.dp).heightIn(min=48.dp)) {
                                Text(if(running) "停止" else "运行",fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick={onDelete(work)},enabled=!s.busy,modifier=Modifier.size(48.dp)) { Icon(Icons.Outlined.DeleteOutline,"删除本地版本",tint=AppSecondary,modifier=Modifier.size(20.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Spacer(Modifier.height(6.dp))
                Surface(onClick={vm.appsGroup(2)},modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,AppLine)) {
                    Row(Modifier.heightIn(min=80.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        AppArtwork(WorkshopIcons.Grid)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text("它原本就有很多本领",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            Text("Camera、Music、IMU… 去看看",fontSize=10.sp,color=AppSecondary)
                        }
                        Icon(WorkshopIcons.Arrow,null,tint=AppSecondary,modifier=Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    deleting?.let { work ->
        val address=s.device?.address
        AlertDialog(onDismissRequest={deleting=null},title={Text((if(work.active) "停止并删除「" else "删除「")+work.name+"」？")},
            text={Text((if(work.active) "将先停止运行，等待退出确认后再删除。\n\n" else "")+"仅删除 Mosaico 上此版本的作品和文件。手机创作、源码、其他版本与预装作品保留。设备删除不可撤销。")},
            confirmButton={TextButton(enabled=address!=null&&!s.busy&&!s.deviceWorksLoading&&work.deletable,onClick={deleting=null;if(address!=null) vm.deleteDeviceWork(work,address)}) { Text(if(work.active) "停止并删除" else "从设备删除",color=Color(0xFFAA4936)) }},
            dismissButton={TextButton(onClick={deleting=null}) { Text("取消") }})
    }
    if(s.centerUploadOpen) CenterUploadDialog(s,vm,onSource)
    builtin?.let { app -> AlertDialog(onDismissRequest={builtin=null},title={Text(app.name+" · "+app.label)},
        text={Text(if(app.name=="CLAW") "与 Mosaico 上的设备 Agent 独立对话，不进入手机创作流程，也不切换设备前台。" else app.origin+"入口已保留。请在 Mosaico 上打开；手机端尚无已验证的远程启动、停止与画面回执接口。系统应用不在这里卸载。")},
        confirmButton={ if(app.name=="CLAW") TextButton(enabled=s.device!=null,onClick={builtin=null;vm.openClaw()}){Text("和设备 Claw 对话")} else TextButton(onClick={builtin=null}){Text("返回应用列表")} },
        dismissButton={ if(app.name=="CLAW") TextButton(onClick={builtin=null}){Text("返回应用列表")} }) }
}

@Composable
internal fun AppArtwork(icon: ImageVector) {
    Box(Modifier.size(44.dp).background(Color(0xFFEDF1E5),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
        Icon(icon,null,tint=Color(0xFF557A41),modifier=Modifier.size(22.dp))
    }
}

private fun builtinIcon(app: BuiltinApp): ImageVector = when(app.name) {
    "Camera" -> Icons.Outlined.PhotoCamera
    "CLAW" -> WorkshopIcons.Device
    "Works" -> WorkshopIcons.Grid
    "Settings" -> Icons.Outlined.Settings
    "IMU" -> Icons.Outlined.ScreenRotation
    "Album" -> Icons.Outlined.PhotoLibrary
    "Music" -> Icons.Outlined.MusicNote
    "Weather" -> Icons.Outlined.WbSunny
    "Interaction" -> Icons.Outlined.TouchApp
    "Partner" -> Icons.Outlined.Face
    else -> WorkshopIcons.Game
}
