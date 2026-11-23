package com.zghonnyk.plateau.data.repository

import android.net.Uri
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zghonnyk.domain.MonthlyStatItem
import com.zghonnyk.domain.PlantPeakWeight
import com.zghonnyk.domain.SpeciesMeasurementStats
import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.db.Watering_sessions
import com.zghonnyk.plateau.data.toDomain
import com.zghonnyk.plateau.domain.CsvRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

data class ImportSummary(
    val plantsAdded: Int,
    val measurementsAdded: Int,
    val wateringsAdded: Int,
    val repottingsAdded: Int,
    val measurementsSkipped: Int
)

fun fetchAllMeasurements(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllMeasurements().executeAsList()

fun exportRows(db: PlateauDatabase): List<CsvRecord> {
    val plants = fetchPlantsWithSpecies(db).sortedBy { it.id }
    val byPlant = fetchAllMeasurements(db).groupBy { it.plant_id }
    return plants.flatMap { p ->
        val ms = byPlant[p.id].orEmpty()
        if (ms.isEmpty()) {
            listOf(
                CsvRecord(
                    "plant", 0, mapOf("plant" to p.plant_name, "species" to (p.species_name ?: ""))
                )
            )
        } else {
            ms.map {
                CsvRecord(
                    "measurement", 0, mapOf(
                        "plant" to p.plant_name,
                        "species" to (p.species_name ?: ""),
                        "timestamp" to Instant.ofEpochMilli(it.timestamp).toString(),
                        "weight_grams" to it.weight_grams.toString()
                    )
                )
            }
        }
    }
}

fun fetchAllSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllSpecies().executeAsList()

fun fetchPlantsWithSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPlantsWithSpecies().executeAsList()

fun fetchMeasurementsForPlant(db: PlateauDatabase, plantId: Long) =
    db.plateauDatabaseQueries.selectMeasurementsForPlant(plantId).executeAsList()

fun insertNewPlant(db: PlateauDatabase): InsertPlant = { name, speciesId, acquiredAt, photoPath ->
    db.transactionWithResult {
        val q = db.plateauDatabaseQueries
        q.insertPlant(name, speciesId, acquisition_date = acquiredAt)
        val id = q.selectLastInsertRowId().executeAsOne()
        photoPath?.let { q.insertPhoto(id, acquiredAt, it, null) }
        id
    }
}

typealias InsertPlant = (name: String, speciesId: Long?, acquiredAt: Long, photoPath: String?) -> Long
typealias CopyPhoto = suspend (Uri) -> String
typealias DeletePhoto = (String) -> Unit


fun updateWatering(
    db: PlateauDatabase, id: Long, plantId: Long, oldTs: Long, newTs: Long, volumeMl: Double?
) {
    val q = db.plateauDatabaseQueries
    q.transaction {
        q.updateWateringTimestamp(newTs, volumeMl, id)
        q.moveMeasurementAt(newTs, plantId, oldTs)
    }
}


fun insertNewMeasurement(db: PlateauDatabase, plantId: Long, timestamp: Long, weightGrams: Double) =
    db.plateauDatabaseQueries.insertMeasurement(plantId, timestamp, weightGrams)


fun removePlant(db: PlateauDatabase, plantId: Long) = db.plateauDatabaseQueries.deletePlant(plantId)

fun deleteMeasurement(db: PlateauDatabase, id: Long) =
    db.plateauDatabaseQueries.deleteMeasurement(id)

fun observePlantsWithSpecies(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPlantsWithSpecies().asFlow().mapToList(Dispatchers.IO)

fun observeAllMeasurements(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllMeasurements().asFlow().mapToList(Dispatchers.IO)

fun observeAllWaterings(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllWatering().asFlow().mapToList(Dispatchers.IO)

fun updatePlant(database: PlateauDatabase, id: Long, name: String, speciesId: Long?) {
    database.plateauDatabaseQueries.updatePlant(name, speciesId, id)
}

fun updateMeasurement(db: PlateauDatabase, id: Long, weightGrams: Double, timestamp: Long) =
    db.plateauDatabaseQueries.updateMeasurementWeightAndTime(weightGrams, timestamp, id)

fun observeSpecies(database: PlateauDatabase) =
    database.plateauDatabaseQueries.selectAllSpecies().asFlow().mapToList(Dispatchers.IO)

fun observeAllWatering(database: PlateauDatabase): Flow<List<Watering_sessions>> =
    database.plateauDatabaseQueries.selectAllWatering().asFlow().mapToList(Dispatchers.IO)

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

fun observeAllSoilAdditions(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllSoilAdditions().asFlow().mapToList(Dispatchers.IO)

fun observeAllPhotos(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPhotos().asFlow().mapToList(Dispatchers.IO)

fun observePhotosByPlant(db: PlateauDatabase, plantId: Long) =
    db.plateauDatabaseQueries.selectPhotosByPlant(plantId).asFlow().mapToList(Dispatchers.IO)

fun observeLatestPhotos(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectLatestPhotoPerPlant().asFlow().mapToList(Dispatchers.IO)

fun insertPhoto(db: PlateauDatabase, plantId: Long, timestamp: Long, filePath: String, caption: String? = null) =
    db.plateauDatabaseQueries.insertPhoto(plantId, timestamp, filePath, caption)

fun deletePhoto(db: PlateauDatabase, id: Long) =
    db.plateauDatabaseQueries.deletePhoto(id)

fun insertNewSoilAddition(
    db: PlateauDatabase,
    plantId: Long,
    timestamp: Long,
    addedGrams: Double?,
    soilId: Long?,
    weightAfterGrams: Double?,
) {
    val q = db.plateauDatabaseQueries
    q.transaction {
        q.insertSoilAddition(plantId, timestamp, addedGrams, soilId, null)
        if (weightAfterGrams != null) q.insertMeasurement(plantId, timestamp, weightAfterGrams)
    }
}

fun deleteSoilAddition(db: PlateauDatabase, id: Long) =
    db.plateauDatabaseQueries.deleteSoilAddition(id)

fun observeAllPots(db: PlateauDatabase) =
    db.plateauDatabaseQueries.selectAllPots().asFlow().mapToList(Dispatchers.IO)

fun observeTotalEvaporatedBySpecies(db: PlateauDatabase, speciesId: Long): Flow<Double> =
    db.plateauDatabaseQueries.getTotalEvaporatedBySpecies(speciesId)
        .asFlow()
        .map { it.executeAsOneOrNull() ?: 0.0 }

fun observeGlobalMedianEvaporation(db: PlateauDatabase): Flow<Double> =
    db.plateauDatabaseQueries.getGlobalMedianDailyEvaporation()
        .asFlow()
        .map { it.executeAsOneOrNull() ?: 0.0 }

fun observeSpeciesMeasurementStats(db: PlateauDatabase): Flow<List<SpeciesMeasurementStats>> =
    db.plateauDatabaseQueries.getSpeciesMeasurementStats()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { list -> list.map { it.toDomain() } }

fun observePlantsWithMinPeakWeight(db: PlateauDatabase, minWeight: Double): Flow<List<PlantPeakWeight>> =
    db.plateauDatabaseQueries.getPlantsWithMinPeakWeight(minWeight)
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { list -> list.map { it.toDomain() } }

fun observeMonthlyStats(db: PlateauDatabase): Flow<List<MonthlyStatItem>> =
    db.plateauDatabaseQueries.getMonthlyMeasurementStats()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { list -> list.map { it.toDomain() } }