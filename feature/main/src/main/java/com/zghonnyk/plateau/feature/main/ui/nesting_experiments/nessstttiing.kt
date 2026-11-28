package com.zghonnyk.plateau.feature.main.ui.nesting_experiments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zghonnyk.ui.theme.PlateauTheme

@DslMarker
annotation class NestDsl


sealed interface Level

data object Zero : Level

class S<P : Level>(val prev: P) : Level

typealias L0 = Zero
typealias L1 = S<L0>
typealias L2 = S<L1>
typealias L3 = S<L2>
typealias L4 = S<L3>
typealias L5 = S<L4>

private const val BASE_DP = 8

private tailrec fun fibFrom(n: Int, a: Int, b: Int): Int =
    if (n == 0) a else fibFrom(n - 1, b, a + b)

private fun fib(n: Int): Int = fibFrom(n, 0, 1)

private tailrec fun indexOf(level: Level, acc: Int): Int =
    when (level) {
        Zero -> acc
        is S<*> -> indexOf(level.prev, acc + 1)
    }

private fun Level.index(): Int = indexOf(this, 0)

private fun radiusOf(level: Level): Dp =
    when (level) {
        Zero -> 0.dp
        is S<*> -> (BASE_DP * fib(level.index() + 1)).dp
    }

private fun paddingOf(level: Level): Dp =
    when (level) {
        Zero -> 0.dp
        is S<*> -> radiusOf(level) - radiusOf(level.prev)
    }


@NestDsl
class NestScope<L : Level> internal constructor(val level: L) {
    val radius: Dp = radiusOf(level)
    val padding: Dp = paddingOf(level)
    val shape: Shape = RoundedCornerShape(radius)
}

fun <P : Level> NestScope<S<P>>.inner(): NestScope<P> = NestScope(level.prev)

fun Modifier.nestPadding(s: NestScope<*>): Modifier = padding(s.padding)

fun Modifier.nestPaddingHorizontal(s: NestScope<*>): Modifier = padding(horizontal = s.padding)

@Composable
fun <P : Level> NestScope<S<P>>.containerColor(): Color =
    MaterialTheme.colorScheme.let { c ->
        listOf(
            c.tertiaryContainer,
            c.secondaryContainer,
            c.primaryContainer,
            c.surfaceContainerHighest,
            c.surfaceContainerLow,
        )
    }.let { tones -> tones[(level.index() - 1) % tones.size] }

@Composable
fun <P : Level> NestScope<S<P>>.NestContainer(
    modifier: Modifier = Modifier,
    content: @Composable NestScope<P>.() -> Unit,
) = Column(
    modifier
        .background(containerColor(), shape)
        .nestPadding(this),
    Arrangement.spacedBy(padding),
) { inner().content() }

@Composable
fun <P : Level> NestScope<S<P>>.NestRow(
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical,

    content: @Composable NestScope<P>.() -> Unit,
) = Row(
    modifier = modifier
        .background(containerColor(), shape)
        .nestPadding(this),
    horizontalArrangement = Arrangement.spacedBy(padding, alignment),
    verticalAlignment = verticalAlignment,
) {
    inner().content()
}

@Composable
fun <P : Level> NestScope<S<P>>.NestGroupRow(
    alignment: Alignment.Horizontal,
    modifier: Modifier,
    verticalAlignment: Alignment.Vertical,
    content: @Composable RowScope.(NestScope<P>) -> Unit,
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(padding, alignment),
    verticalAlignment = verticalAlignment,
) { content(inner()) }

@Composable
fun <P : Level> NestCard(
    s: NestScope<S<P>>,
    onClick: () -> Unit,
    enabled: Boolean,
    colors: CardColors,
    modifier: Modifier,
    content: @Composable (NestScope<P>) -> Unit,
) = Card(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    shape = s.shape,
    colors = colors,
    content = {
        Column(Modifier.nestPadding(s), Arrangement.spacedBy(s.padding)) {
            content(s.inner())
        }
    },
)


private val ROOT: NestScope<L5> = NestScope(S(S(S(S(S(Zero))))))

@Composable
fun NestRoot(content: @Composable NestScope<L5>.() -> Unit) = ROOT.content()

@Composable
private fun PreviewHost(content: @Composable () -> Unit) =
    PlateauTheme(false) { Box(Modifier.fillMaxWidth()) { content() } }

@Composable
fun <L : Level> NestScreen(
    level: L,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(NestScope<L>) -> Unit,
) {
    val scope = remember(level) { NestScope(level) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(scope.padding),
        verticalArrangement = Arrangement.spacedBy(scope.padding),
    ) {
        content(scope)
    }
}

@Composable
fun <P : Level> NestScope<S<P>>.NestLabel(label: String) = Box(
    Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.primary, shape)
        .nestPadding(this)
) {
    Text(label, color = MaterialTheme.colorScheme.onPrimary)
}

@Composable
fun <P : Level> NestScope<S<P>>.NestButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable NestScope<P>.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier,
    shape = shape,
    contentPadding = PaddingValues(padding),
) { inner().content() }


@Composable
private fun <P : Level> NestScope<S<P>>.Ring(
    content: @Composable NestScope<P>.() -> Unit,
) = NestContainer(Modifier.fillMaxWidth(), content)

// 1. Один контейнер: L5 снаружи, лист внутри
@Preview
@Composable
private fun SingleRingPreview() = PreviewHost {
    NestRoot { Ring { NestLabel("L4: 40dp") } }
}

// 2. Вся цепочка: 64 -> 40 -> 24 -> 16 -> 8, у текста отступ 8
@Preview
@Composable
private fun FullChainPreview() = PreviewHost {
    NestRoot {
        Ring {
            Ring {
                Ring {
                    Ring { NestLabel("L1: 8dp") }
                }
            }
        }
    }
}

// 3. Потолок: раскомментируй, и не скомпилируется (у L0 нет S<P>)
// @Preview
// @Composable
// private fun BelowFloorPreview() = PreviewHost {
//     NestRoot { Ring { Ring { Ring { Ring {
//         Ring { Ring { } }   // unresolved / type mismatch: NestScope<Zero>
//     } } } } }
// }

// 4. Сиблинги: зазор между ними даёт сам контейнер
@Preview
@Composable
private fun SiblingsPreview() = PreviewHost {
    NestRoot {
        Ring {
            Ring { NestLabel("первая") }
            Ring { NestLabel("вторая") }
            Ring { NestLabel("третья") }
        }
    }
}