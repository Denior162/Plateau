package com.zghonnyk.plateau.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.db.Watering_sessions
import com.zghonnyk.plateau.domain.CsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class ImportSummary(
    val plantsAdded: Int,
    val measurementsAdded: Int,
    val measurementsSkipped: Int
)

fun fetchAllMeasurements(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllMeasurements().executeAsList()

fun exportRows(db: PlateauDatabase): List<CsvRow> {
    val plants = fetchPlantsWithSpecies(db).sortedBy { it.id }
    val byPlant = fetchAllMeasurements(db).groupBy { it.plant_id }
    return plants.flatMap { p ->
        val ms = byPlant[p.id].orEmpty()
        if (ms.isEmpty()) listOf(CsvRow(p.plant_name, p.species_name, null, null))
        else ms.map {
            CsvRow(
                p.plant_name,
                p.species_name,
                Instant.ofEpochMilli(it.timestamp),
                it.weight_grams
            )
        }
    }
}

fun importRows(db: PlateauDatabase, rows: List<CsvRow>): ImportSummary {
    val q = db.plateauDatabaseQueries
    return db.transactionWithResult {
        val species = q.selectAllSpecies().executeAsList()
            .associateBy({ it.name }, { it.id }).toMutableMap()
        val plants = q.selectAllPlantsWithSpecies().executeAsList()
            .associateBy({ it.plant_name to it.species_id }, { it.id }).toMutableMap()
        val seen = q.selectAllMeasurements().executeAsList()
            .mapTo(HashSet()) { it.plant_id to it.timestamp }

        var plantsAdded = 0
        var added = 0
        var skipped = 0

        for ((plant, species1, timestamp, weightGrams) in rows) {
            val speciesId = species1?.let { name ->
                species.getOrPut(name) { q.insertSpecies(name); q.lastInsertRowId().executeAsOne() }
            }
            val plantId = plants.getOrPut(plant to speciesId) {
                q.insertPlant(plant, speciesId)
                plantsAdded++
                q.lastInsertRowId().executeAsOne()
            }
            if (timestamp != null && weightGrams != null) {
                val ts = timestamp.toEpochMilli()
                if (seen.add(plantId to ts)) {
                    q.insertMeasurement(plantId, ts, weightGrams); added++
                } else skipped++
            }
        }
        ImportSummary(plantsAdded, added, skipped)
    }
}

fun fetchAllSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllSpecies().executeAsList()

fun fetchPlantsWithSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPlantsWithSpecies().executeAsList()

fun fetchMeasurementsForPlant(db: PlateauDatabase, plantId: Long) =
    db.plateauDatabaseQueries.selectMeasurementsForPlant(plantId).executeAsList()

fun insertNewPlant(db: PlateauDatabase, name: String, speciesId: Long?) =
    db.plateauDatabaseQueries.insertPlant(name, speciesId)


fun insertNewMeasurement(db: PlateauDatabase, plantId: Long, timestamp: Long, weightGrams: Double) =
    db.plateauDatabaseQueries.insertMeasurement(plantId, timestamp, weightGrams)


fun removePlant(db: PlateauDatabase, plantId: Long) =
    db.plateauDatabaseQueries.deletePlant(plantId)

fun deleteMeasurement(db: PlateauDatabase, id: Long) = db.plateauDatabaseQueries.deleteMeasurement(id)

fun observePlantsWithSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPlantsWithSpecies()
        .asFlow()
        .mapToList(Dispatchers.IO)

fun observeAllMeasurements(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllMeasurements()
        .asFlow()
        .mapToList(Dispatchers.IO)

fun observeAllWaterings(db: PlateauDatabase) = db.plateauDatabaseQueries.selectAllWatering()
    .asFlow()
    .mapToList(Dispatchers.IO)

fun updatePlant(database: PlateauDatabase, id: Long, name: String, speciesId: Long?) {
    database.plateauDatabaseQueries.updatePlant(name, speciesId, id)
}

fun updateMeasurement(database: PlateauDatabase, id: Long, weightGrams: Double) {
    database.plateauDatabaseQueries.updateMeasurement(
        weightGrams = weightGrams,
        id = id
    )
}

fun observeSpecies(database: PlateauDatabase) =
    database.plateauDatabaseQueries.selectAllSpecies().asFlow().mapToList(Dispatchers.IO)

fun observeAllWatering(database: PlateauDatabase): Flow<List<Watering_sessions>> =
    database.plateauDatabaseQueries.selectAllWatering()
        .asFlow()
        .mapToList(Dispatchers.IO)

fun insertNewWatering(
    database: PlateauDatabase,
    plantId: Long,
    timestamp: Long,
    volumeMl: Double?,
    weightAfterGrams: Double?,
) {
    val q = database.plateauDatabaseQueries
    q.transaction {
        q.insertWatering(plantId, timestamp, volumeMl)
        if (weightAfterGrams != null) q.insertMeasurement(plantId, timestamp, weightAfterGrams)
    }
}

fun fetchWateringsForPlant(db: PlateauDatabase, plantId: Long) =
    db.plateauDatabaseQueries.selectAllWatering().executeAsList().filter { it.plant_id == plantId }

fun observeAllPrunings(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPrunings().asFlow().mapToList(Dispatchers.IO)

fun insertNewPruning(db: PlateauDatabase, plantId: Long, timestamp: Long, removedGrams: Double?) =
    db.plateauDatabaseQueries.insertPruning(plantId, timestamp, removedGrams)

sealed interface PotChoice {
    data class Existing(val id: Long) : PotChoice
    data class New(val name: String, val massGrams: Double?) : PotChoice
}

fun insertNewRepotting(
    db: PlateauDatabase,
    plantId: Long,
    timestamp: Long,
    pot: PotChoice,
    weightAfterGrams: Double?,
) {
    val q = db.plateauDatabaseQueries
    q.transaction {
        val potId = when (pot) {
            is PotChoice.Existing -> pot.id
            is PotChoice.New -> {
                q.insertPot(pot.name, pot.massGrams)
                q.lastInsertRowId().executeAsOne()
            }
        }
        q.insertRepotting(plantId, timestamp, potId)
        if (weightAfterGrams != null) q.insertMeasurement(plantId, timestamp, weightAfterGrams)
    }
}

fun observeAllRepottings(db: PlateauDatabase) =
   db.plateauDatabaseQueries.selectAllRepottings().asFlow().mapToList(Dispatchers.IO)