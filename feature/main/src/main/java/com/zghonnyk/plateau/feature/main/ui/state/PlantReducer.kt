package com.zghonnyk.plateau.feature.main.ui.state

import androidx.compose.foundation.text.input.TextFieldState
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.Details.History
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.Details.HistoryMeas
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.Details.HistoryWaters
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.Details.Plant
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.AddMeasurement
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.EditMeasurement
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.EditPlant
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.HistoryModal
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.LogWatering
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.Pruning
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.PlantModal.Repotting
import com.zghonnyk.vector_zipper.VectorZipper
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent as I
import com.zghonnyk.plateau.feature.main.ui.state.PlantState as S

/**
 * Дешёвая проверка для BackHandler: у Plant `parent` вычисляется (zipper.list),
 * поэтому не спрашиваем `parent != null`.
 * Инвариант (хорошее property-тест-место): canGoBack == (parent != null).
 */
val S.Active.canGoBack: Boolean get() = this !is S.Overview

fun reducePlantState(state: S.Active, intent: I): S.Active = when (intent) {
    I.Back -> state.parent ?: state
    is I.Loaded -> state.onLoaded(intent)

    I.Refresh, is I.ExportCsv, is I.ImportCsv, is I.Pruning.Save, is I.Repotting.Save -> state

    is I.Overview -> state.reduceIf<S.Overview> { reduce(intent) }
    is I.EditingName -> state.reduceIf<S.EditingName> { reduce(intent) }
    is I.AddingMeasurement -> state.reduceIf<AddMeasurement> { reduce(intent) }
    is I.Creating -> if (state is S.Creating) state.reduce(intent) else state
    is I.Details -> when (state) {
        is S.Details.Plant -> state.reduce(intent)
        is S.Details.History -> state.reduce(intent)
        else -> state
    }

    is I.LoggingWatering -> if (state is LogWatering) state.reduce(intent) else state


    is I.EditingPlant -> if (state is EditPlant) state.reduce(intent) else state
    is I.EditingMeasurement -> when (state) {
        is EditMeasurement -> state.reduce(intent)
        is HistoryModal.EditMeasurement -> state.reduce(intent)
        else -> state
    }

    is I.EditingWatering -> if (state is HistoryModal.EditWatering) state.reduce(intent) else state

    is I.Pruning -> if (state is Pruning) state.reduce(intent) else state
    is I.Repotting -> if (state is Repotting) state.reduce(intent) else state
}

private fun HistoryModal.EditMeasurement.reduce(i: I.EditingMeasurement): S.Active = when (i) {
    I.EditingMeasurement.Save -> parent
    I.EditingMeasurement.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.EditingMeasurement.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.EditingMeasurement.SetTimestamp -> copy(
        timestamp = i.timestamp,
        isDateTimePickerOpen = false
    )
}

inline fun <reified T : S.Active> S.Active.reduceIf(
    crossinline transform: T.() -> S.Active
): S.Active = when (this) {
    is T -> transform()
    else -> this
}

private fun S.PlantModal.Repotting.reduce(i: I.Repotting): S.Active = when (i) {
    I.Repotting.Save -> parent
    I.Repotting.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.Repotting.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.Repotting.SetRepottingTimestamp ->
        copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false)
}

private fun Pruning.reduce(i: I.Pruning): S.Active = when (i) {
    I.Pruning.Save -> parent
    I.Pruning.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.Pruning.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.Pruning.SetPruningTimestamp ->
        copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false)
}

private fun S.Details.History.reduce(i: I.Details): S.Active = when (i) {
    is I.Details.StartEditingMeasurement -> HistoryModal.EditMeasurement(
        parent = this,
        measurementId = i.id,
        weightInput = TextFieldState(i.currentWeightString),
        originalTimestamp = i.timestamp,
        timestamp = i.timestamp,
    )

    is I.Details.StartEditingWatering -> HistoryModal.EditWatering(
        parent = this,
        wateringId = i.wateringEventId,
        volumeInput = TextFieldState(i.currentMl),
        originalTimestamp = i.timestamp,
        timestamp = i.timestamp,
    )

    else -> this
}

private fun S.Overview.reduce(i: I.Overview): S.Active = when (i) {
    I.Overview.OpenAddPlant -> S.Creating(
        parent = this,
        speciesList = emptyList()
    )

    is I.Overview.SelectPlant ->
        plants.indexOfFirst { it.id == i.plantId }
            .takeIf { it != -1 }
            ?.let { Plant(VectorZipper(it, plants)) }
            ?: this

    is I.Overview.StartEditingPlantName ->
        plants.find { it.id == i.plantId }?.let { plant ->
            S.EditingName(
                parent = this,
                plantId = plant.id,
                nameInput = TextFieldState(plant.name),
                selectedSpeciesId = plant.speciesId,
                isDropdownExpanded = false,
                speciesList = emptyList(),
            )
        } ?: this

    is I.Overview.Deletion -> this
}

private fun S.EditingName.reduce(i: I.EditingName): S.Active = when (i) {
    I.EditingName.Save -> parent
    is I.EditingName.ToggleDropdown -> copy(isDropdownExpanded = i.expanded)
    is I.EditingName.SelectSpecies -> copy(
        selectedSpeciesId = i.speciesId,
        isDropdownExpanded = false
    )
}

private fun S.Creating.reduce(i: I.Creating): S.Active = when (i) {
    I.Creating.Save -> parent
    is I.Creating.ToggleDropdown -> copy(form = form.copy(isDropdownExpanded = i.expanded))
    is I.Creating.SelectSpecies -> copy(
        form = form.copy(
            speciesSelection = i.selection,
            isDropdownExpanded = false
        )
    )

    I.Creating.UnknownSpecies -> copy(
        form = form.copy(
            speciesSelection = SpeciesSelection.Unknown,
            isDropdownExpanded = false
        )
    )

    is I.Creating.AddPhoto -> this
    is I.Creating.AddPhotoCopied -> copy(form = form.copy(photoPath = i.relativePath))
}

private fun S.Details.Plant.reduce(i: I.Details): S.Active = when (i) {
    I.Details.GoLeft -> copy(zipper = zipper.goBack())
    I.Details.GoRight -> copy(zipper = zipper.goForward())

    I.Details.OpenAddMeasurement -> AddMeasurement(parent = this)
    I.Details.OpenWateringDialog -> LogWatering(parent = this)
    I.Details.OpenPruning -> Pruning(parent = this)
    I.Details.OpenRepottingDialog -> Repotting(parent = this)

    I.Details.StartEditingPlant -> EditPlant(
        parent = this,
        plantId = focus.id,
        nameInput = TextFieldState(focus.name),
        selectedSpeciesId = focus.speciesId,
    )

    is I.Details.StartEditingMeasurement -> HistoryModal.EditMeasurement(
        parent = S.Details.History(this),
        measurementId = i.id,
        weightInput = TextFieldState(i.currentWeightString),
        originalTimestamp = i.timestamp,
        timestamp = i.timestamp,
    )

    is I.Details.StartEditingWatering,
    is I.Details.CopyMonth,
    is I.Details.DeleteMeasurement,
    is I.Details.DeleteWatering -> this

    I.Details.OpenMeasurementHistory,
    I.Details.OpenWateringHistory -> S.Details.History(parent = this)
}

private fun AddMeasurement.reduce(i: I.AddingMeasurement): S.Active = when (i) {
    I.AddingMeasurement.Save -> parent
    I.AddingMeasurement.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.AddingMeasurement.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.AddingMeasurement.SetMeasurementTimestamp ->
        copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false)
}

private fun LogWatering.reduce(i: I.LoggingWatering): S.Active = when (i) {
    I.LoggingWatering.Save -> parent
    I.LoggingWatering.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.LoggingWatering.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.LoggingWatering.SetWateringTimestamp ->
        copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false)
}

private fun EditPlant.reduce(i: I.EditingPlant): S.Active = when (i) {
    I.EditingPlant.Save -> parent
    is I.EditingPlant.ToggleDropdown -> copy(isDropdownExpanded = i.expanded)
    is I.EditingPlant.SelectSpecies -> copy(
        selectedSpeciesId = i.speciesId,
        isDropdownExpanded = false
    )
}

private fun EditMeasurement.reduce(i: I.EditingMeasurement): S.Active = when (i) {
    I.EditingMeasurement.Save -> parent
    I.EditingMeasurement.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.EditingMeasurement.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.EditingMeasurement.SetTimestamp -> copy(
        timestamp = i.timestamp,
        isDateTimePickerOpen = false
    )
}

private fun HistoryModal.EditWatering.reduce(i: I.EditingWatering): S.Active = when (i) {
    I.EditingWatering.Save -> parent
    I.EditingWatering.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.EditingWatering.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.EditingWatering.SetTimestamp -> copy(timestamp = i.timestamp, isDateTimePickerOpen = false)
}


private fun S.Active.onLoaded(m: I.Loaded): S.Active = when (m) {
    is I.Loaded.Species ->
        (this as? S.Overview)?.let { S.Species(parent = it, species = m.species) } ?: this

    is I.Loaded.EditingSpecies ->
        (this as? S.EditingName)?.copy(speciesList = m.species) ?: this

    is I.Loaded.CreatingSpecies ->
        (this as? S.Creating)?.copy(speciesList = m.species) ?: this

    is I.Loaded.MeasurementHistory ->
        (this as? S.Details.Plant)
            ?.takeIf { it.focus.id == m.plantId }
            ?.let { S.Details.HistoryMeas(parent = it, elements = m.elements) }
            ?: this

    is I.Loaded.WateringHistory ->
        (this as? S.Details.Plant)
            ?.takeIf { it.focus.id == m.plantId }
            ?.let { S.Details.HistoryWaters(parent = it, elements = m.elements) }
            ?: this
}

// ───────────── свежий список растений (подписка на БД) ─────────────

/**
 * Чистая функция: ViewModel зовёт её из подписки на БД.
 * Список протаскивается по всей цепочке предков — цена «родитель в типе».
 */
fun applyPlants(current: S, plants: List<PlantUi>): S = when (current) {
    S.DBUninitialized -> S.Overview(plants)
    is S.Active -> current.withPlants(plants)
}

private fun S.Active.withPlants(plants: List<PlantUi>): S.Active {
    if (plants.isEmpty()) {
        return if (this is S.Creating) copy(parent = S.Overview(emptyList())) else S.Overview(
            emptyList()
        )
    }
    return when (this) {
        is S.Overview -> copy(plants = plants)

        is S.EditingName ->
            if (plants.none { it.id == plantId }) S.Overview(plants)
            else copy(parent = parent.copy(plants = plants))

        is S.Creating -> copy(parent = parent.copy(plants = plants))
        is S.Species -> copy(parent = parent.copy(plants = plants))

        is Plant -> refocused(plants)
        is HistoryMeas -> parent.rebased(plants) { copy(parent = it) }
        is HistoryWaters -> parent.rebased(plants) { copy(parent = it) }

        is AddMeasurement -> parent.rebased(plants) { copy(parent = it) }
        is LogWatering -> parent.rebased(plants) { copy(parent = it) }
        is EditMeasurement -> parent.rebased(plants) { copy(parent = it) }
        is EditPlant -> parent.rebased(plants) { copy(parent = it) }
        is Pruning -> parent.rebased(plants) { copy(parent = it) }
        is Repotting -> parent.rebased(plants) { copy(parent = it) }
        is History -> parent.rebased(plants) { copy(parent = it) }
        is HistoryModal -> parent.parent.rebased(plants) { rebasedPlant ->
            val newHistory = History(rebasedPlant)
            when (this) {
                is HistoryModal.EditMeasurement -> copy(parent = newHistory)
                is HistoryModal.EditWatering -> copy(parent = newHistory)
            }
        }
    }
}

private fun S.Details.Plant.refocused(plants: List<PlantUi>): S.Details.Plant {
    val i = plants.indexOfFirst { it.id == focus.id }
        .takeIf { it != -1 }
        ?: zipper.index.coerceAtMost(plants.lastIndex)
    return S.Details.Plant(VectorZipper(i, plants))
}

private inline fun S.Details.Plant.rebased(
    plants: List<PlantUi>,
    build: (S.Details.Plant) -> S.Active,
): S.Active {
    val next = refocused(plants)
    return if (plants.any { it.id == focus.id }) build(next) else next
}