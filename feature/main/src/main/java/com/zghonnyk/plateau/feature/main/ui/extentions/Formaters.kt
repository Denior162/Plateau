package com.zghonnyk.plateau.feature.main.ui.extentions

import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementWithRate
import com.zghonnyk.domain.Watering
import com.zghonnyk.plateau.feature.main.ui.state.MeasurementUiModel
import com.zghonnyk.plateau.feature.main.ui.state.MonthGroupUiModel
import com.zghonnyk.plateau.feature.main.ui.state.RecordUiModel
import com.zghonnyk.plateau.feature.main.ui.state.WateringUiModel
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun List<MeasurementWithRate>.toMonthGroupUiModels(
    zoneId: ZoneId
): List<MonthGroupUiModel> {
    val locale = Locale.getDefault()
    return this
        .map { item -> item to item.timestamp.atZone(zoneId) }
        .groupBy { (_, zdt) -> YearMonth.from(zdt) }
        .map { (month, items) ->
            MonthGroupUiModel(
                month = month,
                monthNameAndYearTitle = month.format(monthYearFormatter(locale))
                    .replaceFirstChar { it.titlecase(locale) },
                records = items.map { (item, zdt) ->
                    RecordUiModel(
                        id = item.id,
                        dayOfMonthAndTime = zdt.format(dayTimeFormatter(locale)),
                        weight = "%.0f g".format(locale, item.weightGrams),
                        delta = item.deltaGramsPerDay
                            ?.let { "%.1f g/d".format(locale, it) }
                            ?: "—",
                        timestamp = item.timestamp
                    )
                }
            )
        }
}

private fun  monthYearFormatter(locale: Locale) = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
private fun dayTimeFormatter(locale: Locale) = DateTimeFormatter.ofPattern("dd, HH:mm", locale)

fun MonthGroupUiModel.toClipboardText(): String = buildString {
    appendLine(monthNameAndYearTitle)
    records.forEach { appendLine("${it.dayOfMonthAndTime}: ${it.weight} (Δ: ${it.delta})") }
}

fun hoursBetween(from: Instant, to: Instant) = Duration.between(from, to).toHours()

fun Measurement.toUiModel(
    zoneId: ZoneId,
    locale: Locale = Locale.getDefault(),
) = MeasurementUiModel(
    id = id,
    formattedDate = formatHistoryDate(timestamp, zoneId, locale),
    formattedWeight = formatWeight(weightGrams, locale),
    timestamp = timestamp,
)

fun Watering.toUiModel(
    zoneId: ZoneId,
    locale: Locale = Locale.getDefault(),
) = WateringUiModel(
    id = id,
    formattedDate = formatHistoryDate(at, zoneId, locale),
    formattedVolume = formatVolume(volumeMl, locale),
    timestamp = at,
)

private const val EMPTY_VALUE = "—"

private fun formatWeight(grams: Double, locale: Locale) = "%.0f g".format(locale, grams)

private fun formatVolume(ml: Double?, locale: Locale) =
    ml?.let { "%.0f ml".format(locale, it) } ?: EMPTY_VALUE

private fun formatHistoryDate(at: Instant, zoneId: ZoneId, locale: Locale) =
    at.atZone(zoneId).format(dayTimeFormatter(locale))