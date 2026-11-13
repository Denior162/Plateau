package com.zghonnyk.plateau.domain

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.zghonnyk.plateau.data.db.PlateauDatabase

data class ImportSummary(
    val added: Map<String, Int>,
    val skipped: Int
) {
    override fun toString() =
        if (added.isEmpty()) "Нового нет, пропущено: $skipped"
        else added.entries.joinToString { "${it.key}: +${it.value}" } + ", пропущено: $skipped"
}

class Raw(private val driver: SqlDriver) {
    private fun SqlPreparedStatement.bindAll(args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> bindString(i, null)
                is String -> bindString(i, a)
                is Int -> bindLong(i, a.toLong())
                is Long -> bindLong(i, a)
                is Double -> bindDouble(i, a)
                else -> error("unsupported ${a::class}")
            }
        }
    }

    fun exec(sql: String, vararg args: Any?) {
        driver.execute(null, sql, args.size) { bindAll(args) }.value
    }

    fun <T> query(sql: String, vararg args: Any?, map: (SqlCursor) -> T): List<T> =
        driver.executeQuery(null, sql, { c ->
            val out = mutableListOf<T>()
            while (c.next().value) out += map(c)
            QueryResult.Value(out)
        }, args.size) { bindAll(args) }.value

    fun insert(sql: String, vararg args: Any?): Long {
        exec(sql, *args)
        return query("SELECT last_insert_rowid()") { it.getLong(0)!! }.single()
    }

    /** Читает все колонки как строки, имена полей CSV берёт из cols. */
    fun table(type: String, cols: List<String>, sql: String): List<CsvRecord> =
        query(sql) { c ->
            CsvRecord(type, 0, cols.indices.mapNotNull { i ->
                c.getString(i)?.let { cols[i] to it }
            }.toMap())
        }
}

fun exportRecords(driver: SqlDriver): List<CsvRecord> {
    val raw = Raw(driver)
    return buildList {
        addAll(raw.table("soil", listOf("name", "acidity_ph", "components_description"),
            "SELECT name, acidity_ph, components_description FROM soil_types ORDER BY id"))
        addAll(raw.table("room", listOf("name", "window_orientation"),
            "SELECT name, window_orientation FROM rooms ORDER BY id"))
        addAll(raw.table("pot",
            listOf("name", "empty_mass_grams", "material", "diameter_cm", "volume_ml", "pot_type"),
            "SELECT name, empty_mass_grams, material, diameter_cm, volume_ml, pot_type FROM pots ORDER BY id"))
        addAll(raw.table("species",
            listOf("name", "family", "common_name", "soil", "dry_down_pct", "extra_days",
                "target_pot_type", "watering_method", "max_interval_days"),
            """SELECT s.name, s.family, s.common_name, so.name, s.dry_down_pct, s.extra_days,
                      s.target_pot_type, s.watering_method, s.max_interval_days
               FROM species s LEFT JOIN soil_types so ON so.id = s.soil_id ORDER BY s.id"""))
        addAll(raw.table("plant",
            listOf("name", "species", "room", "pot", "notes", "acquisition_date",
                "archived_at", "measurement_noise_grams"),
            """SELECT p.name, sp.name, r.name, po.name, p.notes, p.acquisition_date,
                      p.archived_at, p.measurement_noise_grams
               FROM plants p LEFT JOIN species sp ON sp.id = p.species_id
               LEFT JOIN rooms r ON r.id = p.room_id
               LEFT JOIN pots po ON po.id = p.pot_id ORDER BY p.id"""))
        addAll(raw.table("measurement", listOf("plant", "timestamp", "weight_grams", "excluded"),
            """SELECT p.name, m.timestamp, m.weight_grams, m.excluded
               FROM measurements m JOIN plants p ON p.id = m.plant_id ORDER BY p.id, m.timestamp"""))
        addAll(raw.table("watering", listOf("plant", "timestamp", "water_volume_ml"),
            """SELECT p.name, w.timestamp, w.water_volume_ml
               FROM watering_sessions w JOIN plants p ON p.id = w.plant_id ORDER BY p.id, w.timestamp"""))
        addAll(raw.table("removal",
            listOf("plant", "timestamp", "removed_grams", "event_type", "notes"),
            """SELECT p.name, e.timestamp, e.removed_grams, e.event_type, e.notes
               FROM mass_removal_events e JOIN plants p ON p.id = e.plant_id ORDER BY p.id, e.timestamp"""))
        addAll(raw.table("soil_addition",
            listOf("plant", "timestamp", "added_grams", "soil", "notes"),
            """SELECT p.name, e.timestamp, e.added_grams, so.name, e.notes
               FROM soil_addition_events e JOIN plants p ON p.id = e.plant_id
               LEFT JOIN soil_types so ON so.id = e.soil_id ORDER BY p.id, e.timestamp"""))
        addAll(raw.table("repotting", listOf("plant", "timestamp", "pot", "soil", "reason"),
            """SELECT p.name, e.timestamp, po.name, so.name, e.reason
               FROM repotting_events e JOIN plants p ON p.id = e.plant_id
               JOIN pots po ON po.id = e.new_pot_id
               LEFT JOIN soil_types so ON so.id = e.soil_id ORDER BY p.id, e.timestamp"""))
    }
}

fun importRecords(db: PlateauDatabase, driver: SqlDriver, records: List<CsvRecord>): ImportSummary {
    val raw = Raw(driver)
    return db.transactionWithResult {
        fun ids(table: String) = raw.query("SELECT name, id FROM $table") {
            it.getString(0)!! to it.getLong(1)!!
        }.toMap().toMutableMap()

        fun pairs(table: String) = raw.query("SELECT plant_id, timestamp FROM $table") {
            it.getLong(0)!! to it.getLong(1)!!
        }.toHashSet()

        val soil = ids("soil_types")
        val room = ids("rooms")
        val pot = ids("pots")
        val species = ids("species")
        val plant = ids("plants")
        val seen = mapOf(
            "measurement" to pairs("measurements"),
            "watering" to pairs("watering_sessions"),
            "removal" to pairs("mass_removal_events"),
            "soil_addition" to pairs("soil_addition_events"),
            "repotting" to pairs("repotting_events"),
        )

        val added = linkedMapOf<String, Int>()
        var skipped = 0
        fun bump(t: String) { added[t] = (added[t] ?: 0) + 1 }

        fun create(map: MutableMap<String, Long>, type: String, r: CsvRecord, sql: String, vararg a: Any?) {
            val name = r.req("name")
            if (name in map) { skipped++; return }
            map[name] = raw.insert(sql, *a); bump(type)
        }

        for (r in records) when (r.type) {
            "soil" -> create(soil, "soil", r,
                "INSERT INTO soil_types(name, acidity_ph, components_description) VALUES (?,?,?)",
                r.req("name"), r.num("acidity_ph"), r.opt("components_description"))
            "room" -> create(room, "room", r,
                "INSERT INTO rooms(name, window_orientation) VALUES (?,?)",
                r.req("name"), r.opt("window_orientation"))
            "pot" -> create(pot, "pot", r,
                "INSERT INTO pots(name, empty_mass_grams, material, diameter_cm, volume_ml, pot_type) VALUES (?,?,?,?,?,?)",
                r.req("name"), r.num("empty_mass_grams"), r.opt("material"),
                r.num("diameter_cm"), r.num("volume_ml"), r.opt("pot_type"))
            "species" -> create(species, "species", r,
                """INSERT INTO species(name, family, common_name, soil_id, dry_down_pct, extra_days,
                       target_pot_type, watering_method, max_interval_days) VALUES (?,?,?,?,?,?,?,?,?)""",
                r.req("name"), r.opt("family"), r.opt("common_name"), r.opt("soil")?.let(soil::getValue),
                r.num("dry_down_pct"), r.int("extra_days"), r.opt("target_pot_type"),
                r.opt("watering_method"), r.int("max_interval_days"))
            "plant" -> create(plant, "plant", r,
                """INSERT INTO plants(name, species_id, room_id, pot_id, notes, acquisition_date,
                       archived_at, measurement_noise_grams) VALUES (?,?,?,?,?,?,?,?)""",
                r.req("name"), r.opt("species")?.let(species::getValue), r.opt("room")?.let(room::getValue),
                r.opt("pot")?.let(pot::getValue), r.opt("notes"),
                r.ts("acquisition_date")?.toEpochMilli() ?: System.currentTimeMillis(),
                r.ts("archived_at")?.toEpochMilli(), r.num("measurement_noise_grams"))
            else -> {
                val pid = plant.getValue(r.req("plant"))
                val ts = r.reqTs().toEpochMilli()
                if (!seen.getValue(r.type).add(pid to ts)) { skipped++; continue }
                when (r.type) {
                    "measurement" -> raw.exec(
                        "INSERT INTO measurements(plant_id, timestamp, weight_grams, excluded) VALUES (?,?,?,?)",
                        pid, ts, r.reqNum("weight_grams"), if (r.bool("excluded")) 1 else 0)
                    "watering" -> raw.exec(
                        "INSERT INTO watering_sessions(plant_id, timestamp, water_volume_ml) VALUES (?,?,?)",
                        pid, ts, r.num("water_volume_ml"))
                    "removal" -> raw.exec(
                        "INSERT INTO mass_removal_events(plant_id, timestamp, removed_grams, event_type, notes) VALUES (?,?,?,?,?)",
                        pid, ts, r.num("removed_grams"), r.opt("event_type") ?: "pruning", r.opt("notes"))
                    "soil_addition" -> raw.exec(
                        "INSERT INTO soil_addition_events(plant_id, timestamp, added_grams, soil_id, notes) VALUES (?,?,?,?,?)",
                        pid, ts, r.num("added_grams"), r.opt("soil")?.let(soil::getValue), r.opt("notes"))
                    "repotting" -> raw.exec(
                        "INSERT INTO repotting_events(plant_id, timestamp, new_pot_id, soil_id, reason) VALUES (?,?,?,?,?)",
                        pid, ts, pot.getValue(r.req("pot")), r.opt("soil")?.let(soil::getValue), r.opt("reason"))
                }
                bump(r.type)
            }
        }
        ImportSummary(added, skipped)
    }
}