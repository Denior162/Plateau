package com.zghonnyk.domain

import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

const val MILLIS_PER_DAY = 86_400_000.0
private const val GRID_DECADES = 3.5
private const val GRID_MIN_K_FACTOR = 0.02
private const val GRID_LOG_BASE = 10.0
private const val MIN_VARIANCE = 1e-12
const val MIN_POINTS_FOR_EXP_FIT = 4
private const val STEPS_PER_DECADE = 57
private const val PERCENT = 100.0

private fun kCandidates(span: Double): Sequence<Double> =
    (GRID_DECADES * STEPS_PER_DECADE).toInt().let { steps ->
        (0..steps).asSequence().map { i ->
            (GRID_MIN_K_FACTOR / span) * GRID_LOG_BASE.pow(GRID_DECADES * i / steps)
        }
    }

sealed interface Tare {
    data object Unknown : Tare
    data class Known(val grams: Double) : Tare
}

private fun Tare.grams() = when (this) {
    Tare.Unknown -> 0.0
    is Tare.Known -> grams
}

fun DryingForecast.Active.moistureAt(wet: Double, tare: Tare): MoistureReading.Exact? =
    moisturePercent(wet, expFit.plateauWeight, tare)?.let {
        MoistureReading.Exact(it, tare)
    }

fun moisturePercent(wet: Double, dry: Double, tare: Tare): Double? =
    (dry - tare.grams()).takeIf { it > 0.0 }
        ?.let { ((wet - dry) / it * PERCENT).coerceAtLeast(0.0) }

private fun isPlausibleTare(grams: Double, minObserved: Double) =
    grams.isFinite() && grams >= 0.0 && grams < minObserved

fun resolveTare(user: Tare, minObserved: Double): Tare = when (user) {
    Tare.Unknown -> Tare.Unknown
    is Tare.Known -> if (isPlausibleTare(user.grams, minObserved)) user else Tare.Unknown
}

sealed interface MoistureReading {
    val tare: Tare

    data class Exact(
        val percent: Double,
        override val tare: Tare
    ) : MoistureReading

    data class Range(
        val low: Double,
        val high: Double,
        override val tare: Tare
    ) : MoistureReading
}

fun MoistureReading.percentInt(): String = when (this) {
    is MoistureReading.Exact -> percent.roundToInt().toString()
    is MoistureReading.Range -> {
        val l = low.roundToInt()
        val h = high.roundToInt()
        if (l == h) "$l" else "$l–$h"
    }
}

private infix fun Instant.daysSince(origin: Instant): Double =
    (this.toEpochMilli() - origin.toEpochMilli()) / MILLIS_PER_DAY

data class ExpFit(
    val plateauWeight: Double,
    val amplitude: Double,
    val k: Double,
    val t0: Instant,
    val rmse: Double,
    val lastDay: Double,
    val isIdentifiable: Boolean,
    val fStat: Double,
    val kSpan: Double
)

fun ExpFit.weightAt(day: Double) = plateauWeight + amplitude * exp(-k * day)
fun ExpFit.rateAt(day: Double) = k * amplitude * exp(-k * day)

private fun fitForK(
    k: Double,
    days: List<Double>,
    weights: List<Double>,
    wMean: Double,
    n: Int,
    t0: Instant,
    span: Double,
    linearRmse: Double,
    scaleAccuracyGrams: Double,
    minFStat: Double
): ExpFit? {
    val decay = days.map { exp(-k * it) }
    val xMean = decay.average()

    val sxx = decay.sumOf { xi ->
        val diff = xi - xMean
        diff * diff
    }
    if (sxx < MIN_VARIANCE) return null

    val amplitude = decay.zip(weights).sumOf { (xi, wi) ->
        (xi - xMean) * (wi - wMean)
    } / sxx
    if (amplitude <= 0.0) return null
    val plateau = wMean - amplitude * xMean
    if (plateau <= 0.0) return null

    val rss = decay.zip(weights).sumOf { (xi, wi) ->
        val diff = plateau + amplitude * xi - wi
        diff * diff
    }
    val rmse = sqrt(rss / maxOf(1, n - 3))
    val rssExp = rmse * rmse * (n - 3)
    val rssLin = linearRmse * linearRmse * (n - 2)
    val fStat = if (n > 3) (n - 3) * (rssLin - rssExp) / maxOf(MIN_VARIANCE, rssExp) else 0.0
    return ExpFit(
        plateau,
        amplitude,
        k,
        t0,
        rmse,
        span,
        isExpIdentifiable(rmse, linearRmse, n, k, span, amplitude, scaleAccuracyGrams, minFStat),
        fStat = fStat,
        kSpan = k * span
    )
}

private fun List<Measurement>.fitExponential(
    minPoints: Int, scaleAccuracyGrams: Double, minFStat: Double
): ExpFit? = takeIf { size >= minPoints }?.let {
    fitPrepared(
        it.first().timestamp, it.map { m -> m.weightGrams }, scaleAccuracyGrams, minFStat
    )
}

private fun List<Measurement>.fitPrepared(
    t0: Instant, weights: List<Double>, scaleAccuracyGrams: Double, minFStat: Double
): ExpFit? = map { it.timestamp daysSince t0 }.let { days ->
    days.last().takeIf { it > 0.0 }?.let { span ->
        val wMean = weights.average()
        val linRmse = linearRmse(days, weights)
        kCandidates(span).mapNotNull { k ->
            fitForK(
                k, days, weights, wMean, size, t0, span, linRmse, scaleAccuracyGrams, minFStat
            )
        }.minByOrNull { it.rmse }
    }
}

data class MeasurementWithRate(
    val id: MeasurementId,
    val timestamp: Instant,
    val weightGrams: Double,
    val deltaGramsPerDay: Double?
)

sealed interface DryingForecast {
    val recommendedInterval: Duration?

    data class InsufficientData(
        override val recommendedInterval: Duration?
    ) : DryingForecast

    sealed interface Active : DryingForecast {
        val currentRate: Double
        val daysUntilPlateau: Int
        val expFit: ExpFit
    }

    data class StillDrying(
        override val recommendedInterval: Duration?,
        override val currentRate: Double,
        override val daysUntilPlateau: Int,
        override val expFit: ExpFit
    ) : Active

    data class NearPlateau(
        override val recommendedInterval: Duration?,
        override val currentRate: Double,
        override val daysUntilPlateau: Int,
        override val expFit: ExpFit
    ) : Active
}

fun ActiveDryingPhase.analyzeDrying(config: DryingConfig): DryingForecast =
    measurements.slopeIntervalOf(config).let { interval ->
        measurements.fitExponential(config.minPoints, config.scaleAccuracyGrams, config.minFStatForExp)
            ?.takeIf { it.isIdentifiable }
            ?.toActive(config, interval)
            ?: DryingForecast.InsufficientData(interval)
    }

private fun List<Measurement>.slopeIntervalOf(config: DryingConfig): Duration? {
    val lastTime = lastOrNull()?.timestamp ?: return null
    val tail = indices.reversed().firstOrNull { i ->
        (size - i) >= config.minPoints && (lastTime daysSince this[i].timestamp) >= config.slopeMinSpanDays
    }?.let { subList(it, size) } ?: return null

    val t0 = tail.first().timestamp
    val days = tail.map { it.timestamp daysSince t0 }
    val weights = tail.map { it.weightGrams }

    val slope = linearSlope(days, weights) ?: return null
    val rate = -slope
    if (rate <= 0.0) return null

    val minSignal = config.scaleAccuracyGrams * config.minSignalMultiplier

    return (minSignal / rate).days
}

private fun ExpFit.toActive(config: DryingConfig, fallback: Duration?): DryingForecast.Active {
    val rate = rateAt(lastDay)
    val interval =
        calculateOptimalInterval(config.scaleAccuracyGrams, config.minSignalMultiplier) ?: fallback
    val days = daysUntilPlateau(config.epsilon, lastDay)
    return if (rate <= config.epsilon) DryingForecast.NearPlateau(interval, rate, days, this)
    else DryingForecast.StillDrying(interval, rate, days, this)
}

private fun calculateRate(previous: Measurement, current: Measurement): MeasurementWithRate {
    val days = current.timestamp daysSince previous.timestamp
    val rate = if (days > 0.0) (previous.weightGrams - current.weightGrams) / days else 0.0
    return MeasurementWithRate(current.id, current.timestamp, current.weightGrams, rate)
}

fun List<Measurement>.withRates(): List<MeasurementWithRate> {
    if (isEmpty()) return emptyList()
    return mapIndexed { index, current ->
        if (index == 0) {
            MeasurementWithRate(current.id, current.timestamp, current.weightGrams, null)
        } else {
            calculateRate(this[index - 1], current)
        }
    }
}

private fun ExpFit.calculateOptimalInterval(
    scaleAccuracyGrams: Double,
    minSignalMultiplier: Double,
    maxRateDropRatio: Double = 0.25
): Duration? {
    val remaining = amplitude * exp(-k * lastDay)
    val minSignal = scaleAccuracyGrams * minSignalMultiplier

    // Если доступной воды меньше, чем шум весов — график вышел на плато
    if (remaining <= minSignal) return null

    // Ограничение СНИЗУ (от 1-й производной):
    // Минимум дней, чтобы весы вообще заметили разницу.
    val daysForMinSignal = -ln(1.0 - minSignal / remaining) / k

    // Ограничение СВЕРХУ (от 2-й производной):
    // Максимум дней до момента, когда текущая скорость (rate) упадёт на maxRateDropRatio.
    // rate(t+dt) = rate(t) * (1 - ratio) => exp(-k*dt) = 1 - ratio
    val daysForDynamicCurve = -ln(1.0 - maxRateDropRatio) / k

    // Идеальный интервал: ждём достаточно долго, чтобы весы "увидели" сигнал (daysForMinSignal),
    // но если форма кривой меняется плавно (daysForDynamicCurve большое),
    // разрешаем растянуть интервал, чтобы не спамить пользователя.
    val days = maxOf(daysForMinSignal, daysForDynamicCurve)

    if (days.isNaN()) return null

    return days.days
}

data class DryingConfig(
    val epsilon: Double = 1.0,
    val scaleAccuracyGrams: Double = 2.0,
    val minSignalMultiplier: Double = 3.0,
    val minPoints: Int = MIN_POINTS_FOR_EXP_FIT,
    val slopeMinSpanDays: Double = 1.0,
    val confidenceFactor: Double = 3.0,
    val sigmaFloor: Double = 5.0,
    val growthUncertaintyPerDay: Double = 2.0,
    val minFStatForExp: Double = 4.0,
    val displayStepGrams: Double = 1.0,
    val confidence: Double = 0.99
)

private fun linearSlope(days: List<Double>, weights: List<Double>): Double? =
    days.average().let { dMean ->
        days.sumOf { (it - dMean) * (it - dMean) }
            .takeIf { it >= MIN_VARIANCE }
            ?.let { sdd ->
                val wMean = weights.average()
                days.zip(weights).sumOf { (d, w) -> (d - dMean) * (w - wMean) } / sdd
            }
    }

private fun linearRmse(days: List<Double>, weights: List<Double>): Double {
    val slope = linearSlope(days, weights) ?: return Double.POSITIVE_INFINITY

    val dMean = days.average()
    val wMean = weights.average()

    val rss = days.zip(weights)
        .sumOf { (d, w) -> (intercept(wMean, slope, dMean) + slope * d - w).let { it * it } }
    return sqrt(rss / maxOf(1, days.size - 2))
}

private fun intercept(wMean: Double, slope: Double, dMean: Double): Double = wMean - slope * dMean
private fun ExpFit.daysUntilPlateau(epsilon: Double, day: Double): Int =
    if (rateAt(day) <= epsilon) 0
    else ceil((ln(k * amplitude / epsilon) / k) - day).toInt()

fun ExpFit.daysUntilPlateauAt(now: Instant, epsilon: Double): Int =
    daysUntilPlateau(epsilon, day = maxOf(now daysSince t0, lastDay))

sealed interface Verdict {
    data object Plausible : Verdict
    data class TooHeavy(val expected: Prediction) : Verdict
    data class TooLight(val expected: Prediction, val lookalike: PlantId?) : Verdict
}

enum class Basis { EXPONENTIAL, LINEAR }
data class Prediction(
    val grams: Double,
    val low: Double,
    val high: Double,
    val basis: Basis,
    val debugMetadata: String = ""
)

private data class ModelBasis(
    val basis: Basis,
    val xs: List<Double>,
    val x0: Double,
    val expected: Double,
    val rmse: Double
)

fun predictWeight(
    history: List<Measurement>,
    at: Instant,
    config: DryingConfig
): Prediction? {
    if (history.size < config.minPoints) return null

    val t0 = history.first().timestamp
    val days = history.map { it.timestamp daysSince t0 }
    val weights = history.map { it.weightGrams }
    val atDay = at daysSince t0

    val model = prepareBasis(history, days, weights, atDay, config) ?: return null
    val xMean = model.xs.average()
    val sxx = model.xs.sumOf { (it - xMean).pow(2) }.takeIf { it >= MIN_VARIANCE } ?: return null

    val df = (if (model.basis == Basis.EXPONENTIAL) history.size - 3 else history.size - 2)
        .takeIf { it >= 1 } ?: return null

    val t = studentT(config.confidence, df) ?: return null

    val quantNoise = config.displayStepGrams / sqrt(12.0)
    val noise = max(model.rmse, quantNoise)
    val leverage = 1.0 / history.size + (model.x0 - xMean).pow(2) / sxx
    val half = t * noise * sqrt(1.0 + leverage)

    val low = (model.expected - half).coerceAtLeast(0.0)
    val high = min(model.expected + half, weights.last() + config.displayStepGrams).coerceAtLeast(low)

    return Prediction(model.expected, low, high, model.basis, "n=${history.size}, noise=%.2f".format(noise))
}

private fun prepareBasis(
    history: List<Measurement>,
    days: List<Double>,
    weights: List<Double>,
    atDay: Double,
    config: DryingConfig
): ModelBasis? =
    history.fitExponential(config.minPoints, config.scaleAccuracyGrams, config.minFStatForExp)
        ?.takeIf { it.isIdentifiable }
        ?.let { fit ->
            ModelBasis(
                basis = Basis.EXPONENTIAL,
                xs = days.map { exp(-fit.k * it) },
                x0 = exp(-fit.k * atDay),
                expected = fit.weightAt(atDay),
                rmse = fit.rmse
            )
        } ?: linearSlope(days, weights)?.let { slope ->
        ModelBasis(
            basis = Basis.LINEAR,
            xs = days,
            x0 = atDay,
            expected = weights.average() + slope * (atDay - days.average()),
            rmse = linearRmse(days, weights)
        )
    }

fun assessMeasurement(
    history: List<Measurement>, candidate: Double, at: Instant, config: DryingConfig
): Verdict = predictWeight(history, at, config)?.let { p ->
    when {
        candidate > p.high -> Verdict.TooHeavy(p)
        candidate < p.low -> Verdict.TooLight(p, null)
        else -> Verdict.Plausible
    }
} ?: Verdict.Plausible

private fun isExpIdentifiable(
    expRmse: Double,
    linearRmse: Double,
    n: Int,
    k: Double,
    span: Double,
    amplitude: Double,
    scaleAccuracyGrams: Double,
    minFStat: Double
): Boolean {
    if (n <= 3 || expRmse >= linearRmse) return false

    val minK = (1.0 / span) * ln(1.0 + scaleAccuracyGrams / maxOf(1.0, amplitude))
    if (k <= minK) return false

    val rssExp = expRmse * expRmse * (n - 3)
    val rssLin = linearRmse * linearRmse * (n - 2)
    val fStat = (n - 3) * (rssLin - rssExp) / maxOf(MIN_VARIANCE, rssExp)

    return fStat >= minFStat
}

private fun centralMass(t: Double, nu: Int): Double {
    val theta = atan(t / sqrt(nu.toDouble()))
    if (nu == 1) return 2.0 * theta / PI
    val c = cos(theta)
    return if (nu % 2 == 0) {
        var coef = 1.0
        var sum = 1.0
        for (j in 1..(nu - 2) / 2) {
            coef *= (2.0 * j - 1) / (2.0 * j)
            sum += coef * c.pow(2 * j)
        }
        sin(theta) * sum
    } else {
        var coef = 1.0
        var bracket = c
        for (j in 1..(nu - 3) / 2) {
            coef *= (2.0 * j) / (2.0 * j + 1)
            bracket += coef * c.pow(2 * j + 1)
        }
        2.0 / PI * (theta + sin(theta) * bracket)
    }
}

private const val MIN_DEGREES_OF_FREEDOM = 1
private const val MIN_CONFIDENCE = 0.0
private const val MAX_CONFIDENCE = 1.0
private const val INITIAL_LOWER_BOUND = 0.0
private const val INITIAL_UPPER_BOUND_GUESS = 1.0
private const val UPPER_BOUND_EXPANSION_FACTOR = 2.0
private const val BISECTION_STEPS = 60
private const val MIDPOINT_DIVISOR = 2.0
private const val TERMINAL_STEPS = 0

private fun studentT(confidence: Double, nu: Int): Double? =
    if (nu >= MIN_DEGREES_OF_FREEDOM && confidence > MIN_CONFIDENCE && confidence < MAX_CONFIDENCE) {
        bisect(
            lo = INITIAL_LOWER_BOUND,
            hi = findUpperLimit(INITIAL_UPPER_BOUND_GUESS, confidence, nu),
            confidence = confidence,
            nu = nu,
            stepsLeft = BISECTION_STEPS
        )
    } else null

private tailrec fun findUpperLimit(hi: Double, confidence: Double, nu: Int): Double =
    if (centralMass(hi, nu) < confidence) {
        findUpperLimit(hi * UPPER_BOUND_EXPANSION_FACTOR, confidence, nu)
    } else hi

private tailrec fun bisect(
    lo: Double,
    hi: Double,
    confidence: Double,
    nu: Int,
    stepsLeft: Int
): Double = if (stepsLeft == TERMINAL_STEPS) {
    (lo + hi) / MIDPOINT_DIVISOR
} else if (centralMass((lo + hi) / MIDPOINT_DIVISOR, nu) < confidence) {
    bisect((lo + hi) / MIDPOINT_DIVISOR, hi, confidence, nu, stepsLeft - 1)
} else {
    bisect(lo, (lo + hi) / MIDPOINT_DIVISOR, confidence, nu, stepsLeft - 1)
}

fun moistureRangeAfterPruning(
    wet: Double,
    dryOld: Double,
    removed: Double,
    tare: Tare
): MoistureReading.Range? =
    moisturePercent(wet, dryOld, tare)?.let { lower ->
        moisturePercent(wet, dryOld - removed.coerceAtLeast(0.0), tare)?.let { upper ->
            MoistureReading.Range(lower, upper, tare)
        }
    }

