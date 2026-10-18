package androidx.compose.foundation.layout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
interface ColumnScope { fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier; fun Modifier.align(alignment: Alignment.Horizontal): Modifier }
interface RowScope { fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier; fun Modifier.align(alignment: Alignment.Vertical): Modifier }
interface BoxScope { fun Modifier.align(alignment: Alignment): Modifier }
class PaddingValues(val horizontal: Dp = Dp(0f), val vertical: Dp = Dp(0f))
object Arrangement {
    interface Horizontal; interface Vertical; interface HorizontalOrVertical : Horizontal, Vertical
    fun spacedBy(space: Dp): HorizontalOrVertical = TODO()
    val Center: HorizontalOrVertical = TODO(); val Start: Horizontal = TODO(); val End: Horizontal = TODO()
    val SpaceBetween: HorizontalOrVertical = TODO(); val SpaceEvenly: HorizontalOrVertical = TODO(); val Top: Vertical = TODO()
}
@Composable fun Column(modifier: Modifier = Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, horizontalAlignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {}
@Composable fun Row(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, verticalAlignment: Alignment.Vertical = Alignment.Top, content: @Composable RowScope.() -> Unit) {}
@Composable fun Box(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, content: @Composable BoxScope.() -> Unit = {}) {}
@Composable fun Spacer(modifier: Modifier) {}
fun Modifier.padding(all: Dp): Modifier = this
fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier = this
fun Modifier.padding(start: Dp = Dp(0f), top: Dp = Dp(0f), end: Dp = Dp(0f), bottom: Dp = Dp(0f)): Modifier = this
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = this
fun Modifier.width(width: Dp): Modifier = this
fun Modifier.height(height: Dp): Modifier = this
fun Modifier.size(size: Dp): Modifier = this
fun Modifier.size(width: Dp, height: Dp): Modifier = this
fun Modifier.widthIn(min: Dp = Dp(0f), max: Dp = Dp(0f)): Modifier = this
fun Modifier.heightIn(min: Dp = Dp(0f), max: Dp = Dp(0f)): Modifier = this
fun Modifier.safeDrawingPadding(): Modifier = this
