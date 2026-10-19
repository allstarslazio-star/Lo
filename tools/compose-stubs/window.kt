package androidx.compose.ui.window
import androidx.compose.runtime.Composable
class DialogProperties(val usePlatformDefaultWidth: Boolean = true, val dismissOnBackPress: Boolean = true, val dismissOnClickOutside: Boolean = true)
@Composable fun Dialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {}
