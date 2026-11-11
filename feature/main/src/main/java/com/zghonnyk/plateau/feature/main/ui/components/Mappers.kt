package com.zghonnyk.plateau.feature.main.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.plateau.feature.main.R


@Composable
fun DryingForecast.statusText(): String = when (this) {
    is DryingForecast.InsufficientData -> stringResource(R.string.drying_insufficient_data)
    is DryingForecast.StillDrying -> stringResource(R.string.drying_still, currentRate)
    is DryingForecast.NearPlateau -> stringResource(R.string.drying_near_plateau, currentRate)
}

@Composable
fun DryingForecast.daysText(): String = when (this) {
    is DryingForecast.InsufficientData -> stringResource(R.string.days_awaiting)
    is DryingForecast.Active -> when (daysUntilPlateau) {
        0 -> stringResource(R.string.days_plateau_reached)
        else -> pluralStringResource(R.plurals.days_to_plateau, daysUntilPlateau, daysUntilPlateau)
    }
}
