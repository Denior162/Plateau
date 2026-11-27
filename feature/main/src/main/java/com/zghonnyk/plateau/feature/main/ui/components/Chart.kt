package com.zghonnyk.plateau.feature.main.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.zghonnyk.domain.ExpFit
import com.zghonnyk.domain.MILLIS_PER_DAY
import com.zghonnyk.domain.weightAt

private const val CHART_PADDING = 32f
private const val ANIMATION_DURATION_MS = 1000
private const val CURVE_STEPS = 100
private const val FORECAST_EXTENSION_DAYS = 3.0
private const val PLATEAU_LINE_ALPHA = 0.5f
private const val FILL_GRADIENT_ALPHA = 0.25f
private const val DASH_LENGTH = 10f
private const val POINT_OUTER_RADIUS_DP = 5.5
private const val POINT_INNER_RADIUS_DP = 3.5
private const val LINE_STROKE_WIDTH_DP = 2.5
private const val PLATEAU_STROKE_WIDTH_DP = 1.5

data class ChartPoint(
    val timestamp: Long, val grams: Double
)

@Composable
fun DryingChart(
    modifier: Modifier = Modifier, points: List<ChartPoint>, expFit: ExpFit?
) {
    if (points.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val validFit = expFit?.takeIf { it.isIdentifiable }
    val minTime = points.minOf { it.timestamp }
    val maxRealTime = points.maxOf { it.timestamp }

    val maxTime = if (validFit != null && validFit.lastDay > 0) {
        maxOf(
            maxRealTime,
            validFit.t0.toEpochMilli() + (validFit.lastDay + FORECAST_EXTENSION_DAYS).toLong() * MILLIS_PER_DAY.toLong()
        )
    } else {
        if (maxRealTime == minTime) maxRealTime + 1L else maxRealTime
    }

    val minWeight = minOf(
        points.minOf { it.grams },
        validFit?.plateauWeight ?: Double.MAX_VALUE
    )
    val maxWeight = points.maxOf { it.grams }.let { if (it == minWeight) it + 1.0 else it }

    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MS)
    )

    LaunchedEffect(points, validFit) {
        animationPlayed = true
    }

    val curvePath = remember { Path() }
    val fillPath = remember { Path() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = CHART_PADDING

        val usableWidth = width - (padding * 2)
        val usableHeight = height - (padding * 2)

        val plateauStroke = PLATEAU_STROKE_WIDTH_DP.dp.toPx()
        val lineStroke = LINE_STROKE_WIDTH_DP.dp.toPx()
        val outerRadius = POINT_OUTER_RADIUS_DP.dp.toPx()
        val innerRadius = POINT_INNER_RADIUS_DP.dp.toPx()

        curvePath.rewind()
        fillPath.rewind()

        if (validFit != null) {
            val plateauY = mapY(
                validFit.plateauWeight, minWeight, maxWeight, usableHeight, animProgress,
                height = height
            )
            drawLine(
                color = tertiaryColor.copy(alpha = PLATEAU_LINE_ALPHA),
                start = Offset(padding, plateauY),
                end = Offset(width - padding, plateauY),
                strokeWidth = plateauStroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH, DASH_LENGTH), 0f)
            )
        }

        if (validFit != null) {
            val t0Ms = validFit.t0.toEpochMilli()
            val totalDays = (maxTime - t0Ms).toDouble() / MILLIS_PER_DAY

            for (i in 0..CURVE_STEPS) {
                val day = (i.toDouble() / CURVE_STEPS) * totalDays
                val timeMs = t0Ms + (day * MILLIS_PER_DAY).toLong()
                val weight = validFit.weightAt(day)

                val x = mapX(timeMs, minTime, maxTime, usableWidth)
                val y = mapY(weight, minWeight, maxWeight, usableHeight, animProgress, height)

                if (i == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, height - padding)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            val lastX = mapX(t0Ms + (totalDays * MILLIS_PER_DAY).toLong(), minTime, maxTime, usableWidth)
            fillPath.lineTo(lastX, height - padding)
            fillPath.close()
        } else {

            points.forEachIndexed { index, point ->
                val x = mapX(point.timestamp, minTime, maxTime, usableWidth)
                val y = mapY(
                    point.grams, minWeight, maxWeight, usableHeight, animProgress,
                    height = height
                )

                if (index == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, height - padding)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = FILL_GRADIENT_ALPHA),
                    primaryColor.copy(alpha = 0.0f)
                ),
                startY = padding,
                endY = height - padding
            )
        )

        drawPath(
            path = curvePath,
            color = primaryColor,
            style = Stroke(width = lineStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        points.forEach { point ->
            val x = mapX(point.timestamp, minTime, maxTime, usableWidth)
            val y = mapY(point.grams, minWeight, maxWeight, usableHeight, animProgress, height)

            drawCircle(color = surfaceVariantColor, radius = outerRadius, center = Offset(x, y))
            drawCircle(color = primaryColor, radius = innerRadius, center = Offset(x, y))
        }
    }
}

private fun mapX(
    time: Long, minTime: Long, maxTime: Long, usableWidth: Float
): Float {
    val fraction = (time - minTime).toFloat() / (maxTime - minTime).coerceAtLeast(1L).toFloat()
    return CHART_PADDING + (fraction * usableWidth)
}

private fun mapY(
    weight: Double,
    minWeight: Double,
    maxWeight: Double,
    usableHeight: Float,
    animProgress: Float,
    height: Float,
): Float {
    val fraction =
        (weight - minWeight).toFloat() / (maxWeight - minWeight).coerceAtLeast(1.0).toFloat()
    return height - CHART_PADDING - (fraction * usableHeight * animProgress)
}