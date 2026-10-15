package dev.mosaicobot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Same 24-unit, 1.65-stroke paths as docs/mosaicobot/index.html. */
internal object WorkshopIcons {
    private fun outline(name:String,data:String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(pathData=PathParser().parsePathString(data).toNodes(),fill=null,
            stroke=SolidColor(Color.Black),strokeLineWidth=1.65f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()

    val Spark = outline("Spark","m12 3 2.6 6.4L21 12l-6.4 2.6L12 21l-2.6-6.4L3 12l6.4-2.6Z")
    val Plus = outline("Plus","M12 5v14M5 12h14")
    val Arrow = outline("Arrow","m7 17 10-10M7 7h10v10")
    val Send = outline("Send","M12 19V5m-6 6 6-6 6 6")
    val Device = outline("Device","M9 4h6a5 5 0 0 1 5 5v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9a5 5 0 0 1 5-5Z M9 10v4m6-4v4")
    val Grid = outline("Grid","M5.5 4h3A1.5 1.5 0 0 1 10 5.5v3A1.5 1.5 0 0 1 8.5 10h-3A1.5 1.5 0 0 1 4 8.5v-3A1.5 1.5 0 0 1 5.5 4Z M15.5 4h3A1.5 1.5 0 0 1 20 5.5v3A1.5 1.5 0 0 1 18.5 10h-3A1.5 1.5 0 0 1 14 8.5v-3A1.5 1.5 0 0 1 15.5 4Z M5.5 14h3A1.5 1.5 0 0 1 10 15.5v3A1.5 1.5 0 0 1 8.5 20h-3A1.5 1.5 0 0 1 4 18.5v-3A1.5 1.5 0 0 1 5.5 14Z M15.5 14h3A1.5 1.5 0 0 1 20 15.5v3A1.5 1.5 0 0 1 18.5 20h-3A1.5 1.5 0 0 1 14 18.5v-3A1.5 1.5 0 0 1 15.5 14Z")
    val Clock = outline("Clock","M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0 M12 6v6l4 2")
    val Game = outline("Game","M7 7h10c2 0 3 2 3.5 5l.5 5c0 2-2 3-3.5 1L15 16H9l-2.5 2C5 20 3 19 3 17l.5-5C4 9 5 7 7 7Z M8 10v5m-2.5-2.5h5m5-1h.1m2 2h.1")
}
