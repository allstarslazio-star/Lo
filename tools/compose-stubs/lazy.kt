package androidx.compose.foundation.lazy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
interface LazyItemScope
interface LazyListScope {
    fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit)
    fun items(count: Int, key: ((Int) -> Any)? = null, itemContent: @Composable LazyItemScope.(Int) -> Unit)
}
inline fun <T> LazyListScope.items(items: List<T>, noinline key: ((T) -> Any)? = null, crossinline itemContent: @Composable LazyItemScope.(T) -> Unit) {}
@Composable fun LazyColumn(modifier: Modifier = Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, content: LazyListScope.() -> Unit) {}
@Composable fun LazyRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, content: LazyListScope.() -> Unit) {}
