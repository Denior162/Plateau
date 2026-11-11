package com.zghonnyk.plateau.feature.main.ui.extentions

import com.zghonnyk.domain.MeasurementWithRate
import com.zghonnyk.plateau.feature.main.ui.state.MonthGroupUiModel
import com.zghonnyk.plateau.feature.main.ui.state.RecordUiModel
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun List<MeasurementWithRate>.toMonthGroupUiModels(
    zoneId: ZoneId = ZoneId.systemDefault()
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
                            ?: "—"
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