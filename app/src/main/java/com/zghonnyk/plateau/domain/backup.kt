package com.zghonnyk.plateau.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant

/** Типизированная запись файла. Ссылки на другие сущности хранятся именами. */
sealed interface CsvEntry { val line: Int }

data class SoilEntry(
    override val line: Int, val name: String, val acidityPh: Double?, val components: String?,
) : CsvEntry

data class RoomEntry(
    override val line: Int, val name: String, val windowOrientation: String?,
) : CsvEntry

data class PotEntry(
    override val line: Int, val name: String, val emptyMassGrams: Double?, val material: String?,
    val diameterCm: Double?, val volumeMl: Double?, val potType: String?,
) : CsvEntry

data class SpeciesEntry(
    override val line: Int, val name: String, val family: String?, val commonName: String?,
    val soil: String?, val dryDownPct: Double?, val extraDays: Int?, val targetPotType: String?,
    val wateringMethod: String?, val maxIntervalDays: Int?,
) : CsvEntry

data class PlantEntry(
    override val line: Int, val name: String, val species: String?, val room: String?, val pot: String?,
    val notes: String?, val acquisition: Instant?, val archivedAt: Instant?, val noiseGrams: Double?,
) : CsvEntry

data class MeasurementEntry(
    override val line: Int, val plant: String, val at: Instant, val weightGrams: Double, val excluded: Boolean,
) : CsvEntry

data class WateringEntry(
    override val line: Int, val plant: String, val at: Instant, val volumeMl: Double?,
) : CsvEntry

data class RemovalEntry(
    override val line: Int, val plant: String, val at: Instant, val grams: Double?,
    val eventType: String, val notes: String?,
) : CsvEntry

data class SoilAdditionEntry(
    override val line: Int, val plant: String, val at: Instant, val grams: Double?,
    val soil: String?, val notes: String?,
) : CsvEntry

data class RepottingEntry(
    override val line: Int, val plant: String, val at: Instant, val pot: String,
    val soil: String?, val reason: String?,
) : CsvEntry

// Значения совпадают с CHECK-ограничениями в схеме БД.
private val POT_TYPES = setOf("classic", "cachepot", "self_watering", "technical")
private val WATERING_METHODS = setOf("root", "drip", "overhead", "subsurface", "auto")

private fun CsvRecord.oneOf(k: String, allowed: Set<String>): String? =
    opt(k)?.also { if (it !in allowed) fail("$k \"$it\", allowed: ${allowed.joinToString()}") }

private fun CsvRecord.pos(k: String): Double? =
    num(k)?.also { if (it <= 0) fail("$k must be greater than 0") }

private fun CsvRecord.posInt(k: String): Int? =
    int(k)?.also { if (it <= 0) fail("$k must be greater than 0") }

fun CsvRecord.toEntry(): CsvEntry = when (type) {
    "soil" -> SoilEntry(line, req("name"), num("acidity_ph"), opt("components_description"))
    "room" -> RoomEntry(line, req("name"), opt("window_orientation"))
    "pot" -> PotEntry(
        line, req("name"), pos("empty_mass_grams"), opt("material"),
        num("diameter_cm"), num("volume_ml"), oneOf("pot_type", POT_TYPES),
    )
    "species" -> SpeciesEntry(
        line, req("name"), opt("family"), opt("common_name"), opt("soil"),
        num("dry_down_pct"), int("extra_days"), oneOf("target_pot_type", POT_TYPES),
        oneOf("watering_method", WATERING_METHODS), posInt("max_interval_days"),
    )
    "plant" -> PlantEntry(
        line, req("name"), opt("species"), opt("room"), opt("pot"), opt("notes"),
        ts("acquisition_date"), ts("archived_at"), pos("measurement_noise_grams"),
    )
    "measurement" -> MeasurementEntry(
        line, req("plant"), reqTs(),
        pos("weight_grams") ?: fail("empty field \"weight_grams\""), bool("excluded"),
    )
    "watering" -> WateringEntry(line, req("plant"), reqTs(), pos("water_volume_ml"))
    "removal" -> RemovalEntry(
        line, req("plant"), reqTs(), pos("removed_grams"), opt("event_type") ?: "pruning", opt("notes"),
    )
    "soil_addition" -> SoilAdditionEntry(
        line, req("plant"), reqTs(), pos("added_grams"), opt("soil"), opt("notes"),
    )
    "repotting" -> RepottingEntry(
        line, req("plant"), reqTs(), req("pot"), opt("soil"), opt("reason"),
    )
    else -> fail("unknown type \"$type\"")
}

/** Excel в укр. локали сохраняет CSV в windows-1251; если это не UTF-8, читаем как 1251. */
fun decodeCsv(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: CharacterCodingException) {
    String(bytes, charset("windows-1251"))
}

/**
 * Разбирает файл в типизированные записи. Бросает [CsvFormatException] с номером строки.
 * Ссылки на справочники здесь не проверяются: их разрешает импорт по базе,
 * поэтому файл может ссылаться и на то, что уже есть в приложении.
 */
fun parseBackup(text: String): List<CsvEntry> {
    val clean = text.removePrefix("\uFEFF") // BOM из Excel
    val firstLine = clean.lineSequence().first()
    val delimiter = if (';' in firstLine && ',' !in firstLine) ';' else ','
    val table = parseCsv(clean, delimiter)
    if (table.isEmpty()) csvBad("file is empty")

    val header = table.first().cells.map { it.trim().lowercase() }
    val records = table.drop(1).map { l ->
        val fields = header.indices.associate { header[it] to l.cells.getOrNull(it)?.trim().orEmpty() }
        CsvRecord(fields["type"].orEmpty().lowercase(), l.number, fields)
    }
    val unified = when {
        "type" in header -> records
        "plant" in header -> legacy(records)
        else -> csvBad("no \"type\" column (or \"plant\" for legacy format)")
    }
    return unified.map { it.toEntry() }
}

/** Старый формат plant,species,timestamp,weight_grams. */
private fun legacy(rows: List<CsvRecord>): List<CsvRecord> {
    val plants = linkedMapOf<String, CsvRecord>()
    val meas = mutableListOf<CsvRecord>()
    for (r in rows) {
        val plant = r.req("plant")
        plants.getOrPut(plant) {
            CsvRecord("plant", r.line, mapOf("name" to plant, "species" to r.str("species")))
        }
        val hasTs = r.str("timestamp").isNotEmpty()
        val hasWeight = r.str("weight_grams").isNotEmpty()
        if (hasTs != hasWeight) r.fail("date and weight must be specified together")
        if (hasTs) meas += CsvRecord(
            "measurement", r.line,
            mapOf("plant" to plant, "timestamp" to r.str("timestamp"), "weight_grams" to r.str("weight_grams")),
        )
    }
    // в старом формате виды не были отдельными строками
    val species = plants.values.mapNotNull { it.opt("species") }.distinct()
        .map { CsvRecord("species", 0, mapOf("name" to it)) }
    return species + plants.values + meas
}