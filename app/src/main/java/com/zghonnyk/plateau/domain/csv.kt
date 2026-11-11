package com.zghonnyk.plateau.domain

import java.time.Instant
import java.time.format.DateTimeParseException

data class CsvRow(
    val plant: String,
    val species: String?,
    val timestamp: Instant?,
    val weightGrams: Double?,
)

private const val HEADER = "plant,species,timestamp,weight_grams"

private fun String.csvEscape(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"" + replace("\"", "\"\"") + "\""
    else this

fun toCsv(rows: List<CsvRow>): String = buildString {
    append(HEADER).append("\r\n")
    for (r in rows) {
        listOf(
            r.plant,
            r.species.orEmpty(),
            r.timestamp?.toString().orEmpty(),
            r.weightGrams?.toString().orEmpty(),
        ).joinTo(this, ",") { it.csvEscape() }
        append("\r\n")
    }
}

/** RFC 4180: кавычки, "" внутри кавычек, переводы строк внутри ячейки. */
fun parseCsv(text: String, delimiter: Char = ','): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0

    fun endCell() { row += cell.toString(); cell.setLength(0) }
    fun endRow() { endCell(); rows += row.toList(); row.clear() }

    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when (c) {
                '"' if text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                '"' -> inQuotes = false
                else -> cell.append(c)
            }
            c == '"' -> inQuotes = true
            c == delimiter -> endCell()
            c == '\n' -> endRow()
            c == '\r' -> {}
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows.filter { r -> r.any { it.isNotBlank() } }
}

/** Бросает IllegalArgumentException с номером строки, если файл некорректен. */
fun parseCsvRows(text: String): List<CsvRow> {
    val clean = text.removePrefix("\uFEFF")                 // BOM из Excel
    val firstLine = clean.lineSequence().first()
    val delimiter = if (';' in firstLine && ',' !in firstLine) ';' else ','  // Excel в укр. локали
    val table = parseCsv(clean, delimiter)
    require(table.isNotEmpty()) { "файл пустой" }

    val header = table.first().map { it.trim().lowercase() }
    fun col(name: String) = header.indexOf(name).also { require(it >= 0) { "нет колонки «$name»" } }
    val iPlant = col("plant")
    val iSpecies = col("species")
    val iTs = col("timestamp")
    val iWeight = col("weight_grams")

    return table.drop(1).mapIndexed { n, r ->
        val line = n + 2
        fun cell(i: Int) = r.getOrNull(i)?.trim().orEmpty()

        val plant = cell(iPlant)
        require(plant.isNotEmpty()) { "строка $line: пустое имя растения" }

        val tsRaw = cell(iTs)
        val ts = try {
            if (tsRaw.isEmpty()) null
            else tsRaw.toLongOrNull()?.let(Instant::ofEpochMilli) ?: Instant.parse(tsRaw)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("строка $line: неверная дата «$tsRaw»")
        }

        val wRaw = cell(iWeight)
        val weight = if (wRaw.isEmpty()) null else
            wRaw.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("строка $line: неверный вес «$wRaw»")

        require((ts == null) == (weight == null)) { "строка $line: дата и вес должны быть заданы вместе" }

        CsvRow(plant, cell(iSpecies).ifEmpty { null }, ts, weight)
    }
}