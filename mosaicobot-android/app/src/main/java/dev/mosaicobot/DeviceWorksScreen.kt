package dev.mosaicobot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeviceWorkRow(work:DeviceWork,enabled:Boolean,onDelete:()->Unit) {
    Surface(color=Color.White,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,AppLine),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AppArtwork(if(work.builtin) WorkshopIcons.Game else WorkshopIcons.Grid)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(work.name,fontSize=13.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold)
                Text((work.version?.let { "v$it · " } ?: "")+(if(work.builtin) "预装" else "已部署"),fontSize=10.sp,color=AppSecondary)
                Text(work.label,fontSize=10.sp,color=if(work.active) Green else AppSecondary)
            }
            if(work.deletable) IconButton(onClick=onDelete,enabled=enabled,modifier=Modifier.size(48.dp)) {
                Icon(Icons.Outlined.DeleteOutline,"从设备删除 ${work.name}",tint=Color(0xFFAA4936),modifier=Modifier.size(20.dp))
            } else Text(if(work.builtin) "预装\n不可删除" else "不可删除",fontSize=10.sp,lineHeight=16.sp,color=AppSecondary)
        }
    }
    Spacer(Modifier.height(8.dp))
}
