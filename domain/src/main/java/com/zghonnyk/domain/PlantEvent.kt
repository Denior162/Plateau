package com.zghonnyk.domain

import java.time.Instant

sealed interface Event {
    val at: Instant
}

data class Weighed(val id: MeasurementId, override val at: Instant, val grams: Double) : Event
data class Watered(override val at: Instant) : Event
data class Repotted(override val at: Instant) : Event

sealed interface PhaseStart {
    data object First : PhaseStart
    data object Watering : PhaseStart
    data class Pruning(val removedGrams: Double?) : PhaseStart
    data class Repotting(val potId: PotId?) : PhaseStart
}

data class AnalyzedPhase(
    val phase: Phase, val forecast: DryingForecast
)

data class Phase(
    val startedBy: PhaseStart, val measurements: List<Measurement>
)

data class Epoch(val phases: List<Phase>)
typealias History = List<Epoch>

private fun <T> List<T>.mapLast(f: (T) -> T): List<T> = dropLast(1) + f(last())
private fun Epoch.mapLastPhase(f: (Phase) -> Phase): Epoch = Epoch(phases.mapLast(f))

fun List<Phase>.activeMeasurements(): List<Measurement> {
    return this.lastOrNull()?.measurements ?: emptyList()
}

fun List<Phase>.allMeasurementsFlattened(): List<Measurement> {
    return this.flatMap { it.measurements }
}

private fun PhaseStart.rank(): Int = when (this) {
    PhaseStart.First -> 0
    PhaseStart.Watering -> 1
    is PhaseStart.Pruning -> 2
    is PhaseStart.Repotting -> 3
}

private fun PhaseStart.orStronger(other: PhaseStart): PhaseStart =
    if (other.rank() > this.rank()) other else this

fun splitIntoPhases(
    measurements: List<Measurement>,
    events: List<PlantTemporalEvent>
): List<Phase> {
    val phaseBoundaryEvents = events.filter {
        it is WateringEvent || it is PruningEvent || it is RepottingEvent
    }

    val allEvents = (measurements + phaseBoundaryEvents).sortedWith(
        compareBy<PlantTemporalEvent> { it.timestamp }.thenBy {
            when (it) {
                is RepottingEvent -> 0
                is PruningEvent -> 1
                is WateringEvent -> 2
                is Measurement -> 3
                else -> 4
            }
        }
    )

    return allEvents.fold(listOf(Phase(PhaseStart.First, emptyList()))) { acc, event ->
        val lastPhase = acc.last()
        when (event) {
            is Measurement -> {
                acc.dropLast(1) + lastPhase.copy(measurements = lastPhase.measurements + event)
            }
            is RepottingEvent -> {
                val start = PhaseStart.Repotting(event.potId)
                if (lastPhase.measurements.isEmpty()) {
                    acc.dropLast(1) + lastPhase.copy(startedBy = start)
                } else {
                    acc + Phase(start, emptyList())
                }
            }
            is PruningEvent -> {
                val start = PhaseStart.Pruning(event.removedGrams)
                if (lastPhase.measurements.isEmpty()) {
                    acc.dropLast(1) + lastPhase.copy(startedBy = start)
                } else {
                    acc + Phase(start, emptyList())
                }
            }
            is WateringEvent -> {
                val start = PhaseStart.Watering
                if (lastPhase.measurements.isEmpty()) {
                    acc.dropLast(1) + lastPhase.copy(startedBy = start)
                } else {
                    acc + Phase(start, emptyList())
                }
            }
            else -> acc
        }
    }
}

fun List<AnalyzedPhase>.latestMoisture(tare: Tare): MoistureReading? {
    val current = lastOrNull() ?: return null
    val currentWet = current.phase.measurements.lastOrNull()?.weightGrams ?: return null

    (current.forecast as? DryingForecast.Active)?.moistureAt(currentWet, tare)?.let {
        return it
    }

    val pruningStart = current.phase.startedBy as? PhaseStart.Pruning ?: return null
    val removed = pruningStart.removedGrams ?: return null
    val previousActive = dropLast(1).lastOrNull()?.forecast as? DryingForecast.Active ?: return null

    return moistureRangeAfterPruning(currentWet, previousActive.expFit.plateauWeight, removed, tare)
}

private fun exactMoisture(current: AnalyzedPhase, wet: Double, tare: Tare): MoistureReading? =
    (current.forecast as? DryingForecast.Active)?.let {
        moisturePercent(
            wet,
            it.expFit.plateauWeight,
            tare
        )
    }?.let { MoistureReading.Exact(it, tare) }

private fun rangeMoisture(phases: List<AnalyzedPhase>, wet: Double, tare: Tare): MoistureReading? =
    (phases.last().phase.startedBy as? PhaseStart.Pruning)?.removedGrams?.let { removed ->
        (phases.dropLast(1).lastOrNull()?.forecast as? DryingForecast.Active)?.let { previous ->
            moistureRangeAfterPruning(wet, previous.expFit.plateauWeight, removed, tare)
        }
    }

fun timeline(phases: List<Phase>, events: List<PlantTemporalEvent>): List<TimelineEntry> =
    (weightings(phases) + eventEntries(events))
        .sortedWith(compareBy<TimelineEntry> { it.at }
            .thenBy {
                when (it) {
                    is PhaseBreak -> 0
                    is Watering -> 1
                    is Weighing -> 2
                }
            }
        )


private fun weightings(phases: List<Phase>) = phases.flatMap { phase ->
    phase.measurements.withRates().map { Weighing(it) }
}


private fun eventEntries(events: List<PlantTemporalEvent>): List<TimelineEntry> =
    events.mapNotNull { event ->
        when (event) {
            is WateringEvent -> Watering(
                id = event.id,
                at = event.timestamp,
                volumeMl = event.waterVolumeMl
            )
            is PruningEvent -> PhaseBreak(
                id = "p:${event.id.value}",
                at = event.timestamp,
                startedBy = PhaseStart.Pruning(event.removedGrams)
            )
            is RepottingEvent -> PhaseBreak(
                id = "r:${event.id.value}",
                at = event.timestamp,
                startedBy = PhaseStart.Repotting(event.potId)
            )
            else -> null
        }
    }