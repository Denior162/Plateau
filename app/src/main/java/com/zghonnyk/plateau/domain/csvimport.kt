package com.zghonnyk.plateau.domain

import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.repository.ImportSummary

fun importBackup(db: PlateauDatabase, entries: List<CsvEntry>): ImportSummary {
    val q = db.plateauDatabaseQueries

    return db.transactionWithResult {
        val soil = q.selectAllSoilTypes().executeAsList().associate { it.name to it.id }.toMutableMap()
        val room = q.selectAllRooms().executeAsList().associate { it.name to it.id }.toMutableMap()
        val pot = q.selectAllPots().executeAsList().associate { it.name to it.id }.toMutableMap()
        val species = q.selectAllSpecies().executeAsList().associate { it.name to it.id }.toMutableMap()
        val plant = q.selectAllPlantsWithSpecies().executeAsList().associate { it.plant_name to it.id }.toMutableMap()

        val seenMeasurement = q.selectAllMeasurements().executeAsList().mapTo(HashSet()) { it.plant_id to it.timestamp }
        val seenWatering = q.selectAllWateringSessions().executeAsList().mapTo(HashSet()) { it.plant_id to it.timestamp }
        val seenRemoval = q.selectAllMassRemovalEvents().executeAsList().mapTo(HashSet()) { it.plant_id to it.timestamp }
        val seenSoilAddition = q.selectAllSoilAdditionEvents().executeAsList().mapTo(HashSet()) { it.plant_id to it.timestamp }
        val seenRepotting = q.selectAllRepottingEvents().executeAsList().mapTo(HashSet()) { it.plant_id to it.timestamp }

        val added = linkedMapOf<String, Int>()
        var skipped = 0
        fun bump(type: String) { added[type] = (added[type] ?: 0) + 1 }

        fun lookup(map: Map<String, Long>, kind: String, name: String, line: Int): Long =
            map[name] ?: csvBad("строка $line: $kind «$name» нет ни в файле, ни в базе")

        entries.filterIsInstance<SoilEntry>().forEach { e ->
            if (e.name !in soil) {
                q.insertSoil(e.name, e.acidityPh, e.components)
                soil[e.name] = q.lastInsertRowId().executeAsOne()
                bump("soil")
            } else skipped++
        }

        entries.filterIsInstance<RoomEntry>().forEach { e ->
            if (e.name !in room) {
                q.insertRoom(e.name, e.windowOrientation)
                room[e.name] = q.lastInsertRowId().executeAsOne()
                bump("room")
            } else skipped++
        }

        entries.filterIsInstance<PotEntry>().forEach { e ->
            if (e.name !in pot) {
                q.insertPotFull(e.name, e.emptyMassGrams, e.material, e.diameterCm, e.volumeMl, e.potType)
                pot[e.name] = q.lastInsertRowId().executeAsOne()
                bump("pot")
            } else skipped++
        }

        entries.filterIsInstance<SpeciesEntry>().forEach { e ->
            if (e.name !in species) {
                q.insertSpeciesFull(
                    name = e.name,
                    family = e.family,
                    common_name = e.commonName,
                    soil_id = e.soil?.let { lookup(soil, "грунт", it, e.line) },
                    dry_down_pct = e.dryDownPct,
                    extra_days = e.extraDays?.toLong(),
                    target_pot_type = e.targetPotType,
                    watering_method = e.wateringMethod,
                    max_interval_days = e.maxIntervalDays?.toLong()
                )
                species[e.name] = q.lastInsertRowId().executeAsOne()
                bump("species")
            } else skipped++
        }

        entries.filterIsInstance<PlantEntry>().forEach { e ->
            if (e.name !in plant) {
                q.insertPlantFull(
                    name = e.name,
                    species_id = e.species?.let { lookup(species, "вид", it, e.line) },
                    room_id = e.room?.let { lookup(room, "комната", it, e.line) },
                    pot_id = e.pot?.let { lookup(pot, "горшок", it, e.line) },
                    notes = e.notes,
                    acquisition_date = e.acquisition?.toEpochMilli() ?: System.currentTimeMillis(),
                    archived_at = e.archivedAt?.toEpochMilli(),
                    measurement_noise_grams = e.noiseGrams
                )
                plant[e.name] = q.lastInsertRowId().executeAsOne()
                bump("plant")
            } else skipped++
        }

        entries.filterIsInstance<MeasurementEntry>().forEach { e ->
            val pid = lookup(plant, "растение", e.plant, e.line)
            val ts = e.at.toEpochMilli()
            if (seenMeasurement.add(pid to ts)) {
                q.insertMeasurementFull(pid, ts, e.weightGrams, if (e.excluded) 1L else 0L)
                bump("measurement")
            } else skipped++
        }

        entries.filterIsInstance<WateringEntry>().forEach { e ->
            val pid = lookup(plant, "растение", e.plant, e.line)
            val ts = e.at.toEpochMilli()
            if (seenWatering.add(pid to ts)) {
                q.insertWatering(pid, ts, e.volumeMl)
                bump("watering")
            } else skipped++
        }

        entries.filterIsInstance<RemovalEntry>().forEach { e ->
            val pid = lookup(plant, "растение", e.plant, e.line)
            val ts = e.at.toEpochMilli()
            if (seenRemoval.add(pid to ts)) {
                q.insertRemoval(pid, ts, e.grams, e.eventType, e.notes)
                bump("removal")
            } else skipped++
        }

        entries.filterIsInstance<SoilAdditionEntry>().forEach { e ->
            val pid = lookup(plant, "растение", e.plant, e.line)
            val ts = e.at.toEpochMilli()
            if (seenSoilAddition.add(pid to ts)) {
                q.insertSoilAddition(pid, ts, e.grams, e.soil?.let { lookup(soil, "грунт", it, e.line) }, e.notes)
                bump("soil_addition")
            } else skipped++
        }

        entries.filterIsInstance<RepottingEntry>().forEach { e ->
            val pid = lookup(plant, "растение", e.plant, e.line)
            val ts = e.at.toEpochMilli()
            if (seenRepotting.add(pid to ts)) {
                q.insertRepottingFull(
                    pid,
                    ts,
                    lookup(pot, "горшок", e.pot, e.line),
                    e.soil?.let { lookup(soil, "грунт", it, e.line) },
                    e.reason
                )
                bump("repotting")
            } else skipped++
        }

        ImportSummary(
            plantsAdded = added["plant"] ?: 0,
            measurementsAdded = added["measurement"] ?: 0,
            wateringsAdded = added["watering"] ?: 0,
            repottingsAdded = added["repotting"] ?: 0,
            measurementsSkipped = skipped
        )
    }
}