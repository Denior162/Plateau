package com.zghonnyk.domain.state

import com.zghonnyk.domain.AddPlantForm
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PhotoSource
import com.zghonnyk.domain.PlantFacts
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.SpeciesSelection
import com.zghonnyk.domain.Watering
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
    data class Overview(val plants: List<PlantFacts>) : Screen {
        override val parent: Nothing? get() = null
    }

    data class EditingName(
        override val parent: Overview,
        val plantId: PlantId,
        val nameInput: String,
        val selectedSpeciesId: Long?,
        val isDropdownExpanded: Boolean = false,
        val speciesList: List<DomainSpecies>,
    ) : Modal


    data class Creating(
        override val parent: Overview,
        val form: AddPlantForm,
        val speciesList: List<DomainSpecies>,
    ) : Screen


    data class Species(
        override val parent: Overview,
        val species: List<DomainSpecies>,
    ) : Screen

    sealed interface Details : Screen {

        data class Plant(val zipper: VectorZipper<PlantFacts>) : Details {
            val focus: PlantFacts get() = zipper.focus
            override val parent: Overview get() = Overview(zipper.list)
        }

        data class History(override val parent: Plant) : Details {
            val plant: PlantFacts get() = parent.focus
        }

        data class HistoryMeas(
            override val parent: Plant,
            val elements: List<Measurement>,
        ) : Details

        data class HistoryWaters(
            override val parent: Plant,
            val elements: List<Watering>,
        ) : Details
    }

    sealed interface PlantModal : Modal {
        override val parent: Details.Plant

        sealed interface HistoryModal : Modal {
            override val parent: Details.History

            data class EditMeasurement(
                override val parent: Details.History,
                val measurementId: MeasurementId,
                val weightInput: String,
                val originalTimestamp: Instant,
                val timestamp: Instant,
                val isDateTimePickerOpen: Boolean = false
            ) : HistoryModal

            data class EditWatering(
                override val parent: Details.History,
                val wateringId: WateringEventId,
                val volumeInput: String,
                val originalTimestamp: Instant,
                val timestamp: Instant,
                val isDateTimePickerOpen: Boolean
            ) : HistoryModal
        }


        data class AddMeasurement(
            override val parent: Details.Plant,
            val customTimestamp: Instant?,
            val isDateTimePickerOpen: Boolean,
        ) : PlantModal

        data class LogWatering(
            override val parent: Details.Plant,
            val customTimestamp: Instant?,
            val isDateTimePickerOpen: Boolean,
        ) : PlantModal

        data class Pruning(
            override val parent: Details.Plant,
            val customTimestamp: Instant?,
            val isDateTimePickerOpen: Boolean,
        ) : PlantModal

        data class Repotting(
            override val parent: Details.Plant,
            val customTimestamp: Instant?,
            val isDateTimePickerOpen: Boolean,
        ) : PlantModal
        data class EditMeasurement(
            override val parent: Details.Plant,
            val measurementId: MeasurementId,
            val weightInput: String,
            val timestamp: Instant,
            val isDateTimePickerOpen: Boolean,
        ) : PlantModal

        data class EditPlant(
            override val parent: Details.Plant,
            val plantId: PlantId,
            val nameInput: String,
            val selectedSpeciesId: Long?,
            val isDropdownExpanded: Boolean,
            val speciesList: List<DomainSpecies>,
        ) : PlantModal
    }
}

sealed interface PlantIntent {
    data object Refresh : PlantIntent
    data object Back : PlantIntent
    data class ExportCsv(val uri: PhotoSource) : PlantIntent
    data class ImportCsv(val uri: PhotoSource) : PlantIntent

    sealed interface Loaded : PlantIntent {
        data class Species(val species: List<DomainSpecies>) : Loaded
        data class EditingSpecies(val species: List<DomainSpecies>) : Loaded
        data class MeasurementHistory(val plantId: PlantId, val elements: List<Measurement>) : Loaded

        data class WateringHistory(val plantId: PlantId, val elements: List<Watering>) : Loaded

        data class CreatingSpecies(val species: List<DomainSpecies>) : Loaded
    }

    sealed interface Overview : PlantIntent {
        data class SelectPlant(val plantId: PlantId) : Overview
        data object OpenAddPlant : Overview
        data class StartEditingPlantName(val plantId: PlantId) : Overview
        data class AddPhoto(val plantId: PlantId, val uri: PhotoSource) : Overview
        data class PhotoCopied(val plantId: PlantId, val relativePath: String) : Overview
        sealed interface Deletion : Overview {
            data class Request(val plantId: PlantId) : Deletion
            data class Undo(val plantId: PlantId) : Deletion
            data class Commit(val plantId: PlantId) : Deletion
        }
    }

    sealed interface EditingName : PlantIntent {
        data class Save(val name: String) : EditingName
        data class ToggleDropdown(val expanded: Boolean) : EditingName
        data class SelectSpecies(val speciesId: Long?) : EditingName
    }

    sealed interface Creating : PlantIntent {
        data class Save(val name: String) : Creating
        data class ToggleDropdown(val expanded: Boolean) : Creating
        data class SelectSpecies(val selection: SpeciesSelection) : Creating
        data object UnknownSpecies : Creating
        data class AddPhoto(val uri: PhotoSource) : Creating
        data class AddPhotoCopied(val relativePath: String) : Creating
    }

    sealed interface Details : PlantIntent {
        data class GoTo(val index: Int) : Details
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
        data class Save(val weight: String) : AddingMeasurement
        data object OpenDateTimePicker : AddingMeasurement
        data object CloseDateTimePicker : AddingMeasurement
        data class SetMeasurementTimestamp(val timestamp: Instant?) : AddingMeasurement
    }

    sealed interface LoggingWatering : PlantIntent {
        data class Save(val volume: String, val weight: String) : LoggingWatering
        data object OpenDateTimePicker : LoggingWatering
        data object CloseDateTimePicker : LoggingWatering
        data class SetWateringTimestamp(val timestamp: Instant?) : LoggingWatering
    }

    sealed interface EditingPlant : PlantIntent {
        data class Save(val name: String) : EditingPlant
        data class ToggleDropdown(val expanded: Boolean) : EditingPlant
        data class SelectSpecies(val speciesId: Long?) : EditingPlant
    }

    sealed interface EditingMeasurement : PlantIntent {
        data class Save(val weight: String) : EditingMeasurement
        data object OpenDateTimePicker : EditingMeasurement
        data object CloseDateTimePicker : EditingMeasurement
        data class SetTimestamp(val timestamp: Instant) : EditingMeasurement
        data object Saved : EditingMeasurement
    }

    sealed interface EditingWatering : PlantIntent {
        data class Save(val volume: String) : EditingWatering
        data object OpenDateTimePicker : EditingWatering
        data object CloseDateTimePicker : EditingWatering
        data class SetTimestamp(val timestamp: Instant) : EditingWatering
        data object Saved : EditingWatering
    }

    sealed interface Pruning : PlantIntent {
        data class Save(val removedWeight: String) : Pruning
        data object OpenDateTimePicker : Pruning
        data object CloseDateTimePicker : Pruning
        data class SetPruningTimestamp(val timestamp: Instant?) : Pruning
    }

    sealed interface Repotting : PlantIntent {
        data class Save(val weightAfter: String) : Repotting
        data object OpenDateTimePicker : Repotting
        data object CloseDateTimePicker : Repotting
        data class SetRepottingTimestamp(val timestamp: Instant?) : Repotting
    }
}