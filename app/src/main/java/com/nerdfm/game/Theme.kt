package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

object C {
    val bg = Color(Palette.BG)
    val surface = Color(Palette.SURFACE)
    val card = Color(Palette.CARD)
    val line = Color(Palette.LINE)
    val accent = Color(Palette.ACCENT)
    val green = Color(Palette.GREEN)
    val red = Color(Palette.RED)
    val amber = Color(Palette.AMBER)
    val star = Color(Palette.STAR)
    val text = Color(Palette.TEXT)
    val muted = Color(Palette.MUTED)
}

@Composable
fun NerdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = Color.Black, background = C.bg, surface = C.surface,
            onSurface = C.text, onBackground = C.text, secondary = C.green, error = C.red
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(2.dp),
            medium = RoundedCornerShape(3.dp), large = RoundedCornerShape(3.dp),
            extraLarge = RoundedCornerShape(4.dp)
        ),
        content = content
    )
}

@Composable
fun T(
    text: String, size: Int = 13, color: Color = C.text, bold: Boolean = false,
    modifier: Modifier = Modifier, lines: Int = 1
) {
    Text(
        text, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier, maxLines = lines, overflow = TextOverflow.Clip
    )
}

@Composable
fun Btn(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = Accessibility.MIN_TOUCH_DP.dp),
        shape = RoundedCornerShape(2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(3.dp)).background(C.surface).padding(10.dp),
        content = content
    )
}

@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clip(RoundedCornerShape(2.dp)).background(C.card)) {
        options.forEachIndexed { i, o ->
            Box(
                modifier = Modifier
                    .background(if (i == selected) C.accent else Color.Transparent)
                    .heightIn(min = Accessibility.MIN_TOUCH_DP.dp)
                    .describe(o + if (i == selected) ", selected" else "")
                    .clickable(onClickLabel = "Select $o") { onSelect(i) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) { T(o, 12, if (i == selected) Color.Black else C.text, i == selected) }
        }
    }
}

@Composable
fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(6.dp).clip(RoundedCornerShape(1.dp)).background(C.line)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(clampF(frac, 0f, 1f)).background(color))
    }
}

fun attrColor(v: Int): Color = when {
    v >= 16 -> Color(0xFF2EA043)
    v >= 13 -> Color(0xFF7BC96F)
    v >= 10 -> Color(0xFFD7C14A)
    v >= 7 -> Color(0xFFE08A3C)
    else -> Color(0xFFE5534B)
}

fun ovrColor(v: Int): Color = when {
    v >= 75 -> Color(0xFF2EA043)
    v >= 62 -> Color(0xFF7BC96F)
    v >= 50 -> Color(0xFFD7C14A)
    v >= 40 -> Color(0xFFE08A3C)
    else -> Color(0xFFE5534B)
}

@Composable
fun Cell(text: String, w: Int, color: Color = C.text, bold: Boolean = false) {
    T(text, 12, color, bold, Modifier.width(w.dp))
}

// ---- star ratings (0..5, half-star steps)

fun repStars(rep: Int): Float = rep / 20f
fun strengthStars(s: Float): Float = (s - 24f) / 10f

@Composable
fun Stars(value: Float, fontSz: Int = 12, modifier: Modifier = Modifier) {
    val v = Math.round(clampF(value, 0f, 5f) * 2f) / 2f
    Row(modifier) {
        for (i in 0 until 5) {
            val fill = clampF(v - i, 0f, 1f)
            Box {
                Text("\u2605", color = Color(0xFF3A3F3A), fontSize = fontSz.sp, maxLines = 1)
                if (fill > 0f) {
                    Text(
                        "\u2605", color = C.star, fontSize = fontSz.sp, maxLines = 1,
                        modifier = Modifier.drawWithContent {
                            clipRect(right = this.size.width * fill) { this@drawWithContent.drawContent() }
                        }
                    )
                }
            }
        }
    }
}
