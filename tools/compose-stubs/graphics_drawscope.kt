package androidx.compose.ui.graphics.drawscope
import androidx.compose.ui.geometry.Size
interface DrawScope { val size: Size }
interface ContentDrawScope : DrawScope { fun drawContent() }
fun DrawScope.clipRect(left: Float = 0f, top: Float = 0f, right: Float = size.width, bottom: Float = size.height, block: DrawScope.() -> Unit) {}
