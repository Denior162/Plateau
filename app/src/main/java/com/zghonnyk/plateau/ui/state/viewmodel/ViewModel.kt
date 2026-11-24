package com.zghonnyk.plateau.ui.state.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zghonnyk.domain.DryingConfig
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.daysUntilPlateauAt
import com.zghonnyk.domain.predictWeight
import com.zghonnyk.plateau.PlateauApplication
import com.zghonnyk.plateau.feature.main.ui.state.Cmd
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantFacts
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.applyPlants
import com.zghonnyk.plateau.feature.main.ui.state.update
import com.zghonnyk.plateau.feature.main.ui.state.updateDeletions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit

class PlantsViewModel(
    private val effects: PlantEffects,
    initialNow: Instant,
) : ViewModel() {
    private val _now = MutableStateFlow(initialNow)
    private val _uiState = MutableStateFlow<PlantState>(PlantState.DBUninitialized)
    val uiState: StateFlow<PlantState> = _uiState.asStateFlow()

    sealed interface UiText {
        data class DynamicString(val value: String) : UiText
        data class Resource(
            val resId: Int, val args: List<Any> = emptyList()
        ) : UiText

        fun asString(context: android.content.Context): String = when (this) {
            is DynamicString -> value
            is Resource -> context.getString(resId, *args.toTypedArray())
        }
    }

    sealed interface PlantEffect {
        data class ShowMessage(val text: UiText) : PlantEffect
        data class CopyToClipboard(val label: String, val text: String) : PlantEffect
    }

    private val _uiEffects = Channel<PlantEffect>(Channel.BUFFERED)
    val uiEffects = _uiEffects.receiveAsFlow()

    fun onIntent(intent: PlantIntent, now: Instant) {
        _now.value = now.truncatedTo(ChronoUnit.MINUTES)
        handleIntent(intent)
    }

    private val _pendingDeletes = MutableStateFlow<List<PendingDelete>>(emptyList())
    val pendingDeletes: StateFlow<List<PendingDelete>> = _pendingDeletes.asStateFlow()

    fun handleIntent(intent: PlantIntent) =
        if (intent is PlantIntent.Overview.Deletion) dispatchDeletion(intent) else dispatch(intent)

    private fun dispatchDeletion(intent: PlantIntent.Overview.Deletion) {
        val (pending, cmd) = updateDeletions(_pendingDeletes.value, intent, now().toEpochMilli())
        _pendingDeletes.value = pending
        run(cmd)
    }

    init {
        effects.attach(
            scope = viewModelScope,
            dispatch = ::handleIntent,
            emitEffect = { _uiEffects.trySend(it) }
        )
        viewModelScope.launch {
            combine(effects.observePlants(), _now) { facts, now ->
                facts.map {
                    it.withTime(now, DryingConfig().epsilon)
                }
            }.collect { uiPlants ->
                _uiState.update { current -> applyPlants(current, uiPlants) }
            }
        }
    }

    private fun PlantFacts.withTime(now: Instant, epsilon: Double) = PlantUi(
        id = id,
        name = name,
        latinName = latinName,
        records = records,
        dryingForecast = dryingForecast,
        lastWeighedAt = lastWeighedAt,
        speciesId = speciesId,
        hasWatering = hasWatering,
        activeMeasurementPoints = activeMeasurementPoints,
        activeMeasurements = activeMeasurements,
        hoursLeft = readyAt?.let { hoursBetween(now, it) },
        isReadyForAction = readyAt?.let { !it.isAfter(now) } ?: false,
        plateauInDays = (dryingForecast as? DryingForecast.Active)?.expFit?.daysUntilPlateauAt(
            now, epsilon
        ),
        moisture = moisture,
        prediction = predictWeight(history = activeMeasurements, at = now, DryingConfig()),
        timeline,
        plantPhoto
    )

    private fun hoursBetween(from: Instant, to: Instant) =
        java.time.Duration.between(from, to).toHours()

    private fun dispatch(intent: PlantIntent) {
        val s = _uiState.value as? PlantState.Active ?: return
        val (next, cmd) = update(s, intent, now())
        _uiState.value = next
        run(cmd)
    }

    private fun run(cmd: Cmd) {
        when (cmd) {
            Cmd.None -> Unit
            is Cmd.ExportCsv -> effects.exportCsv(cmd)
            is Cmd.ImportCsv -> effects.importCsv(cmd)
            is Cmd.SavePruning -> effects.savePruning(cmd)
            is Cmd.SaveRepotting -> effects.saveRepotting(cmd)
            is Cmd.SaveMeasurement -> effects.saveMeasurement(cmd)
            is Cmd.SaveWatering -> effects.saveWatering(cmd)
            is Cmd.SavePlant -> effects.savePlant(cmd)
            is Cmd.CopyPhoto -> effects.copyPhoto(cmd)
            is Cmd.DeletePhoto -> effects.deletePhoto(cmd)
            is Cmd.UpdatePlant -> effects.updatePlant(cmd)
            is Cmd.LoadEditingSpecies -> effects.loadEditingSpecies(cmd)
            is Cmd.LoadCreatingSpecies -> effects.loadCreatingSpecies(cmd)
            is Cmd.UpdateMeasurement -> effects.updateMeasurement(cmd)
            is Cmd.UpdateWatering -> effects.updateWatering(cmd)
            is Cmd.DeleteMeasurement -> effects.deleteMeasurement(cmd)
            is Cmd.DeleteWatering -> effects.deleteWatering(cmd)
            is Cmd.CopyToClipboard -> effects.copyToClipboard(cmd)
            is Cmd.StartDeleteTimer -> effects.startDeleteTimer(cmd)
            is Cmd.CancelDeleteTimer -> effects.cancelDeleteTimer(cmd)
            is Cmd.DeletePlant -> effects.deletePlant(cmd)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PlateauApplication
                PlantsViewModel(
                    effects = PlantEffectsImpl(
                        database = app.container.database,
                        deps = app.container.plantDeps,
                    ),
                    initialNow = now(),
                )
            }
        }
    }
}