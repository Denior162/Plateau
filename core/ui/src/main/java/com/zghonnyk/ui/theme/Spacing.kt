package com.zghonnyk.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val GOLDEN = 1.618033988749895
@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 40.dp,
    val huge: Dp = 64.dp,
    val massive: Dp = 104.dp,
    val gigantic: Dp = 168.dp
)
private tailrec fun fib8Step(remaining: UInt, current: Int, next: Int): Int =
    if (remaining == 0u) current
    else fib8Step(remaining - 1u, next, current + next)

fun spacingStep(index: UInt): Dp = when (index) {
    0u -> 0.dp
    1u -> 4.dp
    else -> fib8Step(remaining = index - 2u, current = 8, next = 16).dp
}
val LocalSpacing = staticCompositionLocalOf { Spacing() }

val spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current