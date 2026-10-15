package dev.mosaicobot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val UploadPaper = Color(0xFFFFFFFC)
private val UploadOutline = Color(0xFF92988D)

/** Local-only form. Header and consent/actions stay outside the scrollable fields. */
@Composable
internal fun CenterUploadDialog(s:BotState,vm:BotViewModel,onSource:(Work)->Unit) {
    val close={vm.centerUpload(false)}
    if(s.works.isEmpty()) {
        UploadPanel(s.busy,close,footer={
            Button(onClick={close();vm.appsGroup(1)},enabled=!s.busy,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("浏览预置应用",fontSize=12.sp) }
            TextButton(onClick={close();vm.newCreation()},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) { Text("去创作第一个作品",fontSize=12.sp) }
        }) {
            Icon(WorkshopIcons.Grid,null,tint=Green,modifier=Modifier.padding(top=24.dp).size(32.dp))
            Text("先有作品，再分享灵感",fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=16.dp))
            Text("还没有本地作品。去创作一个新应用，或获取预置应用后体验上传。",fontSize=12.sp,lineHeight=21.sp,color=AppSecondary,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
        }
        return
    }
    var packageId by rememberSaveable { mutableStateOf(s.works.first().packageId) }
    val work=s.works.find { it.packageId==packageId } ?: s.works.first()
    val origin=s.centerApps.find { it.id==work.centerOrigin }
    var title by rememberSaveable(work.packageId) { mutableStateOf(work.title.take(24)) }
    var summary by rememberSaveable(work.packageId) { mutableStateOf(work.summary.take(80)) }
    var category by rememberSaveable(work.packageId) { mutableStateOf(origin?.category ?: "趣玩") }
    var consent by rememberSaveable(work.packageId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val nameTooLong=title.trim().toByteArray(Charsets.UTF_8).size>48
    val ready=s.libraryReady&&s.centerReady&&!s.busy
    val canUpload=ready&&consent&&title.isNotBlank()&&!nameTooLong&&summary.isNotBlank()
    val focus=LocalFocusManager.current
    UploadPanel(s.busy,close,footer={
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(8.dp))
            .toggleable(value=consent,enabled=!s.busy,role=Role.Checkbox,onValueChange={consent=it}),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Checkbox(checked=consent,onCheckedChange=null,enabled=!s.busy,colors=CheckboxDefaults.colors(checkedColor=Green,uncheckedColor=AppSecondary),modifier=Modifier.size(20.dp))
            Text("我有权分享，且已检查源码不含密钥、个人信息等私密内容。",fontSize=11.sp,lineHeight=18.sp,color=Ink,modifier=Modifier.weight(1f))
        }
        s.error?.let { Text(it,fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=8.dp)) }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=close,enabled=!s.busy,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,AppLine),modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("取消",fontSize=12.sp) }
            Button(onClick={focus.clearFocus();vm.publishCenter(work,title,summary,category,consent)},enabled=canUpload,
                shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=Color.White,disabledContainerColor=AppLine,disabledContentColor=AppSecondary),
                modifier=Modifier.weight(2f).heightIn(min=48.dp)) {
                if(s.busy) CircularProgressIndicator(modifier=Modifier.size(16.dp),strokeWidth=2.dp,color=AppSecondary)
                else Icon(Icons.Outlined.FileUpload,null,modifier=Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp));Text(if(s.busy) "正在保存…" else "确认上传 · 仅本机",fontSize=12.sp,lineHeight=18.sp)
            }
        }
    }) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("选择作品版本",fontSize=11.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={focus.clearFocus();onSource(work)},enabled=!s.busy,contentPadding=PaddingValues(horizontal=4.dp)) {
                Icon(Icons.Outlined.Code,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("查看源码",fontSize=11.sp)
            }
        }
        Box {
            Surface(onClick={focus.clearFocus();menu=true},enabled=!s.busy,color=Soft,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,AppLine),
                modifier=Modifier.fillMaxWidth().semantics { stateDescription=if(menu) "版本列表已展开" else "选择作品版本，已收起" }) {
                Row(Modifier.heightIn(min=64.dp).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(origin!=null) CenterArtwork(origin.artwork,Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)))
                    else Box(Modifier.size(40.dp).background(Ink,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) { Icon(WorkshopIcons.Grid,null,tint=Color.White,modifier=Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(work.title+" · v"+work.version,fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(work.capabilityLabel+" · 版本快照",fontSize=10.sp,lineHeight=16.sp,color=AppSecondary)
                    }
                    Icon(Icons.Outlined.ExpandMore,null,tint=AppSecondary,modifier=Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded=menu,onDismissRequest={menu=false},containerColor=UploadPaper,modifier=Modifier.heightIn(max=280.dp)) {
                s.works.forEach { w->DropdownMenuItem(text={Column {Text(w.title,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("v"+w.version+" · "+w.capabilityLabel,fontSize=10.sp,color=AppSecondary)}},
                    enabled=!s.busy,onClick={packageId=w.packageId;menu=false;vm.clearError()},
                    trailingIcon={if(w.packageId==work.packageId) Icon(Icons.Outlined.Check,null,tint=Green,modifier=Modifier.size(18.dp))}) }
            }
        }
        Spacer(Modifier.height(16.dp))
        UploadField("应用名称",title,{title=it.take(24);vm.clearError()},""+title.length+" / 24",enabled=!s.busy,
            errorText=if(nameTooLong) "名称超过 48 字节，请缩短后上传。" else null,
            hint="最多 24 字符 / 48 字节，约 16 个汉字",onNext={focus.moveFocus(FocusDirection.Down)})
        Spacer(Modifier.height(16.dp))
        UploadField("一句话介绍",summary,{summary=it.take(80);vm.clearError()},""+summary.length+" / 80",enabled=!s.busy,
            multiline=true,onNext={focus.clearFocus()})
        Spacer(Modifier.height(16.dp))
        Text("应用分类",fontSize=11.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top=6.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppCenter.categories.forEach { name->
                val selected=category==name
                Row(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(10.dp))
                    .background(if(selected) Color(0xFFEDF4ED) else Color.White)
                    .border(1.dp,if(selected) Green else AppLine,RoundedCornerShape(10.dp))
                    .selectable(selected=selected,enabled=!s.busy,role=Role.RadioButton,onClick={category=name;vm.clearError()}),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                    if(selected) {Icon(Icons.Outlined.Check,null,tint=Green,modifier=Modifier.size(14.dp));Spacer(Modifier.width(4.dp))}
                    Text(name,fontSize=12.sp,lineHeight=18.sp,color=if(selected) Green else Ink,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Text("保存独立版本快照，后续修改不覆盖。\n不附带对话、设备地址或模型配置。",fontSize=10.sp,lineHeight=17.sp,color=AppSecondary,modifier=Modifier.padding(top=12.dp))
    }
}

@Composable
private fun UploadPanel(busy:Boolean,onDismiss:()->Unit,footer:@Composable ColumnScope.()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnBackPress=!busy,dismissOnClickOutside=!busy)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            // Full-window dialog: handle taps on the surrounding scrim without consuming field taps.
            Box(Modifier.matchParentSize().pointerInput(busy) { detectTapGestures { if(!busy) onDismiss() } })
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal=16.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                Surface(color=UploadPaper,contentColor=Ink,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,AppLine),
                    modifier=Modifier.widthIn(max=440.dp).fillMaxWidth().heightIn(max=720.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=16.dp,bottom=12.dp),verticalAlignment=Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("APP CENTER / LOCAL",fontFamily=FontFamily.Monospace,fontSize=8.sp,lineHeight=12.sp,letterSpacing=1.4.sp,color=AppSecondary)
                                Text("上传到应用中心",fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp,bottom=4.dp))
                                Text("只在这台手机上架，不会公开到网络。",fontSize=11.sp,lineHeight=18.sp,color=AppSecondary)
                            }
                            IconButton(onClick=onDismiss,enabled=!busy) {Icon(Icons.Outlined.Close,"关闭上传窗口",tint=AppSecondary,modifier=Modifier.size(18.dp))}
                        }
                        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=16.dp),content=content)
                        HorizontalDivider(color=AppLine)
                        Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=16.dp),content=footer)
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadField(label:String,value:String,onChange:(String)->Unit,count:String,enabled:Boolean,multiline:Boolean=false,
    errorText:String?=null,hint:String?=null,onNext:()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline=if(errorText!=null) MaterialTheme.colorScheme.error else if(focused) Green else UploadOutline
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=11.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
            Text(count,fontFamily=FontFamily.Monospace,fontSize=10.sp,lineHeight=16.sp,color=AppSecondary)
        }
        BasicTextField(value=value,onValueChange=onChange,enabled=enabled,singleLine=!multiline,minLines=if(multiline) 2 else 1,maxLines=if(multiline) 4 else 1,
            textStyle=TextStyle(color=Ink,fontSize=13.sp,lineHeight=20.sp),cursorBrush=SolidColor(Green),interactionSource=interaction,
            keyboardOptions=KeyboardOptions(imeAction=if(multiline) ImeAction.Done else ImeAction.Next),
            keyboardActions=KeyboardActions(onNext={onNext()},onDone={onNext()}),
            modifier=Modifier.fillMaxWidth().semantics {contentDescription=label;if(errorText!=null) error(errorText)},
            decorationBox={field->Box(Modifier.fillMaxWidth().heightIn(min=if(multiline) 72.dp else 48.dp)
                .background(Color.White,RoundedCornerShape(10.dp)).border(if(focused) 2.dp else 1.dp,outline,RoundedCornerShape(10.dp)).padding(horizontal=12.dp,vertical=13.dp),
                contentAlignment=if(multiline) Alignment.TopStart else Alignment.CenterStart) {
                if(value.isEmpty()) Text(if(multiline) "说说它能做什么、怎么玩" else "给作品起个名字",fontSize=13.sp,lineHeight=20.sp,color=AppSecondary)
                field()
            }})
        (errorText ?: hint)?.let { Text(it,fontSize=10.sp,lineHeight=16.sp,color=if(errorText!=null) MaterialTheme.colorScheme.error else AppSecondary) }
    }
}
