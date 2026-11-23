package com.zghonnyk.plateau

import android.content.ClipData
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zghonnyk.plateau.feature.main.ui.BackAwareAppRoot
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel
import com.zghonnyk.ui.theme.PlateauTheme
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val viewModel: PlantsViewModel by viewModels { PlantsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlateauTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
                val clipboard = LocalClipboard.current
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is PlantsViewModel.PlantEffect.CopyToClipboard -> clipboard.setClipEntry(
                                ClipData.newPlainText(effect.label, effect.text).toClipEntry()
                            )

                            is PlantsViewModel.PlantEffect.ShowMessage -> {}
                        }
                    }
                }

                val send = remember { { intent: PlantIntent -> viewModel.onIntent(intent, Instant.now()) } }
                BackAwareAppRoot(
                    median = viewModel.getGlobalMedian(),
                    speciesStatsFlow = viewModel.getSpeciesMeasurementStats(),
                    speciesList = viewModel.getSpeciesList(),
                    onGetEvaporatedForSpecies = { viewModel.getTotalEvaporatedBySpecies(it) },
                    onGetPlantsWithMinWeight = { viewModel.getPlantsWithMinPeakWeight(it) },
                    state = state,
                    pendingDelete = pendingDelete,
                    onIntent = send,
                    onGetMonthlyStats = { lng, lng1 -> viewModel.getMonthlyStats() }
                )
            }
        }
    }
}