package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.runtime.Composable
import com.zghonnyk.plateau.feature.main.ui.state.MeasurementUiModel

@Composable
fun HistoryMeasScreen(onBack: () -> Unit, screenName: String, elements: List<MeasurementUiModel>) =    HistoryContent(onBack, screenName, elements)