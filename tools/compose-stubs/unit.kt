package androidx.compose.ui.unit
class Dp(val value: Float) { operator fun plus(o: Dp) = Dp(value + o.value); operator fun times(f: Float) = Dp(value * f) }
val Int.dp: Dp get() = Dp(this.toFloat())
val Double.dp: Dp get() = Dp(this.toFloat())
val Float.dp: Dp get() = Dp(this)
class TextUnit(val value: Float)
val Int.sp: TextUnit get() = TextUnit(this.toFloat())
val Double.sp: TextUnit get() = TextUnit(this.toFloat())
val Float.sp: TextUnit get() = TextUnit(this)
