package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
annotation class Composable
interface State<out T> { val value: T }
interface MutableState<T> : State<T> { override var value: T }
interface IntState { val intValue: Int }
interface MutableIntState : IntState { override var intValue: Int }
interface FloatState { val floatValue: Float }
interface MutableFloatState : FloatState { override var floatValue: Float }
operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) { this.value = value }
operator fun IntState.getValue(thisObj: Any?, property: KProperty<*>): Int = intValue
operator fun MutableIntState.setValue(thisObj: Any?, property: KProperty<*>, value: Int) { intValue = value }
operator fun FloatState.getValue(thisObj: Any?, property: KProperty<*>): Float = floatValue
operator fun MutableFloatState.setValue(thisObj: Any?, property: KProperty<*>, value: Float) { floatValue = value }
fun <T> mutableStateOf(value: T): MutableState<T> = TODO()
fun mutableIntStateOf(value: Int): MutableIntState = TODO()
fun mutableFloatStateOf(value: Float): MutableFloatState = TODO()
fun <T> mutableStateListOf(vararg elements: T): MutableList<T> = TODO()
@Composable fun <T> remember(calculation: () -> T): T = TODO()
@Composable fun <T> remember(key1: Any?, calculation: () -> T): T = TODO()
@Composable fun <T> remember(key1: Any?, key2: Any?, calculation: () -> T): T = TODO()
@Composable fun <T> remember(vararg keys: Any?, calculation: () -> T): T = TODO()
@Composable fun LaunchedEffect(key1: Any?, block: suspend CoroutineScope.() -> Unit) {}
@Composable fun SideEffect(effect: () -> Unit) {}
@Composable fun rememberCoroutineScope(): CoroutineScope = TODO()
@Composable fun <T> State<T>.asState(): State<T> = this
