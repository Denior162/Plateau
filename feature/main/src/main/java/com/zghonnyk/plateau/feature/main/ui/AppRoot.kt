package com.zghonnyk.plateau.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zghonnyk.plateau.feature.main.ui.screen.AddPlantScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryMeasScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryScreen
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryWatersScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PlantScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PotsScreen
import com.zghonnyk.plateau.feature.main.ui.screen.SpeciesScreen
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Creating.SelectSpecies
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Creating.ToggleDropdown
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Details.DeleteMeasurement
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Details.DeleteWatering
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Details.StartEditingMeasurement
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Details.StartEditingWatering
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.canGoBack

@Composable
fun BackAwareAppRoot(
    state: PlantState,
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
        pendingDeletes = pendingDeletes,
        onIntent = onIntent,
        modifier = modifier,
    )
}

@Composable
fun AppRoot(
    state: PlantState,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
    modifier: Modifier = Modifier,
) = when (state) {
    is PlantState.DBUninitialized -> {
        Surface {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    is PlantState.Active -> ActiveHost(
        state = state,
        pendingDeletes = pendingDeletes,
        onIntent = onIntent,
    )
}

@Composable
private fun ActiveHost(
    state: PlantState.Active,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit
) {
    val holder = rememberSaveableStateHolder()
    val screen = state.screen()
    holder.SaveableStateProvider(screen.key()) {
        when (screen) {
            is PlantState.Overview -> OverviewHost(
                screen.plants, state as? PlantState.EditingName, pendingDeletes, onIntent
            )

            is PlantState.Creating -> AddPlantScreen(
                form = screen.form,
                onBack = { onIntent(PlantIntent.Back) },
                onAddPhoto = { uri -> onIntent(PlantIntent.Creating.AddPhoto(uri)) },
                onToggleDropdown = { expanded -> onIntent(ToggleDropdown(expanded)) },
                speciesList = screen.speciesList,
                onSelectionChange = { selection -> onIntent(SelectSpecies(selection)) },
                onUnknownSpecies = { onIntent(PlantIntent.Creating.UnknownSpecies) },
                onSave = { onIntent(PlantIntent.Creating.Save) }
            )

            is PlantState.Species -> SpeciesScreen(screen.species) { onIntent(PlantIntent.Back) }
            is PlantState.Details.HistoryMeas -> HistoryMeasScreen(
                onBack = { onIntent(PlantIntent.Back) },
                screenName = screen.plant.name,
                elements = screen.elements,
                onEdit = { item ->
                    onIntent(
                        StartEditingMeasurement(
                            item.id, item.formattedWeight, item.timestamp
                        )
                    )
                },
                onDelete = { item -> onIntent(DeleteMeasurement(item.id)) })

            is PlantState.Details.HistoryWaters -> HistoryWatersScreen(
                onBack = { onIntent(PlantIntent.Back) },
                screenName = screen.plant.name,
                elements = screen.elements,
                onEdit = { item ->
                    onIntent(
                        StartEditingWatering(
                            item.id, item.formattedVolume, item.timestamp
                        )
                    )
                },
                onDelete = { item -> onIntent(DeleteWatering(item.id)) })

            is PlantState.Details.Plant -> PlantScreen(
                plant = screen, modal = state as? PlantState.PlantModal, onIntent = onIntent
            )

            is PlantState.Details.History -> HistoryScreen(
                plant = screen.plant,
                modal = state as? PlantState.PlantModal.HistoryModal,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun OverviewHost(
    plants: List<PlantUi>,
    editing: PlantState.EditingName?,
    pendingDeletes: List<PendingDelete>,
    onIntent: (PlantIntent) -> Unit,
) {
    PotsScreen(
        plants = plants,
        editingPlant = editing,
        onSelect = { id -> onIntent(PlantIntent.Overview.SelectPlant(id)) },
        onEdit = { id -> onIntent(PlantIntent.Overview.StartEditingPlantName(id)) },
        onSave = { onIntent(PlantIntent.EditingName.Save) },
        onCancelEdit = { onIntent(PlantIntent.Back) },
        onAddPlantClick = { onIntent(PlantIntent.Overview.OpenAddPlant) },
        onDelete = { id -> onIntent(PlantIntent.Overview.Deletion.Request(id)) },
        onToggleDropdown = { expanded -> onIntent(PlantIntent.EditingName.ToggleDropdown(expanded)) },
        onSelectSpecies = { id -> onIntent(PlantIntent.EditingName.SelectSpecies(id)) },
        onExport = { uri -> onIntent(PlantIntent.ExportCsv(uri)) },
        onImport = { uri -> onIntent(PlantIntent.ImportCsv(uri)) },
        pendingDeletes = pendingDeletes,
        onUndoDelete = { id -> onIntent(PlantIntent.Overview.Deletion.Undo(id)) },
    )
}

private fun PlantState.Active.screen(): PlantState.Screen = when (this) {
    is PlantState.Screen -> this
    is PlantState.Modal -> parent
}

private fun PlantState.Screen.key(): Any = when (this) {
    is PlantState.Details.Plant -> "plant:${focus.id.value}"
    is PlantState.Details.HistoryMeas -> "histM:${plant.id.value}"
    is PlantState.Details.HistoryWaters -> "histW:${plant.id.value}"
    is PlantState.Overview -> "overview"
    is PlantState.Creating -> "creating"
    is PlantState.Species -> "species"
    is PlantState.Details.History -> "hist:${plant.id.value}"
}