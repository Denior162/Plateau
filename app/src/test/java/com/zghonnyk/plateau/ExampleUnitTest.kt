package com.zghonnyk.plateau

import com.zghonnyk.domain.DryingConfig
import com.zghonnyk.domain.MIN_POINTS_FOR_EXP_FIT
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.Verdict
import com.zghonnyk.domain.assessMeasurement
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class AnomalyDetectionBacktest {

    private data class Row(val plant: String, val timestamp: Instant, val grams: Double)

    private fun loadMeasurements(): Map<String, List<Measurement>> {
        val url = javaClass.classLoader?.getResource("measurements.csv")
        requireNotNull(url) { "measurements.csv не найден в domain/src/test/resources/" }
        val file = File(url.toURI())
        return file.readLines().drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split(",")
                Row(p[0], Instant.parse(p[2]), p[3].toDouble())
            }
            .groupBy { it.plant }
            .mapValues { (plant, rows) ->
                rows.sortedBy { it.timestamp }.mapIndexed { i, r ->
                    Measurement(
                        id = MeasurementId(i.toLong()),
                        plantId = PlantId(plant.hashCode().toLong()),
                        timestamp = r.timestamp,
                        weightGrams = r.grams
                    )
                }
            }
    }

    private fun loadWatering(): Map<String, List<Instant>> {
        val url = javaClass.classLoader?.getResource("events.csv") ?: return emptyMap()
        val file = File(url.toURI())
        return file.readLines().asSequence().drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
            .filter { it.size >= 3 && it[2] == "Watered" }
            .groupBy({ it[0] }, { Instant.parse(it[1]) })
            .mapValues { (_, v) -> v.sorted() }
    }

    private fun splitPhases(
        byPlant: Map<String, List<Measurement>>,
        watering: Map<String, List<Instant>>
    ): List<List<Measurement>> {
        val phases = mutableListOf<List<Measurement>>()
        byPlant.forEach { (plant, ms) ->
            val events = watering[plant]
            if (events != null) {
                var current = mutableListOf<Measurement>()
                var e = 0
                for (m in ms) {
                    if (e < events.size && m.timestamp.isAfter(events[e])) {
                        if (current.isNotEmpty()) phases += current
                        current = mutableListOf(m)
                        e++
                    } else current += m
                }
                if (current.isNotEmpty()) phases += current
            } else {
                var current = mutableListOf<Measurement>()
                for ((i, m) in ms.withIndex()) {
                    if (i > 0 && m.weightGrams > ms[i - 1].weightGrams + 10) {
                        if (current.isNotEmpty()) phases += current
                        current = mutableListOf(m)
                    } else current += m
                }
                if (current.isNotEmpty()) phases += current
            }
        }
        return phases
    }

    @Test
    fun backtest() {
        val byPlant = loadMeasurements()
        val watering = loadWatering()
        if (watering.isEmpty()) {
            println("WARNING: events.csv не найден, фазы по эвристике (скачок > 10 г)")
        }
        val phases = splitPhases(byPlant, watering).filter { it.size > MIN_POINTS_FOR_EXP_FIT }

        val config = DryingConfig(
            confidenceFactor = 3.0,
            sigmaFloor = 5.0,
            growthUncertaintyPerDay = 2.0
        )

        // Аномалии в абсолютных граммах и в процентах от веса
        val absAnomalies = listOf(20.0, 40.0)
        val relAnomaliesPercent = listOf(5.0, 10.0) // 5% и 10% от текущего веса

        println("=== DETAILED BACKTEST ===")
        println("Фаз для теста: ${phases.size}")

        for (cf in listOf(2.5, 3.0, 3.5, 4.0)) {
            val cfg = config.copy(confidenceFactor = cf)

            var tested = 0
            var falseAlarms = 0
            var injectedTotal = 0

            val caughtHeavyAbs = absAnomalies.associateWith { 0 }.toMutableMap()
            val caughtLightAbs = absAnomalies.associateWith { 0 }.toMutableMap()
            val caughtHeavyRel = relAnomaliesPercent.associateWith { 0 }.toMutableMap()
            val caughtLightRel = relAnomaliesPercent.associateWith { 0 }.toMutableMap()

            println("\n-----------------------------")
            println("--- TESTING CF = $cf ---")
            println("-----------------------------")

            for (phase in phases) {
                val injectAt = maxOf(MIN_POINTS_FOR_EXP_FIT, phase.size / 2)

                for (i in MIN_POINTS_FOR_EXP_FIT until phase.size) {
                    val history = phase.subList(0, i)
                    val candidate = phase[i]
                    tested++

                    val v =
                        assessMeasurement(history, candidate.weightGrams, candidate.timestamp, cfg)
                    if (v !is Verdict.Plausible) {
                        falseAlarms++
                        val expectedGrams = when (v) {
                            is Verdict.TooLight -> v.expected.grams
                            is Verdict.TooHeavy -> v.expected.grams
                        }
                        val sigma = when (v) {
                            is Verdict.TooLight -> v.expected.sigma
                            is Verdict.TooHeavy -> v.expected.sigma
                        }
                        val diff = candidate.weightGrams - expectedGrams
                        println("FA | ${candidate.timestamp} | i=$i/${phase.size} | cand:${candidate.weightGrams}g | exp:${"%.1f".format(expectedGrams)}g ±${"%.1f".format(sigma)}g | diff:${"%.1f".format(diff)}g | verdict:$v")
                    }

                    // Инъекция ровно в правильную точку injectAt
                    if (i == injectAt) {
                        injectedTotal++

                        // 1. Абсолютные аномалии
                        for (a in absAnomalies) {
                            val heavy = assessMeasurement(
                                history,
                                candidate.weightGrams + a,
                                candidate.timestamp,
                                cfg
                            )
                            val light = assessMeasurement(
                                history,
                                candidate.weightGrams - a,
                                candidate.timestamp,
                                cfg
                            )
                            if (heavy is Verdict.TooHeavy) caughtHeavyAbs[a] = caughtHeavyAbs[a]!! + 1
                            if (light is Verdict.TooLight) caughtLightAbs[a] = caughtLightAbs[a]!! + 1
                        }

                        // 2. Относительные аномалии (%)
                        for (pct in relAnomaliesPercent) {
                            val delta = candidate.weightGrams * (pct / 100.0)
                            val heavy = assessMeasurement(
                                history,
                                candidate.weightGrams + delta,
                                candidate.timestamp,
                                cfg
                            )
                            val light = assessMeasurement(
                                history,
                                candidate.weightGrams - delta,
                                candidate.timestamp,
                                cfg
                            )
                            if (heavy is Verdict.TooHeavy) caughtHeavyRel[pct] = caughtHeavyRel[pct]!! + 1
                            if (light is Verdict.TooLight) caughtLightRel[pct] = caughtLightRel[pct]!! + 1
                        }
                    }
                }
            }

            val falseAlarmRate = falseAlarms.toDouble() / tested
            println("\nИтог по CF = $cf:")
            println("Протестировано замеров: $tested")
            println("Всего инъекций проведено: $injectedTotal")
            println("Ложные тревоги: $falseAlarms из $tested (${"%.2f".format(falseAlarmRate * 100.0)}%)")

            println("\nАбсолютные инъекции (из $injectedTotal):")
            absAnomalies.forEach { a ->
                println("  +${a.toInt()} г: ${caughtHeavyAbs[a]} / $injectedTotal (${"%.1f".format(caughtHeavyAbs[a]!! * 100.0 / injectedTotal)}%)")
                println("  -${a.toInt()} г: ${caughtLightAbs[a]} / $injectedTotal (${"%.1f".format(caughtLightAbs[a]!! * 100.0 / injectedTotal)}%)")
            }

            println("\nОтносительные инъекции % (из $injectedTotal):")
            relAnomaliesPercent.forEach { pct ->
                println("  +${pct.toInt()}%: ${caughtHeavyRel[pct]} / $injectedTotal (${"%.1f".format(caughtHeavyRel[pct]!! * 100.0 / injectedTotal)}%)")
                println("  -${pct.toInt()}%: ${caughtLightRel[pct]} / $injectedTotal (${"%.1f".format(caughtLightRel[pct]!! * 100.0 / injectedTotal)}%)")
            }

            // Регрессионный контроль для базового CF = 3.0
            if (cf == 3.0) {
                assertTrue("Слишком высокий уровень ложных тревог: ${falseAlarmRate * 100}%", falseAlarmRate < 0.05)
            }
        }

        assertTrue("Ни одной фазы для теста", phases.isNotEmpty())
    }
}