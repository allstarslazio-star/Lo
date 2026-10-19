package androidx.compose.ui.graphics
class Color(val v: Long) {
    constructor(v: Int) : this(v.toLong())
    fun copy(alpha: Float = 1f, red: Float = 0f, green: Float = 0f, blue: Float = 0f): Color = this
    companion object { val Black = Color(0L); val White = Color(1L); val Transparent = Color(2L); val Red = Color(3L); val Gray = Color(4L) }
}
interface Shape
val RectangleShape: Shape = TODO()
class Path { fun moveTo(x: Float, y: Float) {}; fun lineTo(x: Float, y: Float) {}; fun quadraticBezierTo(a: Float, b: Float, c: Float, d: Float) {}; fun cubicTo(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {}; fun close() {} }
