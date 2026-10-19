package androidx.compose.ui.geometry
class Size(val width: Float, val height: Float) {
    val minDimension: Float get() = minOf(width, height)
    operator fun times(f: Float) = Size(width * f, height * f)
    operator fun div(f: Float) = Size(width / f, height / f)
}
class Offset(val x: Float, val y: Float) {
    operator fun plus(o: Offset) = Offset(x + o.x, y + o.y)
    operator fun minus(o: Offset) = Offset(x - o.x, y - o.y)
    operator fun times(f: Float) = Offset(x * f, y * f)
    companion object { val Zero = Offset(0f, 0f) }
}
class CornerRadius(val x: Float, val y: Float = x)
