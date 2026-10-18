package androidx.compose.ui.draw
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
fun Modifier.clip(shape: Shape): Modifier = this
fun Modifier.drawWithContent(onDraw: ContentDrawScope.() -> Unit): Modifier = this
