package com.zghonnyk.plateau.domain

import java.time.Instant
import java.time.format.DateTimeParseException

/** Ошибка формата файла; сообщение можно показывать пользователю как есть. */
class CsvFormatException(message: String) : IllegalArgumentException(message)

fun csvBad(message: String): Nothing = throw CsvFormatException(message)

val CSV_HEADER = listOf(
    "type", "name", "plant", "timestamp", "weight_grams", "excluded", "water_volume_ml",
    "pot", "soil", "species", "room", "family", "common_name", "dry_down_pct", "extra_days",
    "target_pot_type", "watering_method", "max_interval_days", "empty_mass_grams", "material",
    "diameter_cm", "volume_ml", "pot_type", "acidity_ph", "components_description",
    "window_orientation", "acquisition_date", "archived_at", "measurement_noise_grams",
    "removed_grams", "added_grams", "event_type", "reason", "notes",
)

/** Одна строка файла: тип + поля по именам колонок. Используется и экспортом, и разбором. */
class CsvRecord(val type: String, val line: Int, private val f: Map<String, String>) {
    fun str(k: String) = f[k].orEmpty()
    fun opt(k: String) = str(k).ifEmpty { null }
    fun req(k: String) = opt(k) ?: fail("пустое поле «$k»")

    fun num(k: String): Double? {
        val raw = str(k)
        if (raw.isEmpty()) return null
        return raw.replace(',', '.').toDoubleOrNull() ?: fail("неверное число $k «$raw»")
    }
    fun reqNum(k: String) = num(k) ?: fail("пустое поле «$k»")

    fun int(k: String): Int? {
        val d = num(k) ?: return null
        return if (d % 1.0 == 0.0) d.toInt() else fail("$k должно быть целым, а не «${str(k)}»")
    }

    fun ts(k: String = "timestamp"): Instant? {
        val raw = str(k)
        if (raw.isEmpty()) return null
        return try {
            raw.toLongOrNull()?.let(Instant::ofEpochMilli) ?: Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            fail("неверная дата «$raw»")
        }
    }
    fun reqTs(k: String = "timestamp") = ts(k) ?: fail("пустое поле «$k»")

    fun bool(k: String) = str(k) == "1" || str(k).equals("true", ignoreCase = true)

    fun fail(msg: String): Nothing = csvBad("строка $line: $msg")

    fun toCells(): List<String> = CSV_HEADER.map { if (it == "type") type else f[it].orEmpty() }
}

private fun String.csvEscape() =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + replace("\"", "\"\"") + "\"" else this

fun toCsv(records: List<CsvRecord>): String = buildString {
    append(CSV_HEADER.joinToString(",")).append("\r\n")
    for (r in records) {
        r.toCells().joinTo(this, ",") { it.csvEscape() }
        append("\r\n")
    }
}

/** Непустая строка таблицы с номером строки файла, с которой она начинается. */
class CsvLine(val number: Int, val cells: List<String>)

/** RFC 4180: кавычки, "" внутри кавычек, переводы строк внутри ячейки. Пустые строки пропускаются. */
fun parseCsv(text: String, delimiter: Char = ','): List<CsvLine> {
    val rows = mutableListOf<CsvLine>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var line = 1
    var rowLine = 1
    var i = 0

    fun endCell() { row += cell.toString(); cell.setLength(0) }
    fun endRow() {
        endCell()
        if (row.any { it.isNotBlank() }) rows += CsvLine(rowLine, row)
        row = mutableListOf()
    }

    while (i < text.length) {
        val c = text[i]
        if (c == '\n') line++
        when {
            inQuotes -> when {
                c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> cell.append(c)
            }
            c == '"' -> inQuotes = true
            c == delimiter -> endCell()
            c == '\n' -> { endRow(); rowLine = line }
            c == '\r' -> {}
            else -> cell.append(c)
        }
        i++
    }
    if (inQuotes) csvBad("не закрыта кавычка")
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}