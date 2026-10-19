package androidx.compose.material3
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
class ColorScheme
class Shapes(val extraSmall: Shape? = null, val small: Shape? = null, val medium: Shape? = null, val large: Shape? = null, val extraLarge: Shape? = null)
fun darkColorScheme(primary: Color = Color.Black, onPrimary: Color = Color.Black, background: Color = Color.Black, surface: Color = Color.Black,
    onSurface: Color = Color.Black, onBackground: Color = Color.Black, secondary: Color = Color.Black, error: Color = Color.Black): ColorScheme = TODO()
@Composable fun MaterialTheme(colorScheme: ColorScheme, shapes: Shapes = Shapes(), content: @Composable () -> Unit) {}
@Composable fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Black, fontSize: TextUnit = TextUnit(14f), fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, overflow: TextOverflow = TextOverflow.Clip, maxLines: Int = Int.MAX_VALUE) {}
@Composable fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape? = null, contentPadding: PaddingValues = PaddingValues(), content: @Composable RowScope.() -> Unit) {}
@Composable fun Surface(modifier: Modifier = Modifier, shape: Shape? = null, color: Color = Color.Black, content: @Composable () -> Unit) {}
@Composable fun HorizontalDivider(modifier: Modifier = Modifier, color: Color = Color.Black) {}
@Composable fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {}
@Composable fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {}
@Composable fun TextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: (@Composable () -> Unit)? = null, singleLine: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default) {}
@Composable fun OutlinedTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: (@Composable () -> Unit)? = null, singleLine: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default) {}
@Composable fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {}
@Composable fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {}
@Composable fun CircularProgressIndicator(modifier: Modifier = Modifier) {}
