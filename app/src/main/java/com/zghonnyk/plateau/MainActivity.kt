package com.zghonnyk.plateau

import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zghonnyk.domain.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.BackAwareAppRoot
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel
import com.zghonnyk.ui.theme.PlateauTheme
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val viewModel: PlantsViewModel by viewModels { PlantsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { viewModel.uiState.value is PlantState.DBUninitialized }
        setContent {
            PlateauTheme(isSystemInDarkTheme()) {
                PlantEffects(viewModel, this@MainActivity)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val pendingDeletes by viewModel.pendingDeletes.collectAsStateWithLifecycle()
                BackAwareAppRoot(
                    state = state,
                    pendingDeletes = pendingDeletes,
                    onIntent = { viewModel.onIntent(it, Instant.now()) },
                    uiPlants = viewModel.uiPlants.collectAsState().value)
            }
        }
    }
}

@Composable
private fun PlantEffects(viewModel: PlantsViewModel, context: Context) {
    val clipboard = LocalClipboard.current
    LaunchedEffect(viewModel) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is PlantsViewModel.PlantEffect.CopyToClipboard -> clipboard.setClipEntry(
                    ClipData.newPlainText(effect.label, effect.text).toClipEntry()
                )
                is PlantsViewModel.PlantEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.text.asString(context),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}