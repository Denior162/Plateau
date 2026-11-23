package com.zghonnyk.plateau.feature.main.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zghonnyk.domain.Prediction
import com.zghonnyk.plateau.feature.main.R
import kotlin.math.roundToInt

fun Prediction.range() = low..high
@Composable
fun Prediction.placeholderText(): String {
    val r = range()
    return stringResource(
        R.string.expected_weight_placeholder,
        r.start.roundToInt(),
        r.endInclusive.roundToInt()
    )
}