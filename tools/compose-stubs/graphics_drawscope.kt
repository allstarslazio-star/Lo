package androidx.compose.ui.graphics.drawscope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
interface DrawStyle
class Stroke(val width: Float = 0f) : DrawStyle
object Fill : DrawStyle
interface DrawScope {
    val size: Size
    fun drawCircle(color: Color, radius: Float = size.minDimension / 2f, center: Offset = Offset.Zero, alpha: Float = 1f, style: DrawStyle = Fill) {}
    fun drawOval(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill) {}
    fun drawArc(color: Color, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill) {}
    fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float = 0f) {}
    fun drawRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill) {}
    fun drawRoundRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, cornerRadius: CornerRadius = CornerRadius(0f), alpha: Float = 1f, style: DrawStyle = Fill) {}
    fun drawPath(path: Path, color: Color, alpha: Float = 1f, style: DrawStyle = Fill) {}
}
interface ContentDrawScope : DrawScope { fun drawContent() }
fun DrawScope.clipRect(left: Float = 0f, top: Float = 0f, right: Float = size.width, bottom: Float = size.height, block: DrawScope.() -> Unit) {}
