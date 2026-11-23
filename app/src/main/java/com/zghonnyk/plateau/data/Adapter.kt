package com.zghonnyk.plateau.data

import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.MonthlyStatItem
import com.zghonnyk.domain.Plant
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.PlantPeakWeight
import com.zghonnyk.domain.SpeciesId
import com.zghonnyk.domain.SpeciesMeasurementStats
import com.zghonnyk.plateau.data.db.GetMonthlyMeasurementStats
import com.zghonnyk.plateau.data.db.GetPlantsWithMinPeakWeight
import com.zghonnyk.plateau.data.db.GetSpeciesMeasurementStats
import com.zghonnyk.plateau.data.db.Measurements
import com.zghonnyk.plateau.data.db.SelectAllPlantsWithSpecies
import com.zghonnyk.plateau.data.db.Species
import java.time.Instant

fun Species.toDomain() = com.zghonnyk.domain.Species(
    id = SpeciesId(id),
    latinName = name,
    category = null
)

fun Measurements.toDomain() = Measurement(
    id = MeasurementId(id),
    plantId = PlantId(plant_id),
    timestamp = Instant.ofEpochMilli(timestamp),
    weightGrams = weight_grams
)

fun SelectAllPlantsWithSpecies.toDomainPlant() = Plant(
    id = PlantId(id),
    name = plant_name,
    speciesId = species_id?.let { SpeciesId(it) },
    potId = null,
    substrateId = null,
    locationId = null,
    acquiredDate = null,
    notes = null
)

fun GetSpeciesMeasurementStats.toDomain() = SpeciesMeasurementStats(
    speciesName = species_name,
    activePlantsCount = active_plants_count,
    totalMeasurements = total_measurements,
    avgWeightGrams = avg_weight_grams,
    maxWeightGrams = max_weight_grams
)

fun GetPlantsWithMinPeakWeight.toDomain() = PlantPeakWeight(
    id = PlantId(id),
    plantName = plant_name,
    speciesName = species_name,
    peakWeight = peak_weight,
    totalMeasurements = total_measurements
)

fun GetMonthlyMeasurementStats.toDomain() = MonthlyStatItem(
    monthYear = month_year,
    totalMeasurements = total_measurements,
    avgWeightGrams = avg_weight_grams,
    maxWeightRecorded = max_weight_recorded
)