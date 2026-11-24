package com.zghonnyk.plateau.feature.main.ui.state

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.WateringEventId
import com.zghonnyk.vector_zipper.VectorZipper
import java.time.Instant
import java.time.YearMonth
import com.zghonnyk.domain.Species as DomainSpecies

sealed interface PlantState {
    data object DBUninitialized : PlantState
    sealed interface Active : PlantState {
        val parent: Active?
    }

    sealed interface Screen : Active


    sealed interface Modal : Active {
        override val parent: Screen
    }
    @Immutable
    data class Overview(val plants: List<PlantUi>) : Screen {
        override val parent: Nothing? get() = null
    }

    @Immutable
    data class EditingName(
        override val parent: Overview,
        val plantId: PlantId,
        val nameInput: TextFieldState,
        val selectedSpeciesId: Long?,
        val isDropdownExpanded: Boolean = false,
        val speciesList: List<DomainSpecies>,
    ) : Modal

    @Immutable
    data class Creating(
        override val parent: Overview,
        val form: AddPlantForm = AddPlantForm.empty(),
        val speciesList: List<DomainSpecies>,
    ) : Screen

    @Immutable
    data class Species(
        override val parent: Overview,
        val species: List<DomainSpecies>,
    ) : Screen

    sealed interface Details : Screen {

        @Immutable
        data class Plant(val zipper: VectorZipper<PlantUi>) : Details {
            val focus: PlantUi get() = zipper.focus // ← единственное место, зависящее от API VectorZipper
            override val parent: Overview get() = Overview(zipper.list)
        }

        @Immutable
        data class History(override val parent: Plant) : Details {
            val plant: PlantUi get() = parent.focus
        }

        @Immutable
        data class HistoryMeas(
            override val parent: Plant,
            val elements: List<MeasurementUiModel>,
        ) : Details {
            val plant: PlantUi get() = parent.focus
        }

        @Immutable
        data class HistoryWaters(
            override val parent: Plant,
            val elements: List<WateringUiModel>,
        ) : Details {
            val plant: PlantUi get() = parent.focus
        }
    }

    // ───────────── модалки над Details.Plant ─────────────

    sealed interface PlantModal : Modal {
        override val parent: Details.Plant

        sealed interface HistoryModal : Modal {
            override val parent: Details.History

            data class EditMeasurement(
                override val parent: Details.History,
                val measurementId: MeasurementId,
                val weightInput: TextFieldState,
                val originalTimestamp: Instant,
                val timestamp: Instant,
                val isDateTimePickerOpen: Boolean = false
            ) : HistoryModal

            data class EditWatering(
                override val parent: Details.History,
                val wateringId: WateringEventId,
                val volumeInput: TextFieldState,
                val originalTimestamp: Instant,
                val timestamp: Instant,
                val isDateTimePickerOpen: Boolean = false
            ) : HistoryModal
        }

        @Immutable
        data class AddMeasurement(
            override val parent: Details.Plant,
            val weightInput: TextFieldState = TextFieldState(""),
            val customTimestamp: Instant? = null,
            val isDateTimePickerOpen: Boolean = false,
        ) : PlantModal

        @Immutable
        data class LogWatering(
            override val parent: Details.Plant,
            val volumeInput: TextFieldState = TextFieldState(""),
            val weightInput: TextFieldState = TextFieldState(""),
            val customTimestamp: Instant? = null,
            val isDateTimePickerOpen: Boolean = false,
        ) : PlantModal

        @Immutable
        data class Pruning(
            override val parent: Details.Plant,
            val weightInput: TextFieldState = TextFieldState(""),
            val customTimestamp: Instant? = null,
            val isDateTimePickerOpen: Boolean = false,
        ) : PlantModal

        @Immutable
        data class Repotting(
            override val parent: Details.Plant,
            val weightInput: TextFieldState = TextFieldState(""),
            val customTimestamp: Instant? = null,
            val isDateTimePickerOpen: Boolean = false,
        ) : PlantModal

        data class EditMeasurement(
            override val parent: Details.Plant,
            val measurementId: MeasurementId,
            val weightInput: TextFieldState,
            val timestamp: Instant,
            val isDateTimePickerOpen: Boolean = false,
        ) : PlantModal

        data class EditPlant(
            override val parent: Details.Plant,
            val plantId: PlantId,
            val nameInput: TextFieldState,
            val selectedSpeciesId: Long?,
            val isDropdownExpanded: Boolean = false,
        ) : PlantModal
    }
}

/**
 * Интенты сгруппированы по тому состоянию, в котором они имеют смысл.
 * Навигация назад — один интент [Back]. Результаты асинхронной загрузки — [Loaded]:
 * они применяются к ТЕКУЩЕМУ состоянию, а не к снимку, захваченному в момент запроса.
 */
sealed interface PlantIntent {
    data object Refresh : PlantIntent
    data object Back : PlantIntent
    data class ExportCsv(val uri: Uri) : PlantIntent
    data class ImportCsv(val uri: Uri) : PlantIntent

    sealed interface Loaded : PlantIntent {
        data class Species(val species: List<DomainSpecies>) : Loaded
        data class EditingSpecies(val species: List<DomainSpecies>) : Loaded
        data class MeasurementHistory(
            val plantId: PlantId, val elements: List<MeasurementUiModel>
        ) : Loaded

        data class WateringHistory(val plantId: PlantId, val elements: List<WateringUiModel>) :
            Loaded

        data class CreatingSpecies(val species: List<DomainSpecies>) : Loaded
    }

    sealed interface Overview : PlantIntent {
        data class SelectPlant(val plantId: PlantId) : Overview
        data object OpenAddPlant : Overview
        data class StartEditingPlantName(val plantId: PlantId) : Overview

        sealed interface Deletion : Overview {
            data class Request(val plantId: PlantId) : Deletion
            data class Undo(val plantId: PlantId) : Deletion
            data class Commit(val plantId: PlantId) : Deletion
        }
    }

    sealed interface EditingName : PlantIntent {
        data object Save : EditingName
        data class ToggleDropdown(val expanded: Boolean) : EditingName
        data class SelectSpecies(val speciesId: Long?) : EditingName
    }

    sealed interface Creating : PlantIntent {
        data object Save : Creating
        data class ToggleDropdown(val expanded: Boolean) : Creating
        data class SelectSpecies(val selection: SpeciesSelection) : Creating
        data object UnknownSpecies : Creating
        data class AddPhoto(val uri: Uri) : Creating
        data class AddPhotoCopied(val relativePath: String) : Creating
    }

    sealed interface Details : PlantIntent {
        data object GoLeft : Details
        data object GoRight : Details
        data class CopyMonth(val month: YearMonth) : Details

        data object OpenPruning : Details
        data object OpenRepottingDialog : Details
        data object OpenAddMeasurement : Details
        data object OpenWateringDialog : Details
        data object OpenMeasurementHistory : Details
        data object OpenWateringHistory : Details

        data object StartEditingPlant : Details

        data class StartEditingMeasurement(
            val id: MeasurementId,
            val currentWeightString: String,
            val timestamp: Instant,
        ) : Details

        data class StartEditingWatering(
            val wateringEventId: WateringEventId,
            val currentMl: String,
            val timestamp: Instant,
        ) : Details

        data class DeleteMeasurement(val id: MeasurementId) : Details
        data class DeleteWatering(val id: WateringEventId) : Details

    }

    sealed interface AddingMeasurement : PlantIntent {
        data object Save : AddingMeasurement
        data object OpenDateTimePicker : AddingMeasurement
        data object CloseDateTimePicker : AddingMeasurement
        data class SetMeasurementTimestamp(val timestamp: Instant?) : AddingMeasurement
    }

    sealed interface LoggingWatering : PlantIntent {
        data object Save : LoggingWatering
        data object OpenDateTimePicker : LoggingWatering
        data object CloseDateTimePicker : LoggingWatering
        data class SetWateringTimestamp(val timestamp: Instant?) : LoggingWatering
    }

    sealed interface EditingPlant : PlantIntent {
        data object Save : EditingPlant
        data class ToggleDropdown(val expanded: Boolean) : EditingPlant
        data class SelectSpecies(val speciesId: Long?) : EditingPlant
    }

    sealed interface EditingMeasurement : PlantIntent {
        data object Save : EditingMeasurement
        data object OpenDateTimePicker : EditingMeasurement
        data object CloseDateTimePicker : EditingMeasurement
        data class SetTimestamp(val timestamp: Instant) : EditingMeasurement
        data object Saved : EditingMeasurement
    }

    sealed interface EditingWatering : PlantIntent {
        data object Save : EditingWatering
        data object OpenDateTimePicker : EditingWatering
        data object CloseDateTimePicker : EditingWatering
        data class SetTimestamp(val timestamp: Instant) : EditingWatering
        data object Saved : EditingWatering
    }

    sealed interface Pruning : PlantIntent {
        data object Save : Pruning
        data object OpenDateTimePicker : Pruning
        data object CloseDateTimePicker : Pruning
        data class SetPruningTimestamp(val timestamp: Instant?) : Pruning
    }

    sealed interface Repotting : PlantIntent {
        data object Save : Repotting
        data object OpenDateTimePicker : Repotting
        data object CloseDateTimePicker : Repotting
        data class SetRepottingTimestamp(val timestamp: Instant?) : Repotting
    }
}