package com.zghonnyk.domain

import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
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
private const val STEPS_PER_DECADE = 57
private const val PERCENT = 100.0

// Число параметров моделей: прямая (наклон, сдвиг), экспонента (плато, амплитуда, k),
// прямая с изломом (сдвиг, два наклона, точка разрыва)
private const val LINEAR_PARAMS = 2
private const val EXP_PARAMS = 3
private const val HINGE_PARAMS = 4

// Минимум точек: число параметров плюс одна степень свободы
const val MIN_POINTS_FOR_EXP_FIT = EXP_PARAMS + 1
private const val MIN_POINTS_FOR_SLOPE = LINEAR_PARAMS + 1
private const val INTERPOLATION_WINDOW = 3                  // точка и два соседа

private const val WEIGHINGS_PER_DIFFERENCE = 2.0            // шум разности двух взвешиваний = σ·√2
private const val UNIFORM_VARIANCE_DIVISOR = 12.0           // дисперсия равномерного квантования h²/12
private const val QUADRATIC_TERM_DIVISOR = 2.0              // 2! из ряда exp(-u) = 1 - u + u²/2 - ...
private const val OFF_DIAGONAL_MULTIPLICITY = 2.0           // G_ij и G_ji
private const val MIN_DF_FINITE_VARIANCE = 3                // Var(t_ν) = ν/(ν-2) конечна только при ν > 2

private fun expDf(n: Int) = n - EXP_PARAMS
private fun linearDf(n: Int) = n - LINEAR_PARAMS

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
    is MoistureReading.Range -> low.roundToInt().let { l ->
        high.roundToInt().let { h -> if (l == h) "$l" else "$l–$h" }
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

// ---------------------------------------------------------------------------
// Регрессия на экспоненциальный спад при фиксированном k
// ---------------------------------------------------------------------------

private data class DecayRegression(val plateau: Double, val amplitude: Double, val rss: Double)

private fun regressOnDecay(
    k: Double, days: List<Double>, weights: List<Double>, wMean: Double
): DecayRegression? =
    days.map { exp(-k * it) }.let { decay ->
        val xMean = decay.average()
        val sxx = decay.sumOf { (it - xMean).pow(2) }
        val amplitude = decay.zip(weights).sumOf { (x, w) -> (x - xMean) * (w - wMean) } / sxx
        val plateau = wMean - amplitude * xMean
        if (sxx < MIN_VARIANCE || amplitude <= 0.0 || plateau <= 0.0) null
        else DecayRegression(
            plateau,
            amplitude,
            decay.zip(weights).sumOf { (x, w) -> (plateau + amplitude * x - w).pow(2) }
        )
    }

// F-статистика экспоненты против прямой (на один параметр больше)
private fun fStatistic(expRmse: Double, linearRmse: Double, n: Int): Double =
    if (n > EXP_PARAMS)
        expDf(n) * (linearRmse.pow(2) * linearDf(n) - expRmse.pow(2) * expDf(n)) /
                maxOf(MIN_VARIANCE, expRmse.pow(2) * expDf(n))
    else 0.0

// T² ~ F(1, df), поэтому критическое F равно квадрату t-квантиля
private fun fCritical(confidence: Double, df: Int): Double? = studentT(confidence, df)?.pow(2)

private fun isExpIdentifiable(
    expRmse: Double,
    linearRmse: Double,
    n: Int,
    k: Double,
    span: Double,
    amplitude: Double,
    sigma: Double,
    fCrit: Double
): Boolean =
    n > EXP_PARAMS &&
            expRmse < linearRmse &&
            k > ln(1.0 + sigma / amplitude) / span &&
            fStatistic(expRmse, linearRmse, n) >= fCrit

private fun fitForK(
    k: Double,
    days: List<Double>,
    weights: List<Double>,
    wMean: Double,
    n: Int,
    t0: Instant,
    span: Double,
    linearRmse: Double,
    sigma: Double,
    fCrit: Double
): ExpFit? =
    regressOnDecay(k, days, weights, wMean)?.let { fit ->
        sqrt(fit.rss / maxOf(MIN_DEGREES_OF_FREEDOM, expDf(n))).let { rmse ->
            ExpFit(
                plateauWeight = fit.plateau,
                amplitude = fit.amplitude,
                k = k,
                t0 = t0,
                rmse = rmse,
                lastDay = span,
                isIdentifiable = isExpIdentifiable(rmse, linearRmse, n, k, span, fit.amplitude, sigma, fCrit),
                fStat = fStatistic(rmse, linearRmse, n),
                kSpan = k * span
            )
        }
    }

private fun List<Measurement>.fitExponential(sigma: Double, confidence: Double): ExpFit? =
    takeIf { size >= MIN_POINTS_FOR_EXP_FIT }?.let { ms ->
        fCritical(confidence, expDf(ms.size))?.let { fCrit ->
            ms.fitPrepared(ms.first().timestamp, ms.map { it.weightGrams }, sigma, fCrit)
        }
    }

private fun List<Measurement>.fitPrepared(
    t0: Instant, weights: List<Double>, sigma: Double, fCrit: Double
): ExpFit? =
    map { it.timestamp daysSince t0 }.let { days ->
        days.last().takeIf { it > 0.0 }?.let { span ->
            val wMean = weights.average()
            val linRmse = linearRmse(days, weights)
            kCandidates(span).mapNotNull { k ->
                fitForK(k, days, weights, wMean, size, t0, span, linRmse, sigma, fCrit)
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

    // Линейная стадия: кривая ещё не загнулась, плато предсказать нельзя, но скорость известна
    data class ConstantRate(
        override val recommendedInterval: Duration?,
        val rateGramsPerDay: Double
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

// ---------------------------------------------------------------------------
// Шум весов: непараметрическая оценка по невязкам линейной интерполяции
// (Gasser–Sroka–Jennen-Steinmetz), с эффективными степенями свободы (Саттертуэйт)
// ---------------------------------------------------------------------------

data class Noise(val sigma: Double, val df: Int)

private data class Triplet(val left: Double, val right: Double, val residual: Double) {
    val normSq: Double = 1.0 + left * left + right * right
}

private data class ResidualStats(val sumSquares: Double, val count: Int, val gramSquares: Double) {
    operator fun plus(o: ResidualStats) =
        ResidualStats(sumSquares + o.sumSquares, count + o.count, gramSquares + o.gramSquares)
}

private val NO_RESIDUALS = ResidualStats(0.0, 0, 0.0)

// Невязка точки относительно линейной интерполяции по двум соседям.
// Если время не строго возрастает, фаза в оценку шума не входит.
private fun List<Measurement>.triplets(): List<Triplet> =
    firstOrNull()?.timestamp
        ?.let { t0 -> map { (it.timestamp daysSince t0) to it.weightGrams } }
        ?.takeIf { pts -> pts.zipWithNext().all { (p, q) -> q.first > p.first } }
        ?.windowed(INTERPOLATION_WINDOW)
        ?.map { (p0, p1, p2) ->
            ((p1.first - p0.first) / (p2.first - p0.first)).let { right ->
                Triplet(
                    left = 1.0 - right,
                    right = right,
                    residual = p1.second - ((1.0 - right) * p0.second + right * p2.second)
                )
            }
        }
        .orEmpty()

// tr(G²): диагональ + соседние тройки (лаг 1) + через одну (лаг 2)
private fun List<Triplet>.stats(): ResidualStats =
    ResidualStats(
        sumSquares = sumOf { it.residual.pow(2) / it.normSq },
        count = size,
        gramSquares = size + OFF_DIAGONAL_MULTIPLICITY * (
                zipWithNext { p, q -> (-q.left - p.right).pow(2) / (p.normSq * q.normSq) }.sum() +
                        zip(drop(2)) { p, q -> (p.right * q.left).pow(2) / (p.normSq * q.normSq) }.sum()
                )
    )

private fun ResidualStats.sigma(displayStepGrams: Double): Double? =
    takeIf { count > 0 }
        ?.let { maxOf(sqrt(sumSquares / count), displayStepGrams / sqrt(UNIFORM_VARIANCE_DIVISOR)) }

private fun ResidualStats.noise(displayStepGrams: Double): Noise? =
    sigma(displayStepGrams)
        ?.let { sigma -> Noise(sigma, (count.toDouble().pow(2) / gramSquares).toInt()) }
        ?.takeIf { it.df >= MIN_DF_FINITE_VARIANCE }

// Шум весов это свойство весов, а не горшка: считаем по всей истории растения
fun History.noise(config: DryingConfig): Noise? =
    flatMap { it.phases }
        .fold(NO_RESIDUALS) { acc, phase -> acc + phase.measurements.triplets().stats() }
        .noise(config.displayStepGrams)

// Разность двух взвешиваний имеет шум σ·√2; порог детекции при заданном confidence
private fun Noise.minSignal(confidence: Double): Double? =
    studentT(confidence, df)?.let { it * sqrt(WEIGHINGS_PER_DIFFERENCE) * sigma }

// ---------------------------------------------------------------------------
// Режим сушки: прямая с изломом, выбор по BIC
// ---------------------------------------------------------------------------

private fun bic(rss: Double, n: Int, params: Int): Double =
    n * ln(maxOf(rss, MIN_VARIANCE) / n) + params * ln(n.toDouble())

private fun lineRss(days: List<Double>, weights: List<Double>): Double =
    linearRmse(days, weights).pow(2) * linearDf(days.size)

// Непрерывная прямая с изломом в days[breakIndex]: w = a + b1·t + c·max(0, t - t_break).
// МНК по двум центрированным регрессорам в замкнутом виде.
private fun hingeRss(days: List<Double>, weights: List<Double>, breakIndex: Int): Double? =
    days.map { maxOf(0.0, it - days[breakIndex]) }.let { hinge ->
        val dMean = days.average()
        val hMean = hinge.average()
        val wMean = weights.average()
        val s11 = days.sumOf { (it - dMean).pow(2) }
        val s22 = hinge.sumOf { (it - hMean).pow(2) }
        val s12 = days.zip(hinge).sumOf { (d, h) -> (d - dMean) * (h - hMean) }
        val s1y = days.zip(weights).sumOf { (d, w) -> (d - dMean) * (w - wMean) }
        val s2y = hinge.zip(weights).sumOf { (h, w) -> (h - hMean) * (w - wMean) }
        val syy = weights.sumOf { (it - wMean).pow(2) }
        (s11 * s22 - s12 * s12).takeIf { it >= MIN_VARIANCE }?.let { det ->
            syy - ((s1y * s22 - s2y * s12) * s1y + (s2y * s11 - s1y * s12) * s2y) / det
        }
    }

// Индекс начала текущего режима: 0, если излом не нужен.
// Каждая сторона излома должна определять наклон (не меньше LINEAR_PARAMS + 1 точек).
// Штраф ln K учитывает перебор K кандидатов на точку разрыва.
private fun regimeStart(days: List<Double>, weights: List<Double>): Int =
    (LINEAR_PARAMS until days.size - LINEAR_PARAMS).let { candidates ->
        candidates
            .mapNotNull { i -> hingeRss(days, weights, i)?.let { i to it } }
            .minByOrNull { (_, rss) -> rss }
            ?.takeIf { (_, rss) ->
                bic(rss, days.size, HINGE_PARAMS) + ln(candidates.count().toDouble()) <
                        bic(lineRss(days, weights), days.size, LINEAR_PARAMS)
            }
            ?.first
            ?: 0
    }

private fun List<Measurement>.currentRegime(): List<Measurement> =
    firstOrNull()?.timestamp
        ?.let { t0 -> drop(regimeStart(map { it.timestamp daysSince t0 }, map { it.weightGrams })) }
        .orEmpty()

private fun List<Double>.sxx(): Double = average().let { m -> sumOf { (it - m).pow(2) } }

// Скорость высыхания значима, если rate >= t·σ/√Sxx
private fun List<Measurement>.significantRate(sigma: Double, t: Double): Double? =
    first().timestamp.let { t0 ->
        map { it.timestamp daysSince t0 }.let { days ->
            linearSlope(days, map { it.weightGrams })
                ?.let { -it }
                ?.takeIf { rate -> rate > 0.0 && rate >= t * sigma / sqrt(days.sxx()) }
        }
    }

private fun List<Measurement>.currentRate(noise: Noise, confidence: Double): Double? =
    studentT(confidence, noise.df)?.let { t ->
        currentRegime()
            .takeIf { it.size >= MIN_POINTS_FOR_SLOPE }
            ?.significantRate(noise.sigma, t)
    }

fun ActiveDryingPhase.analyzeDrying(config: DryingConfig, noise: Noise?): DryingForecast =
    noise?.let { n ->
        n.minSignal(config.confidence)?.let { minSignal ->
            measurements.currentRate(n, config.confidence).let { rate ->
                (rate?.let { (minSignal / it).days }).let { interval ->
                    measurements.fitExponential(n.sigma, config.confidence)
                        ?.takeIf { it.isIdentifiable }
                        ?.toActive(config.epsilon, minSignal, interval)
                        ?: rate?.let { DryingForecast.ConstantRate(interval, it) }
                        ?: DryingForecast.InsufficientData(null)
                }
            }
        }
    } ?: DryingForecast.InsufficientData(null)

private fun ExpFit.toActive(epsilon: Double, minSignal: Double, fallback: Duration?): DryingForecast.Active =
    rateAt(lastDay).let { rate ->
        (optimalInterval(amplitude * exp(-k * lastDay), k, minSignal) ?: fallback).let { interval ->
            daysUntilPlateau(epsilon, lastDay).let { days ->
                if (rate <= epsilon) DryingForecast.NearPlateau(interval, rate, days, this)
                else DryingForecast.StillDrying(interval, rate, days, this)
            }
        }
    }

private fun calculateRate(previous: Measurement, current: Measurement): MeasurementWithRate =
    (current.timestamp daysSince previous.timestamp).let { days ->
        MeasurementWithRate(
            current.id,
            current.timestamp,
            current.weightGrams,
            if (days > 0.0) (previous.weightGrams - current.weightGrams) / days else 0.0
        )
    }

fun List<Measurement>.withRates(): List<MeasurementWithRate> =
    mapIndexed { index, current ->
        if (index == 0) MeasurementWithRate(current.id, current.timestamp, current.weightGrams, null)
        else calculateRate(this[index - 1], current)
    }

// Нижняя граница: за интервал вес меняется на minSignal (первая производная).
// Верхняя граница: за интервал отклонение кривой от касательной достигает minSignal (вторая производная):
// R·(k·dt - 1 + exp(-k·dt)) ≈ R·(k·dt)²/2 = minSignal.
// Если остаток воды не больше minSignal, кривая неотличима от асимптоты.
private fun optimalInterval(remaining: Double, k: Double, minSignal: Double): Duration? =
    remaining.takeIf { it > minSignal }
        ?.let { r ->
            maxOf(
                -ln(1.0 - minSignal / r) / k,
                sqrt(QUADRATIC_TERM_DIVISOR * minSignal / r) / k
            )
        }
        ?.takeUnless { it.isNaN() }
        ?.days

data class DryingConfig(
    val epsilon: Double = 1.0,
    val confidence: Double = 0.99,
    val displayStepGrams: Double = 1.0,
    // В Analysis.kt не используются; удалить, если не нужны в других файлах
    val confidenceFactor: Double = 3.0,
    val sigmaFloor: Double = 5.0,
    val growthUncertaintyPerDay: Double = 2.0
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

private fun linearRmse(days: List<Double>, weights: List<Double>): Double =
    linearSlope(days, weights)?.let { slope ->
        val dMean = days.average()
        val wMean = weights.average()
        val rss = days.zip(weights)
            .sumOf { (d, w) -> (intercept(wMean, slope, dMean) + slope * d - w).pow(2) }
        sqrt(rss / maxOf(MIN_DEGREES_OF_FREEDOM, linearDf(days.size)))
    } ?: Double.POSITIVE_INFINITY

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
    val debugMetadata: String
)

private data class ModelBasis(
    val basis: Basis,
    val xs: List<Double>,
    val x0: Double,
    val expected: Double,
    val rmse: Double
)

private fun Basis.parameters(): Int = when (this) {
    Basis.EXPONENTIAL -> EXP_PARAMS
    Basis.LINEAR -> LINEAR_PARAMS
}

private fun prepareBasis(
    history: List<Measurement>, at: Instant, sigma: Double, confidence: Double
): ModelBasis? =
    history.first().timestamp.let { t0 ->
        history.map { it.timestamp daysSince t0 }.let { days ->
            history.map { it.weightGrams }.let { weights ->
                (at daysSince t0).let { atDay ->
                    history.fitExponential(sigma, confidence)
                        ?.takeIf { it.isIdentifiable }
                        ?.let { fit ->
                            ModelBasis(
                                basis = Basis.EXPONENTIAL,
                                xs = days.map { exp(-fit.k * it) },
                                x0 = exp(-fit.k * atDay),
                                expected = fit.weightAt(atDay),
                                rmse = fit.rmse
                            )
                        }
                        ?: linearSlope(days, weights)?.let { slope ->
                            ModelBasis(
                                basis = Basis.LINEAR,
                                xs = days,
                                x0 = atDay,
                                expected = weights.average() + slope * (atDay - days.average()),
                                rmse = linearRmse(days, weights)
                            )
                        }
                }
            }
        }
    }

private fun ModelBasis.halfWidth(n: Int, noise: Double, confidence: Double): Double? =
    xs.average().let { xMean ->
        xs.sumOf { (it - xMean).pow(2) }.takeIf { it >= MIN_VARIANCE }?.let { sxx ->
            studentT(confidence, n - basis.parameters())?.let { t ->
                t * noise * sqrt(1.0 + 1.0 / n + (x0 - xMean).pow(2) / sxx)
            }
        }
    }

private fun ModelBasis.toPrediction(
    half: Double, history: List<Measurement>, displayStepGrams: Double, noise: Double
): Prediction =
    (expected - half).coerceAtLeast(0.0).let { low ->
        Prediction(
            grams = expected,
            low = low,
            high = min(expected + half, history.last().weightGrams + displayStepGrams).coerceAtLeast(low),
            basis = basis,
            debugMetadata = "n=${history.size}, noise=%.2f".format(noise)
        )
    }

// σ нужен здесь только для проверки идентифицируемости, поэтому берём оценку по самой истории
// без ограничения на степени свободы (иначе проверка отключилась бы при n < 8)
fun predictWeight(
    history: List<Measurement>,
    at: Instant,
    config: DryingConfig
): Prediction? =
    history.takeIf { it.size >= MIN_POINTS_FOR_EXP_FIT }?.let { h ->
        h.triplets().stats().sigma(config.displayStepGrams)?.let { sigma ->
            prepareBasis(h, at, sigma, config.confidence)?.let { model ->
                maxOf(model.rmse, config.displayStepGrams / sqrt(UNIFORM_VARIANCE_DIVISOR)).let { noise ->
                    model.halfWidth(h.size, noise, config.confidence)?.let { half ->
                        model.toPrediction(half, h, config.displayStepGrams, noise)
                    }
                }
            }
        }
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

// ---------------------------------------------------------------------------
// Интервал взвешивания после полива по данным предыдущих фаз той же эпохи
// ---------------------------------------------------------------------------

private data class DecaySeries(
    val days: List<Double>,
    val weights: List<Double>,
    val startedBy: PhaseStart
) {
    private val wMean = weights.average()
    fun regress(k: Double): DecayRegression? = regressOnDecay(k, days, weights, wMean)
}

private data class PooledDecay(val k: Double, val wateredAmplitude: Double)

private fun AnalyzedPhase.decaySeries(): DecaySeries =
    phase.measurements.let { ms ->
        ms.first().timestamp.let { t0 ->
            DecaySeries(ms.map { it.timestamp daysSince t0 }, ms.map { it.weightGrams }, phase.startedBy)
        }
    }

// Общий k для всех фаз: профильный МНК (у каждой фазы свои плато и амплитуда).
// Шум весов одинаков во всех фазах, поэтому сумма RSS без весов даёт оценку максимального правдоподобия.
private fun List<DecaySeries>.pooledRss(k: Double): Double? =
    fold<DecaySeries, Double?>(0.0) { acc, s -> acc?.let { total -> s.regress(k)?.let { total + it.rss } } }

// Амплитуда на первом взвешивании после полива: только фазы, начатые поливом
private fun List<DecaySeries>.wateredAmplitude(k: Double): Double? =
    filter { it.startedBy is PhaseStart.Watering }
        .mapNotNull { it.regress(k)?.amplitude }
        .takeIf { it.isNotEmpty() }
        ?.average()

private fun List<DecaySeries>.pooledDecay(): PooledDecay? =
    takeIf { it.isNotEmpty() }?.let { series ->
        kCandidates(series.maxOf { it.days.last() })
            .mapNotNull { k -> series.pooledRss(k)?.let { rss -> k to rss } }
            .minByOrNull { (_, rss) -> rss }
            ?.first
            ?.let { k -> series.wateredAmplitude(k)?.let { PooledDecay(k, it) } }
    }

private fun List<Measurement>.elapsedDays(): Double =
    firstOrNull()?.let { first -> last().timestamp daysSince first.timestamp } ?: 0.0

private fun AnalyzedPhase.needsPriorInterval(): Boolean =
    phase.startedBy is PhaseStart.Watering &&
            forecast is DryingForecast.InsufficientData &&
            forecast.recommendedInterval == null

private fun List<AnalyzedPhase>.currentEpoch(): List<AnalyzedPhase> =
    subList(indexOfLast { it.phase.startedBy is PhaseStart.Repotting }.coerceAtLeast(0), size)

// Сухой вес по последней идентифицируемой фазе; обрезки после неё уменьшают его на removedGrams
// (если вес обрезанного неизвестен, сухой вес неизвестен)
private fun List<AnalyzedPhase>.dryWeightBeforeCurrent(): Double? =
    dropLast(1).let { previous ->
        previous.indexOfLast { it.forecast is DryingForecast.Active }
            .takeIf { it >= 0 }
            ?.let { i ->
                previous.drop(i + 1)
                    .map { it.phase.startedBy }
                    .filterIsInstance<PhaseStart.Pruning>()
                    .fold<PhaseStart.Pruning, Double?>(
                        (previous[i].forecast as DryingForecast.Active).expFit.plateauWeight
                    ) { dry, pruning -> dry?.let { d -> pruning.removedGrams?.let { d - it } } }
            }
    }

// Остаток воды: фактический вес минус сухой вес; если данных не хватает, средняя амплитуда прошлых поливов
private fun List<AnalyzedPhase>.remainingWater(decay: PooledDecay): Double =
    last().phase.measurements.let { ms ->
        ms.lastOrNull()?.weightGrams?.let { wet -> dryWeightBeforeCurrent()?.let { wet - it } }
            ?: (decay.wateredAmplitude * exp(-decay.k * ms.elapsedDays()))
    }

// Последний элемент: текущая фаза; эпоха вырезается по последнему Repotting
fun List<AnalyzedPhase>.priorInterval(config: DryingConfig, noise: Noise?): Duration? =
    currentEpoch()
        .takeIf { it.lastOrNull()?.needsPriorInterval() == true }
        ?.let { epoch ->
            noise?.minSignal(config.confidence)?.let { minSignal ->
                epoch.dropLast(1)
                    .filter { it.forecast is DryingForecast.Active }
                    .map { it.decaySeries() }
                    .pooledDecay()
                    ?.let { decay -> optimalInterval(epoch.remainingWater(decay), decay.k, minSignal) }
            }
        }

// ---------------------------------------------------------------------------
// Распределение Стьюдента
// ---------------------------------------------------------------------------

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