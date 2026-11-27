package com.zghonnyk.plateau.feature.main.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zghonnyk.domain.PhotoSource
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.state.PendingDelete
import com.zghonnyk.domain.state.PlantIntent
import com.zghonnyk.domain.state.PlantIntent.Creating.SelectSpecies
import com.zghonnyk.domain.state.PlantIntent.Creating.ToggleDropdown
import com.zghonnyk.domain.state.PlantIntent.Details.DeleteMeasurement
import com.zghonnyk.domain.state.PlantIntent.Details.DeleteWatering
import com.zghonnyk.domain.state.PlantIntent.Details.StartEditingMeasurement
import com.zghonnyk.domain.state.PlantIntent.Details.StartEditingWatering
import com.zghonnyk.domain.state.PlantState
import com.zghonnyk.domain.state.canGoBack
import com.zghonnyk.plateau.feature.main.ui.extentions.toUiModel
import com.zghonnyk.plateau.feature.main.ui.screen.AddPlantScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryMeasScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryWatersScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PlantScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PotsScreen
import com.zghonnyk.plateau.feature.main.ui.screen.SpeciesScreen
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import java.time.ZoneId


@Composable
fun BackAwareAppRoot(
    state: PlantState,
    uiPlants: Map<PlantId, PlantUi>,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(PlantIntent.Refresh) }
    BackHandler(enabled = state is PlantState.Active && state.canGoBack) {
        onIntent(PlantIntent.Back)
    }
    AppRoot(
        state = state,
        uiPlants = uiPlants,
        pendingDeletes = pendingDeletes,
        onIntent = onIntent,
        modifier = modifier,
    )
}

@Composable
fun AppRoot(
    state: PlantState,
    uiPlants: Map<PlantId, PlantUi>,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
    modifier: Modifier = Modifier,
) = when (state) {
    is PlantState.DBUninitialized -> LoadingScreen(modifier)

    is PlantState.Active -> ActiveHost(
        state = state,
        uiPlants = uiPlants,
        pendingDeletes = pendingDeletes,
        onIntent = onIntent,
    )
}

@Composable
private fun LoadingScreen(modifier: Modifier) = Surface {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

private fun Uri.toPhotoSource() = PhotoSource(toString())

@Composable
private fun ActiveHost(
    state: PlantState.Active,
    uiPlants: Map<PlantId, PlantUi>,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val screen = state.screen()
    val content: @Composable () -> Unit = {
        when (screen) {
            is PlantState.Overview -> OverviewHost(
                plants = remember(screen.plants, uiPlants) {
                    screen.plants.mapNotNull { uiPlants[it.id] }
                },
                editing = state as? PlantState.EditingName,
                pendingDeletes = pendingDeletes,
                onIntent = onIntent,
            )

            is PlantState.Creating -> AddPlantScreen(
                form = screen.form,
                onBack = { onIntent(PlantIntent.Back) },
                onAddPhoto = { uri -> onIntent(PlantIntent.Creating.AddPhoto(uri.toPhotoSource())) },
                onToggleDropdown = { expanded -> onIntent(ToggleDropdown(expanded)) },
                speciesList = screen.speciesList,
                onSelectionChange = { selection -> onIntent(SelectSpecies(selection)) },
                onUnknownSpecies = { onIntent(PlantIntent.Creating.UnknownSpecies) },
                onSave = { name -> onIntent(PlantIntent.Creating.Save(name)) },
            )

            is PlantState.Species -> SpeciesScreen(screen.species) { onIntent(PlantIntent.Back) }

            is PlantState.Details.HistoryMeas -> {
                val zoneId = remember { ZoneId.systemDefault() }
                val elements = remember(screen.elements) { screen.elements.map { it.toUiModel(zoneId) } }
                HistoryMeasScreen(
                    onBack = { onIntent(PlantIntent.Back) },
                    screenName = screen.parent.focus.name,
                    elements = elements,
                    onEdit = { item ->
                        onIntent(StartEditingMeasurement(item.id, item.formattedWeight, item.timestamp))
                    },
                    onDelete = { item -> onIntent(DeleteMeasurement(item.id)) },
                )
            }

            is PlantState.Details.HistoryWaters -> {
                val zoneId = remember { ZoneId.systemDefault() }
                val elements = remember(screen.elements) { screen.elements.map { it.toUiModel(zoneId) } }
                HistoryWatersScreen(
                    onBack = { onIntent(PlantIntent.Back) },
                    screenName = screen.parent.focus.name,
                    elements = elements,
                    onEdit = { item ->
                        onIntent(StartEditingWatering(item.id, item.formattedVolume, item.timestamp))
                    },
                    onDelete = { item -> onIntent(DeleteWatering(item.id)) },
                )
            }

            is PlantState.Details.History -> uiPlants[screen.plant.id]?.let { plant ->
                HistoryScreen(
                    plant = plant,
                    modal = state as? PlantState.PlantModal.HistoryModal,
                    onIntent = onIntent,
                )
            }

            is PlantState.Details.Plant -> PlantScreen(
                plant = screen,
                uiPlants = uiPlants,
                modal = state as? PlantState.PlantModal,
                onIntent = onIntent,
            )
        }
    }

    screen.saveableKey()
        ?.let { holder.SaveableStateProvider(it, content) }
        ?: content()
}
@Composable
private fun OverviewHost(
    plants: List<PlantUi>,
    editing: PlantState.EditingName?,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
) = PotsScreen(
    plants = plants,
    editingPlant = editing,
    onSelect = { id -> onIntent(PlantIntent.Overview.SelectPlant(id)) },
    onEdit = { id -> onIntent(PlantIntent.Overview.StartEditingPlantName(id)) },
    onSave = { name -> onIntent(PlantIntent.EditingName.Save(name)) },
    onCancelEdit = { onIntent(PlantIntent.Back) },
    onAddPlantClick = { onIntent(PlantIntent.Overview.OpenAddPlant) },
    onDelete = { id -> onIntent(PlantIntent.Overview.Deletion.Request(id)) },
    onToggleDropdown = { expanded -> onIntent(PlantIntent.EditingName.ToggleDropdown(expanded)) },
    onSelectSpecies = { id -> onIntent(PlantIntent.EditingName.SelectSpecies(id)) },
    onExport = { uri -> onIntent(PlantIntent.ExportCsv(uri.toPhotoSource())) },
    onImport = { uri -> onIntent(PlantIntent.ImportCsv(uri.toPhotoSource())) },
    pendingDeletes = pendingDeletes,
    onUndoDelete = { id -> onIntent(PlantIntent.Overview.Deletion.Undo(id)) },
    onAddPhoto = { id, uri -> onIntent(PlantIntent.Overview.AddPhoto(id, uri.toPhotoSource())) },
)

private fun PlantState.Active.screen(): PlantState.Screen = when (this) {
    is PlantState.Screen -> this
    is PlantState.Modal -> parent
}

private fun PlantState.Screen.saveableKey(): Any? = when (this) {
    is PlantState.Details.Plant -> null
    is PlantState.Details.HistoryMeas -> "histM:${parent.focus.id.value}"
    is PlantState.Details.HistoryWaters -> "histW:${parent.focus.id.value}"
    is PlantState.Overview -> "overview"
    is PlantState.Creating -> "creating"
    is PlantState.Species -> "species"
    is PlantState.Details.History -> "hist:${plant.id.value}"
}