package com.zghonnyk.plateau.data

import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.Plant
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.SpeciesId
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