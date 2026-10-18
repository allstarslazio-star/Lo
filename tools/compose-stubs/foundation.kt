package androidx.compose.foundation
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
class ScrollState
@Composable fun rememberScrollState(initial: Int = 0): ScrollState = TODO()
fun Modifier.background(color: Color, shape: Shape = TODO()): Modifier = this
fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = this
fun Modifier.verticalScroll(state: ScrollState, enabled: Boolean = true): Modifier = this
fun Modifier.horizontalScroll(state: ScrollState, enabled: Boolean = true): Modifier = this
fun Modifier.border(width: androidx.compose.ui.unit.Dp, color: Color, shape: Shape = TODO()): Modifier = this
@Composable fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {}
fun Modifier.clickable(enabled: Boolean = true, onClickLabel: String? = null, role: androidx.compose.ui.semantics.Role? = null, onClick: () -> Unit): Modifier = this
