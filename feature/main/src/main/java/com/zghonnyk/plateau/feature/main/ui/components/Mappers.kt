package com.zghonnyk.plateau.feature.main.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zghonnyk.domain.DryingConfig
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.PlantFacts
import com.zghonnyk.domain.daysUntilPlateauAt
import com.zghonnyk.domain.predictWeight
import com.zghonnyk.domain.withRates
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.extentions.hoursBetween
import com.zghonnyk.plateau.feature.main.ui.extentions.toMonthGroupUiModels
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import java.time.Instant
import java.time.ZoneId


@Composable
fun DryingForecast.statusText(): String = when (this) {
    is DryingForecast.InsufficientData -> stringResource(R.string.drying_insufficient_data)
    is DryingForecast.StillDrying -> stringResource(R.string.drying_still, currentRate)
    is DryingForecast.NearPlateau -> stringResource(R.string.drying_near_plateau, currentRate)
    is DryingForecast.ConstantRate -> stringResource(R.string.drying_constant_rate, rateGramsPerDay)
}

@Composable
fun DryingForecast.daysText(): String = when (this) {
    is DryingForecast.InsufficientData -> stringResource(R.string.days_awaiting)
    is DryingForecast.ConstantRate -> stringResource(R.string.days_plateau_not_visible)
    is DryingForecast.Active -> when (daysUntilPlateau) {
        0 -> stringResource(R.string.days_plateau_reached)
        else -> pluralStringResource(R.plurals.days_to_plateau, daysUntilPlateau, daysUntilPlateau)
    }
}

fun Measurement.toChartPoint() = ChartPoint(timestamp = timestamp.toEpochMilli(), grams = weightGrams)

fun PlantFacts.toUi(now: Instant, epsilon: Double, zoneId: ZoneId, config: DryingConfig) = PlantUi(
    id = id,
    name = name,
    latinName = latinName,
    records = activeMeasurements.withRates().toMonthGroupUiModels(zoneId),
    dryingForecast = dryingForecast,
    lastWeighedAt = lastWeighedAt,
    speciesId = speciesId,
    hasWatering = hasWatering,
    activeMeasurementPoints = activeMeasurements.map(Measurement::toChartPoint),
    activeMeasurements = activeMeasurements,
    hoursLeft = readyAt?.let { hoursBetween(now, it) },
    isReadyForAction = readyAt?.let { !it.isAfter(now) } ?: false,
    plateauInDays = (dryingForecast as? DryingForecast.Active)?.expFit?.daysUntilPlateauAt(
        now,
        epsilon
    ),
    moisture = moisture,
    prediction = predictWeight(history = activeMeasurements, at = now, config = config),
    timeline = timeline,
    plantPhoto = plantPhoto,
)