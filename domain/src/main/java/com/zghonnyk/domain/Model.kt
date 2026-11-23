package com.zghonnyk.domain

import java.time.Instant
import java.time.LocalDate

@JvmInline value class SpeciesId(val value: Long)
@JvmInline value class FertilizerId(val value: Long)
@JvmInline value class PotId(val value: Long)
@JvmInline value class SubstrateId(val value: Long)
@JvmInline value class LocationId(val value: Long)
@JvmInline value class PlantId(val value: Long)

@JvmInline value class MeasurementId(val value: Long)
@JvmInline value class WateringEventId(val value: Long)
@JvmInline value class PruningEventId(val value: Long)
@JvmInline value class CalibrationId(val value: Long)
@JvmInline value class RepottingEventId(val value: Long)
@JvmInline value class FertilizingEventId(val value: Long)
@JvmInline
value class SortedMeasurements(val value: List<Measurement>)
@JvmInline
value class ActiveDryingPhase(val measurements: List<Measurement>)

data class Species(
    val id: SpeciesId,
    val latinName: String,
    val category: String? = null,
    val droughtToleranceLevel: Int? = null,
    val wiltingPointMoisturePct: Double? = null,
    val lightRequirement: String? = null
)

data class Pot(
    val id: PotId,
    val name: String,
    val material: String? = null,
    val volumeMl: Int? = null,
    val diameterCm: Double? = null,
    val hasDrainage: Boolean,
    val tareWeightGrams: Double? = null
)

data class Substrate(
    val id: SubstrateId,
    val name: String,
    val perliteRatioPct: Double? = null,
    val soilRatioPct: Double? = null,
    val description: String? = null
)

data class Location(
    val id: LocationId,
    val roomName: String,
    val lightExposure: String? = null,
    val avgHumidityNote: String? = null
)

data class Plant(
    val id: PlantId,
    val name: String,
    val speciesId: SpeciesId? = null,
    val potId: PotId? = null,
    val substrateId: SubstrateId? = null,
    val locationId: LocationId? = null,
    val acquiredDate: LocalDate? = null,
    val notes: String? = null
)

sealed interface PlantTemporalEvent {
    val plantId: PlantId
    val timestamp: Instant
}

data class Measurement(
    val id: MeasurementId,
    override val plantId: PlantId,
    override val timestamp: Instant,
    val weightGrams: Double
) : PlantTemporalEvent

data class WateringEvent(
    val id: WateringEventId,
    override val plantId: PlantId,
    override val timestamp: Instant,
    val waterVolumeMl: Double? = null,
    val notes: String? = null
) : PlantTemporalEvent

data class DryWeightCalibration(
    val id: CalibrationId,
    override val plantId: PlantId,
    override val timestamp: Instant,
    val calibratedWeightGrams: Double,
    val method: String? = null
) : PlantTemporalEvent

data class RepottingEvent(
    val id: RepottingEventId,
    override val plantId: PlantId,
    override val timestamp: Instant,
    val potId: PotId
) : PlantTemporalEvent

data class PruningEvent(
    val id: PruningEventId,
    override val plantId: PlantId,
    override val timestamp: Instant,
    val removedGrams: Double? = null,
    val notes: String? = null
) : PlantTemporalEvent

sealed interface TimelineEntry {
    val at: Instant
    val key: String
}

data class Weighing(val m: MeasurementWithRate) : TimelineEntry {
    override val at: Instant = m.timestamp
    override val key: String = "m:${m.id.value}"
}

data class Watering(
    val id: WateringEventId,
    override val at: Instant,
    val volumeMl: Double?
) : TimelineEntry {
    override val key: String = "w:${id.value}"
}

data class PhaseBreak(
    val id: String,
    override val at: Instant,
    val startedBy: PhaseStart
) : TimelineEntry {
    override val key: String = "b:$id"
}

data class SpeciesMeasurementStats(
    val speciesName: String,
    val activePlantsCount: Long,
    val totalMeasurements: Long,
    val avgWeightGrams: Double?,
    val maxWeightGrams: Double?
)

data class PlantPeakWeight(
    val id: PlantId,
    val plantName: String,
    val speciesName: String?,
    val peakWeight: Double?,
    val totalMeasurements: Long
)

data class MonthlyStatItem(
    val monthYear: String,
    val totalMeasurements: Long,
    val avgWeightGrams: Double?,
    val maxWeightRecorded: Double?
)