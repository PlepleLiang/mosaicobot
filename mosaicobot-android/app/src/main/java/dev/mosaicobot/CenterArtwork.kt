package dev.mosaicobot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/** Cover illustration only, never a live device frame. Same 320x200 geometry as the design. */
@Composable
internal fun CenterArtwork(key:String, modifier:Modifier=Modifier) {
    Canvas(modifier.aspectRatio(1.6f)) {
        scale(size.width/320f,size.height/200f,pivot=Offset.Zero) {
            fun box(x:Float,y:Float,w:Float,h:Float,c:Long) = drawRect(Color(c),Offset(x,y),Size(w,h))
            fun shape(c:Long,vararg points:Pair<Float,Float>) {
                drawPath(Path().apply { points.forEachIndexed { i,p -> if(i==0) moveTo(p.first,p.second) else lineTo(p.first,p.second) };close() },Color(c))
            }
            box(0f,0f,320f,200f,if(key=="pixelpet") 0xFF26232F else if(key=="flight") 0xFF1D2B35 else 0xFF202A26)
            when(key) {
                "tank" -> {
                    listOf(32f to 46f,190f to 48f,32f to 152f,192f to 152f).forEach { (x,y)->box(x,y,70f,20f,0xFF344334) }
                    box(32f,70f,22f,40f,0xFF344334);box(262f,72f,22f,42f,0xFF344334)
                    box(132f,84f,22f,26f,0xFF344334);box(178f,84f,22f,26f,0xFF344334)
                    box(129f,132f,10f,40f,0xFFBDD9A0);box(167f,132f,10f,40f,0xFFBDD9A0);box(143f,138f,20f,27f,0xFFBDD9A0);box(150f,113f,6f,31f,0xFFBDD9A0)
                    box(218f,32f,7f,30f,0xFFEEA277);box(246f,32f,7f,30f,0xFFEEA277);box(229f,36f,13f,23f,0xFFEEA277);box(233f,51f,5f,25f,0xFFEEA277)
                    box(151f,84f,3f,8f,0xFFE7E9B5);box(155f,177f,14f,12f,0xFFF4D793)
                }
                "flight" -> {
                    repeat(15) {i->box(((i*73)%290+12).toFloat(),((i*47)%175+12).toFloat(),2f,3f,0xFF3B5361)}
                    shape(0xFFACD3ED,160f to 116f,136f to 158f,153f to 158f,153f to 173f,167f to 173f,167f to 158f,184f to 158f)
                    listOf(85f to 42f,230f to 67f,168f to 31f).forEach{(x,y)->shape(0xFFDE916E,x to y,(x+15) to (y+15),x to (y+23),(x-15) to (y+15))}
                    box(157f,92f,3f,9f,0xFFB4E4CF);box(163f,77f,3f,9f,0xFFB4E4CF);box(159f,176f,3f,11f,0xFFF5B57D)
                }
                "racer" -> {
                    box(80f,0f,160f,200f,0xFF293036);box(79f,0f,3f,200f,0xFF8D7760);box(239f,0f,3f,200f,0xFF8D7760)
                    repeat(6){i->box(133f,i*34f,2f,17f,0xFF72736B);box(187f,i*34f,2f,17f,0xFF72736B)}
                    box(146f,129f,28f,49f,0xFFEEAC76);box(150f,143f,20f,13f,0xFF323A3E)
                    box(204f,36f,26f,45f,0xFFA8BECB);box(208f,50f,18f,12f,0xFF323A3E)
                    box(38f,10f,17f,38f,0xFF556657);box(268f,69f,17f,35f,0xFF556657)
                }
                "pixelpet" -> {
                    box(110f,76f,103f,63f,0xFFD8BCE9);box(121f,51f,21f,25f,0xFFD8BCE9);box(180f,51f,21f,25f,0xFFD8BCE9);box(122f,139f,79f,19f,0xFFD8BCE9)
                    box(133f,98f,8f,11f,0xFF333142);box(182f,98f,8f,11f,0xFF333142);box(151f,125f,21f,6f,0xFF333142)
                    box(128f,119f,13f,5f,0xFFE89596);box(183f,119f,13f,5f,0xFFE89596)
                    shape(0xFFEEA28C,240f to 43f,246f to 37f,252f to 43f,258f to 37f,264f to 43f,264f to 55f,252f to 67f,240f to 55f)
                }
                "quiettime" -> {
                    drawCircle(Color(0xFF354036),68f,Offset(160f,101f),style=Stroke(5f))
                    drawArc(Color(0xFFBECDAA),-90f,140f,false,Offset(92f,33f),Size(136f,136f),style=Stroke(5f))
                    // Minimal clock hands: vector illustration, not a fabricated clock reading.
                    drawLine(Color(0xFFE7EADC),Offset(160f,101f),Offset(160f,66f),4f)
                    drawLine(Color(0xFFE7EADC),Offset(160f,101f),Offset(189f,116f),4f)
                }
                else -> {
                    repeat(11){i->box(80f+i*16f,28f,1f,144f,0xFF344138);box(80f,28f+i*14.4f,160f,1f,0xFF344138)}
                    shape(0xFFE6B87F,112f to 60f,144f to 60f,144f to 76f,176f to 76f,176f to 60f,208f to 60f,208f to 108f,192f to 108f,192f to 124f,176f to 124f,176f to 140f,144f to 140f,144f to 124f,128f to 124f,128f to 108f,112f to 108f)
                    box(114f,62f,12f,12f,0xFFF8DFB1)
                }
            }
        }
    }
}
