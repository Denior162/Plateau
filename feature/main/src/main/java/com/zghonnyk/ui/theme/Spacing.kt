package com.zghonnyk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val BASE_DP = 8
private const val STEPS_COUNT = 8

private val FIB_STEPS: List<Dp> = generateSequence(1 to 1) { (a, b) -> b to a + b }
    .map { (a, _) -> (a * BASE_DP).dp }
    .take(STEPS_COUNT)
    .toList()

private const val MIN_NEST_INDEX = 2

@Immutable
class Nest(val index: Int) {
    init {
        require(index >= MIN_NEST_INDEX) { "Index $index is too small for nesting" }
    }

    val outer: Dp = FIB_STEPS[index]
    val inner: Dp = FIB_STEPS[index - 1]
    val padding: Dp = FIB_STEPS[index - 2]

    val outerShape = RoundedCornerShape(outer)
    val innerShape = RoundedCornerShape(inner)
}

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = (BASE_DP / 2).dp,
    val small: Dp = FIB_STEPS[1],
    val medium: Dp = FIB_STEPS[2],
    val large: Dp = FIB_STEPS[3],
    val extraLarge: Dp = FIB_STEPS[4],
    val huge: Dp = FIB_STEPS[5],
    val massive: Dp = FIB_STEPS[6],
    val gigantic: Dp = FIB_STEPS[7],
)

private val DEFAULT_SPACING = Spacing(
    none = 0.dp,
    extraSmall = (BASE_DP / 2).dp,
    small = FIB_STEPS[1],
    medium = FIB_STEPS[2],
    large = FIB_STEPS[3],
    extraLarge = FIB_STEPS[4],
    huge = FIB_STEPS[5],
    massive = FIB_STEPS[6],
    gigantic = FIB_STEPS[7],
)

val LocalSpacing = staticCompositionLocalOf { DEFAULT_SPACING }

private tailrec fun fib8Step(remaining: UInt, current: Int, next: Int): Int =
    if (remaining == 0u) current
    else fib8Step(remaining - 1u, next, current + next)

val spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current