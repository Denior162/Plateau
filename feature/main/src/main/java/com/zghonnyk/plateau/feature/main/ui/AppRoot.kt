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
import com.zghonnyk.plateau.feature.main.ui.screen.HistoryWatersScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PlantScreen
import com.zghonnyk.plateau.feature.main.ui.screen.PotsScreen
import com.zghonnyk.plateau.feature.main.ui.screen.SpeciesScreen
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Creating.SelectSpecies
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Creating.ToggleDropdown
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.canGoBack

@Composable
fun BackAwareAppRoot(
    state: PlantState,
    pendingDelete: PendingDelete?,
    onIntent: (PlantIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(PlantIntent.Refresh) }
    BackHandler(enabled = state is PlantState.Active && state.canGoBack) {
        onIntent(PlantIntent.Back)
    }
    AppRoot(state, pendingDelete, onIntent, modifier)
}

@Composable
fun AppRoot(
    state: PlantState,
    pendingDelete: PendingDelete?,
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

    is PlantState.Active -> ActiveHost(state, pendingDelete, onIntent)
}

@Composable
private fun ActiveHost(
    state: PlantState.Active, pendingDelete: PendingDelete?, onIntent: (PlantIntent) -> Unit
) {
    val holder = rememberSaveableStateHolder()
    val screen = state.screen()
    holder.SaveableStateProvider(screen.key()) {
        when (screen) {
            is PlantState.Overview -> OverviewHost(
                screen.plants, state as? PlantState.EditingName, pendingDelete, onIntent
            )

            is PlantState.Creating -> AddPlantScreen(
                form = screen.form,
                onIntent = onIntent,
                onAddPhoto = { TODO() },
                onToggleDropdown = { expanded -> onIntent(ToggleDropdown(expanded)) },
                selectedSpeciesName = screen.speciesList.find { it.id == screen.form.selectedSpecies?.id }?.latinName,
                speciesList = screen.speciesList,
                onSelectSpecies = { speciesId ->
                    val species = screen.speciesList.find { it.id == speciesId }
                    onIntent(SelectSpecies(species))
                }
            )
            is PlantState.Species -> SpeciesScreen(screen.species) { onIntent(PlantIntent.Back) }
            is PlantState.Details.HistoryMeas -> HistoryMeasScreen(
                { onIntent(PlantIntent.Back) },
                screen.plant.name,
                screen.elements
            )

            is PlantState.Details.HistoryWaters -> HistoryWatersScreen(
                { onIntent(PlantIntent.Back) },
                screen.plant.name,
                screen.elements
            )

            is PlantState.Details.Plant -> PlantScreen(
                plant = screen, modal = state as? PlantState.PlantModal, onIntent = onIntent
            )

            is PlantState.Details.History -> TODO()
        }
    }
}

@Composable
private fun OverviewHost(
    plants: List<PlantUi>,
    editing: PlantState.EditingName?,
    pendingDelete: PendingDelete?,
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
        pendingDelete = pendingDelete,
        onUndoDelete = { onIntent(PlantIntent.Overview.Deletion.Undo) },
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
    is PlantState.Details.History -> TODO()
}