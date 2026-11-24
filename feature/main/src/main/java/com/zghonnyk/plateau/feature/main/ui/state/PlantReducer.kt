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
import java.time.Instant
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent as I
import com.zghonnyk.plateau.feature.main.ui.state.PlantState as S
val S.Active.canGoBack: Boolean get() = this !is S.Overview

fun reducePlantState(state: S.Active, intent: I): S.Active = when (intent) {
    I.Back, I.Refresh, is I.Loaded, is I.ExportCsv, is I.ImportCsv,
    is I.Pruning, is I.Repotting, is I.AddingMeasurement, is I.EditingPlant -> state

    is I.Overview -> state.reduceIf<S.Overview> { reduce(intent) }
    is I.EditingName -> state.reduceIf<S.EditingName> { reduce(intent) }
    is I.Creating -> if (state is S.Creating) state.reduce(intent) else state
    is I.Details -> when (state) {
        is Plant -> state.reduce(intent)
        is History -> state.reduce(intent)
        else -> state
    }
    is I.LoggingWatering -> if (state is LogWatering) state.reduce(intent) else state
    is I.EditingMeasurement -> when (state) {
        is EditMeasurement -> state.reduce(intent)
        is HistoryModal.EditMeasurement -> state.reduce(intent)
        else -> state
    }
    is I.EditingWatering -> if (state is HistoryModal.EditWatering) state.reduce(intent) else state
}

fun update(state: S.Active, intent: I, now: Instant): Next = when (intent) {
    I.Back -> Next.just(state.parent ?: state)
    I.Refresh -> Next.just(state)
    is I.ExportCsv -> Next(state, Cmd.ExportCsv(intent.uri))
    is I.ImportCsv -> Next(state, Cmd.ImportCsv(intent.uri))
    is I.Loaded -> Next.just(state.onLoaded(intent))
    is I.Pruning -> updatePruning(state, intent, now)
    is I.Repotting -> updateRepotting(state, intent, now)
    is I.AddingMeasurement -> updateAddingMeasurement(state, intent, now)
    is I.Creating -> updateCreating(state, intent, now)
    is I.LoggingWatering -> updateLoggingWatering(state, intent, now)
    is I.EditingPlant -> updateEditingPlant(state, intent)
    is I.Overview,
    is I.Details,
    is I.EditingName,
    is I.EditingMeasurement,
    is I.EditingWatering -> Next.just(reducePlantState(state, intent))
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
private val WEIGHT_REGEX = Regex("""^\d+([.,]\d+)?$""")

fun String.toPositiveDoubleOrNull(): Double? {
    val s = trim()
    if (!WEIGHT_REGEX.matches(s)) return null
    return s.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
}
private fun parseOptionalWeight(raw: String): Result<Double?> =
    if (raw.isBlank()) Result.success(null)
    else raw.toPositiveDoubleOrNull()?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException())

private fun updateEditingPlant(state: S.Active, i: I.EditingPlant): Next {
    val s = state as? EditPlant ?: return Next.just(state)
    return when (i) {
        I.EditingPlant.Save -> {
            val name = s.nameInput.text.toString().trim()
            if (name.isEmpty()) return Next.just(s)
            Next(s.parent, Cmd.UpdatePlant(s.plantId, name, s.selectedSpeciesId))
        }
        is I.EditingPlant.ToggleDropdown ->
            Next.just(s.copy(isDropdownExpanded = i.expanded))
        is I.EditingPlant.SelectSpecies ->
            Next.just(s.copy(selectedSpeciesId = i.speciesId, isDropdownExpanded = false))
    }
}
private fun updateAddingMeasurement(state: S.Active, i: I.AddingMeasurement, now: Instant): Next {
    val s = state as? AddMeasurement ?: return Next.just(state)
    return when (i) {
        I.AddingMeasurement.Save -> {
            val weight = s.weightInput.text.toString().toPositiveDoubleOrNull()
                ?: return Next.just(s)
            Next(
                s.parent,
                Cmd.SaveMeasurement(
                    plantId = s.parent.focus.id,
                    timestampMs = (s.customTimestamp ?: now).toEpochMilli(),
                    weightGrams = weight,
                )
            )
        }
        I.AddingMeasurement.OpenDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = true))
        I.AddingMeasurement.CloseDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = false))
        is I.AddingMeasurement.SetMeasurementTimestamp ->
            Next.just(s.copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false))
    }
}

private fun updatePruning(state: S.Active, i: I.Pruning, now: Instant): Next {
    val s = state as? Pruning ?: return Next.just(state)
    return when (i) {
        I.Pruning.Save -> {
            val removed = parseOptionalWeight(s.weightInput.text.toString())
                .getOrElse { return Next.just(s) }
            Next(
                s.parent,
                Cmd.SavePruning(
                    plantId = s.parent.focus.id,
                    timestampMs = (s.customTimestamp ?: now).toEpochMilli(),
                    removedGrams = removed,
                )
            )
        }
        I.Pruning.OpenDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = true))
        I.Pruning.CloseDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = false))
        is I.Pruning.SetPruningTimestamp ->
            Next.just(s.copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false))
    }
}

private fun updateRepotting(state: S.Active, i: I.Repotting, now: Instant): Next {
    val s = state as? Repotting ?: return Next.just(state)
    return when (i) {
        I.Repotting.Save -> {
            val weightAfter = parseOptionalWeight(s.weightInput.text.toString())
                .getOrElse { return Next.just(s) }
            Next(
                s.parent,
                Cmd.SaveRepotting(
                    plantId = s.parent.focus.id,
                    timestampMs = (s.customTimestamp ?: now).toEpochMilli(),
                    potName = "${s.parent.focus.name} pot",
                    weightAfterGrams = weightAfter,
                )
            )
        }
        I.Repotting.OpenDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = true))
        I.Repotting.CloseDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = false))
        is I.Repotting.SetRepottingTimestamp ->
            Next.just(s.copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false))
    }
}

private fun updateLoggingWatering(state: S.Active, i: I.LoggingWatering, now: Instant): Next {
    val s = state as? LogWatering ?: return Next.just(state)
    return when (i) {
        I.LoggingWatering.Save -> {
            val volume = parseOptionalWeight(s.volumeInput.text.toString())
                .getOrElse { return Next.just(s) }
            val weight = parseOptionalWeight(s.weightInput.text.toString())
                .getOrElse { return Next.just(s) }
            Next(
                s.parent,
                Cmd.SaveWatering(
                    plantId = s.parent.focus.id,
                    timestampMs = (s.customTimestamp ?: now).toEpochMilli(),
                    volume = volume,
                    weightGrams = weight,
                )
            )
        }
        I.LoggingWatering.OpenDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = true))
        I.LoggingWatering.CloseDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = false))
        is I.LoggingWatering.SetWateringTimestamp ->
            Next.just(s.copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false))
    }
}

private fun updateCreating(state: S.Active, i: I.Creating, now: Instant): Next {
    val s = state as? S.Creating
        ?: return when (i) {
            // результат пришёл, а экрана создания уже нет: чистим файл
            is I.Creating.AddPhotoCopied -> Next(state, Cmd.DeletePhoto(i.relativePath))
            else -> Next.just(state)
        }
    return when (i) {
        I.Creating.Save -> {
            val name = s.form.customName.text.toString().trim()
            if (name.isEmpty()) return Next.just(s)
            val speciesId = when (val sel = s.form.speciesSelection) {
                is SpeciesSelection.Selected -> sel.species.id.value
                SpeciesSelection.Unknown -> null
                SpeciesSelection.Unselected -> return Next.just(s)
            }
            Next(
                s.parent,
                Cmd.SavePlant(name, speciesId, now.toEpochMilli(), s.form.photoPath)
            )
        }
        is I.Creating.ToggleDropdown ->
            Next.just(s.copy(form = s.form.copy(isDropdownExpanded = i.expanded)))
        is I.Creating.SelectSpecies -> Next.just(
            s.copy(form = s.form.copy(speciesSelection = i.selection, isDropdownExpanded = false))
        )
        I.Creating.UnknownSpecies -> Next.just(
            s.copy(form = s.form.copy(speciesSelection = SpeciesSelection.Unknown, isDropdownExpanded = false))
        )
        is I.Creating.AddPhoto -> Next(s, Cmd.CopyPhoto(i.uri))
        is I.Creating.AddPhotoCopied -> Next(
            s.copy(form = s.form.copy(photoPath = i.relativePath)),
            s.form.photoPath?.let { Cmd.DeletePhoto(it) } ?: Cmd.None
        )
    }
}

private fun History.reduce(i: I.Details): S.Active = when (i) {
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

private fun Plant.reduce(i: I.Details): S.Active = when (i) {
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
        parent = History(this),
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
    I.Details.OpenWateringHistory -> History(parent = this)
}

private fun LogWatering.reduce(i: I.LoggingWatering): S.Active = when (i) {
    I.LoggingWatering.Save -> parent
    I.LoggingWatering.OpenDateTimePicker -> copy(isDateTimePickerOpen = true)
    I.LoggingWatering.CloseDateTimePicker -> copy(isDateTimePickerOpen = false)
    is I.LoggingWatering.SetWateringTimestamp ->
        copy(customTimestamp = i.timestamp, isDateTimePickerOpen = false)
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
        (this as? Plant)
            ?.takeIf { it.focus.id == m.plantId }
            ?.let { HistoryMeas(parent = it, elements = m.elements) }
            ?: this

    is I.Loaded.WateringHistory ->
        (this as? Plant)
            ?.takeIf { it.focus.id == m.plantId }
            ?.let { HistoryWaters(parent = it, elements = m.elements) }
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

private fun Plant.refocused(plants: List<PlantUi>): Plant {
    val i = plants.indexOfFirst { it.id == focus.id }
        .takeIf { it != -1 }
        ?: zipper.index.coerceAtMost(plants.lastIndex)
    return Plant(VectorZipper(i, plants))
}

private inline fun Plant.rebased(
    plants: List<PlantUi>,
    build: (Plant) -> S.Active,
): S.Active {
    val next = refocused(plants)
    return if (plants.any { it.id == focus.id }) build(next) else next
}