package com.zghonnyk.domain.state

import com.zghonnyk.domain.AddPlantForm
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantFacts
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.SpeciesSelection
import com.zghonnyk.domain.state.PlantState.Details.History
import com.zghonnyk.domain.state.PlantState.Details.HistoryMeas
import com.zghonnyk.domain.state.PlantState.Details.HistoryWaters
import com.zghonnyk.domain.state.PlantState.Details.Plant
import com.zghonnyk.domain.state.PlantState.PlantModal.AddMeasurement
import com.zghonnyk.domain.state.PlantState.PlantModal.EditMeasurement
import com.zghonnyk.domain.state.PlantState.PlantModal.EditPlant
import com.zghonnyk.domain.state.PlantState.PlantModal.HistoryModal
import com.zghonnyk.domain.state.PlantState.PlantModal.HistoryModal.EditWatering
import com.zghonnyk.domain.state.PlantState.PlantModal.LogWatering
import com.zghonnyk.domain.state.PlantState.PlantModal.Pruning
import com.zghonnyk.domain.state.PlantState.PlantModal.Repotting
import com.zghonnyk.vector_zipper.VectorZipper
import java.time.Instant
import java.time.YearMonth
import com.zghonnyk.domain.state.PlantIntent as I
import com.zghonnyk.domain.state.PlantState as S

const val DELETE_DELAY_MS = 10_000L

val S.Active.canGoBack: Boolean get() = this !is S.Overview

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
    is I.EditingName -> updateEditingName(state, intent)
    is I.EditingMeasurement -> updateEditingMeasurement(state, intent)
    is I.EditingWatering -> updateEditingWatering(state, intent)
    is I.Overview -> updateOverview(state, intent)
    is I.Details -> updateDetails(state, intent)
}

private fun measurementEdit(
    state: S.Active,
    id: MeasurementId,
    weightInput: String,
    timestamp: Instant,
): Next =
    weightInput.toPositiveDoubleOrNull()
        ?.let { w -> Next(state, Cmd.UpdateMeasurement(id, w, timestamp.toEpochMilli())) }
        ?: Next.just(state)

data class PendingDelete(val plantId: PlantId, val deadlineMs: Long)
data class DeletionNext(val pending: List<PendingDelete>, val cmd: Cmd)

private fun List<PendingDelete>.isPending(id: PlantId): Boolean = any { it.plantId == id }

private fun resolveDeletion(pending: List<PendingDelete>, id: PlantId, cmd: Cmd): DeletionNext =
    if (pending.isPending(id)) DeletionNext(pending.filterNot { it.plantId == id }, cmd)
    else DeletionNext(pending, Cmd.None)

fun updateDeletions(
    pending: List<PendingDelete>,
    intent: I.Overview.Deletion,
    nowMs: Long,
): DeletionNext = when (intent) {
    is I.Overview.Deletion.Request ->
        if (pending.isPending(intent.plantId)) DeletionNext(pending, Cmd.None)
        else DeletionNext(
            pending + PendingDelete(intent.plantId, nowMs + DELETE_DELAY_MS),
            Cmd.StartDeleteTimer(intent.plantId, DELETE_DELAY_MS),
        )
    is I.Overview.Deletion.Undo ->
        resolveDeletion(pending, intent.plantId, Cmd.CancelDeleteTimer(intent.plantId))
    is I.Overview.Deletion.Commit ->
        resolveDeletion(pending, intent.plantId, Cmd.DeletePlant(intent.plantId))
}
private fun updateEditingWatering(state: S.Active, i: I.EditingWatering): Next =
    (state as? EditWatering)?.let { s ->
        when (i) {
            is I.EditingWatering.Save ->
                parseOptionalWeight(i.volume).fold(
                    onSuccess = { volume ->
                        Next(
                            s,
                            Cmd.UpdateWatering(
                                wateringId = s.wateringId,
                                plantId = s.parent.plant.id,
                                oldTimestampMs = s.originalTimestamp.toEpochMilli(),
                                newTimestampMs = s.timestamp.toEpochMilli(),
                                volumeMl = volume,
                            )
                        )
                    },
                    onFailure = { Next.just(s) },
                )
            I.EditingWatering.OpenDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = true))
            I.EditingWatering.CloseDateTimePicker -> Next.just(s.copy(isDateTimePickerOpen = false))
            is I.EditingWatering.SetTimestamp ->
                Next.just(s.copy(timestamp = i.timestamp, isDateTimePickerOpen = false))
            I.EditingWatering.Saved -> Next.just(s.parent)
        }
    } ?: Next.just(state)

private inline fun onPlant(state: S.Active, f: (Plant) -> S.Active): Next =
    Next.just((state as? Plant)?.let(f) ?: state)

private inline fun onHistory(state: S.Active, f: (History) -> S.Active): Next =
    Next.just((state as? History)?.let(f) ?: state)

private fun editMeasurement(
    parent: History,
    i: I.Details.StartEditingMeasurement,
): S.Active = HistoryModal.EditMeasurement(
    parent = parent,
    measurementId = i.id,
    weightInput = i.currentWeightString,
    originalTimestamp = i.timestamp,
    timestamp = i.timestamp,
    isDateTimePickerOpen = false,
)

private fun updateDetails(state: S.Active, i: I.Details): Next = when (i) {
    is I.Details.DeleteMeasurement -> Next(state, Cmd.DeleteMeasurement(i.id))
    is I.Details.DeleteWatering -> Next(state, Cmd.DeleteWatering(i.id))
    is I.Details.CopyMonth -> copyMonth(state, i.month)

    I.Details.GoLeft -> onPlant(state) { it.copy(zipper = it.zipper.goBack()) }
    I.Details.GoRight -> onPlant(state) { it.copy(zipper = it.zipper.goForward()) }
    I.Details.OpenAddMeasurement -> onPlant(state) {
        AddMeasurement(parent = it, customTimestamp = null, isDateTimePickerOpen = false)
    }
    I.Details.OpenWateringDialog -> onPlant(state) {
        LogWatering(parent = it, customTimestamp = null, isDateTimePickerOpen = false)
    }
    I.Details.OpenPruning -> onPlant(state) {
        Pruning(parent = it, customTimestamp = null, isDateTimePickerOpen = false)
    }
    I.Details.OpenRepottingDialog -> onPlant(state) {
        Repotting(parent = it, customTimestamp = null, isDateTimePickerOpen = false)
    }
    I.Details.OpenMeasurementHistory,
    I.Details.OpenWateringHistory -> onPlant(state) { History(parent = it) }
    I.Details.StartEditingPlant -> (state as? Plant)?.let {
        Next(
            EditPlant(
                parent = it,
                plantId = it.focus.id,
                nameInput = it.focus.name,
                selectedSpeciesId = it.focus.speciesId,
                isDropdownExpanded = false,
                speciesList = emptyList(),
            ),
            Cmd.LoadEditingSpecies
        )
    } ?: Next.just(state)

    is I.Details.StartEditingMeasurement -> Next.just(
        when (state) {
            is Plant -> editMeasurement(History(parent = state), i)
            is History -> editMeasurement(state, i)
            else -> state
        }
    )

    is I.Details.StartEditingWatering -> onHistory(state) {
        EditWatering(
            parent = it,
            wateringId = i.wateringEventId,
            volumeInput = i.currentMl,
            originalTimestamp = i.timestamp,
            timestamp = i.timestamp,
            isDateTimePickerOpen = false,
        )
    }

    is I.Details.GoTo -> onPlant(state) { it.copy(zipper = it.zipper.goTo(i.index)) }
}

private fun copyMonth(state: S.Active, month: YearMonth): Next =
    (state as? Plant)
        ?.let { Next(state, Cmd.CopyMonth(it.focus.id, month)) }
        ?: Next.just(state)

private fun updateOverview(state: S.Active, i: I.Overview): Next =
    (state as? S.Overview)?.let { s ->
        when (i) {
            I.Overview.OpenAddPlant -> Next(
                S.Creating(parent = s, form = AddPlantForm.empty(), speciesList = emptyList()),
                Cmd.LoadCreatingSpecies
            )

            is I.Overview.SelectPlant -> Next.just(
                s.plants.indexOfFirst { it.id == i.plantId }
                    .takeIf { it != -1 }
                    ?.let { Plant(VectorZipper(it, s.plants)) }
                    ?: s
            )

            is I.Overview.StartEditingPlantName ->
                s.plants.find { it.id == i.plantId }?.let { plant ->
                    Next(
                        S.EditingName(
                            parent = s,
                            plantId = plant.id,
                            nameInput = plant.name,
                            selectedSpeciesId = plant.speciesId,
                            isDropdownExpanded = false,
                            speciesList = emptyList(),
                        ),
                        Cmd.LoadEditingSpecies
                    )
                } ?: Next.just(s)

            is I.Overview.AddPhoto -> Next(s, Cmd.CopyPlantPhoto(i.plantId, i.uri))
            is I.Overview.PhotoCopied -> Next(s, Cmd.SavePlantPhoto(i.plantId, i.relativePath))
            is I.Overview.Deletion -> Next.just(s)
        }

    } ?: Next.just(state)

private fun updateEditingMeasurement(state: S.Active, i: I.EditingMeasurement): Next =
    when (state) {
        is EditMeasurement -> when (i) {
            is I.EditingMeasurement.Save ->
                measurementEdit(state, state.measurementId, i.weight, state.timestamp)

            I.EditingMeasurement.OpenDateTimePicker -> Next.just(state.copy(isDateTimePickerOpen = true))
            I.EditingMeasurement.CloseDateTimePicker -> Next.just(state.copy(isDateTimePickerOpen = false))
            is I.EditingMeasurement.SetTimestamp ->
                Next.just(state.copy(timestamp = i.timestamp, isDateTimePickerOpen = false))
            I.EditingMeasurement.Saved -> Next.just(state.parent)
        }

        is HistoryModal.EditMeasurement -> when (i) {
           is I.EditingMeasurement.Save ->
                measurementEdit(state, state.measurementId, state.weightInput, state.timestamp)
            I.EditingMeasurement.OpenDateTimePicker -> Next.just(state.copy(isDateTimePickerOpen = true))
            I.EditingMeasurement.CloseDateTimePicker -> Next.just(state.copy(isDateTimePickerOpen = false))
            is I.EditingMeasurement.SetTimestamp ->
                Next.just(state.copy(timestamp = i.timestamp, isDateTimePickerOpen = false))
            I.EditingMeasurement.Saved -> Next.just(state.parent)
        }

        else -> Next.just(state)
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
        is I.EditingPlant.Save -> {
            val name = i.name.trim()
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
        is I.AddingMeasurement.Save -> {
            val weight = i.weight.toPositiveDoubleOrNull() ?: return Next.just(s)
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
        is I.Pruning.Save -> {
            val removed = parseOptionalWeight(i.removedWeight)
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
        is I.Repotting.Save -> {
            val weightAfter = parseOptionalWeight(i.weightAfter)
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
        is I.LoggingWatering.Save -> {
            val volume = parseOptionalWeight(i.volume)
                .getOrElse { return Next.just(s) }
            val weight = parseOptionalWeight(i.weight)
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
            is I.Creating.AddPhotoCopied -> Next(state, Cmd.DeletePhoto(i.relativePath))
            else -> Next.just(state)
        }
    return when (i) {
        is I.Creating.Save -> {
            val name = i.name.trim()
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
fun canSaveName(name: String): Boolean = name.trim().isNotEmpty()
private fun updateEditingName(state: S.Active, i: I.EditingName): Next =
    (state as? S.EditingName)?.let { s ->
        when (i) {
            is I.EditingName.Save ->
                if (canSaveName(i.name)) Next(
                    s.parent,
                    Cmd.UpdatePlant(s.plantId, i.name.trim(), s.selectedSpeciesId)
                ) else Next.just(s)

            is I.EditingName.ToggleDropdown ->
                Next.just(s.copy(isDropdownExpanded = i.expanded))

            is I.EditingName.SelectSpecies ->
                Next.just(s.copy(selectedSpeciesId = i.speciesId, isDropdownExpanded = false))
        }
    } ?: Next.just(state)


private fun S.Active.onLoaded(m: I.Loaded): S.Active = when (m) {
    is I.Loaded.Species ->
        (this as? S.Overview)?.let { S.Species(parent = it, species = m.species) } ?: this

    is I.Loaded.EditingSpecies -> when (this) {
        is S.EditingName -> copy(speciesList = m.species)
        is EditPlant -> copy(speciesList = m.species)
        else -> this
    }

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

fun applyPlants(current: S, plants: List<PlantFacts>): S = when (current) {
    S.DBUninitialized -> S.Overview(plants)
    is S.Active -> current.withPlants(plants)
}

private fun S.Active.withPlants(plants: List<PlantFacts>): S.Active {
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
                is EditWatering -> copy(parent = newHistory)
            }
        }
    }
}

private fun Plant.refocused(plants: List<PlantFacts>): Plant {
    val i = plants.indexOfFirst { it.id == focus.id }
        .takeIf { it != -1 }
        ?: zipper.index.coerceAtMost(plants.lastIndex)
    return Plant(VectorZipper(i, plants))
}

private inline fun Plant.rebased(
    plants: List<PlantFacts>,
    build: (Plant) -> S.Active,
): S.Active {
    val next = refocused(plants)
    return if (plants.any { it.id == focus.id }) build(next) else next
}