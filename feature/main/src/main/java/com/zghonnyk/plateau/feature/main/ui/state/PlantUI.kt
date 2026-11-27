package com.zghonnyk.plateau.feature.main.ui.state

import androidx.compose.runtime.Immutable
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.MoistureReading
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.Prediction
import com.zghonnyk.domain.TimelineEntry
import com.zghonnyk.domain.WateringEventId
import com.zghonnyk.plateau.feature.main.ui.components.ChartPoint
import java.time.Instant
import java.time.YearMonth


data class PlantUi(
    val id: PlantId,
    val name: String,
    val latinName: String,
    val records: List<MonthGroupUiModel>,
    val dryingForecast: DryingForecast?,
    val lastWeighedAt: Instant?,
    val speciesId: Long?,
    val hasWatering: Boolean,
    val activeMeasurementPoints: List<ChartPoint>,
    val activeMeasurements: List<Measurement>,
    val hoursLeft: Long?,
    val isReadyForAction: Boolean,
    val plateauInDays: Int?,
    val moisture: MoistureReading?,
    val prediction: Prediction?,
    val timeline: List<TimelineEntry>,
    val plantPhoto: String?
)

interface HistoryUIModel {
    val date: String
    val usefulData: String
}

@Immutable
data class MeasurementUiModel(
    val id: MeasurementId,
    val formattedDate: String,
    val formattedWeight: String,
    val timestamp: Instant,
) : HistoryUIModel {
    override val date: String get() = formattedDate
    override val usefulData: String get() = formattedWeight
}

@Immutable
data class WateringUiModel(
    val id: WateringEventId,
    val formattedDate: String,
    val formattedVolume: String,
    val timestamp: Instant,
) : HistoryUIModel {
    override val date: String get() = formattedDate
    override val usefulData: String get() = formattedVolume
}

@Immutable
data class MonthGroupUiModel(
    val month: YearMonth,
    val monthNameAndYearTitle: String,
    val records: List<RecordUiModel>
)

@Immutable
data class RecordUiModel(
    val id: MeasurementId,
    val dayOfMonthAndTime: String,
    val weight: String,
    val delta: String,
    val timestamp: Instant
)

