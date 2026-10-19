package androidx.compose.ui.semantics
import androidx.compose.ui.Modifier
class Role private constructor() { companion object { val Button: Role = Role(); val Tab: Role = Role(); val Checkbox: Role = Role(); val Switch: Role = Role() } }
class SemanticsPropertyReceiver
var SemanticsPropertyReceiver.contentDescription: String
    get() = ""
    set(v) {}
var SemanticsPropertyReceiver.role: Role
    get() = Role.Button
    set(v) {}
fun Modifier.semantics(mergeDescendants: Boolean = false, properties: SemanticsPropertyReceiver.() -> Unit): Modifier = this
