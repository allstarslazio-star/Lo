package androidx.compose.ui.platform
import androidx.compose.ui.text.AnnotatedString
interface ClipboardManager { fun setText(text: AnnotatedString) }
class Holder<T> { val current: T get() = TODO() }
val LocalClipboardManager = Holder<ClipboardManager>()
val LocalContext = Holder<android.content.Context>()
