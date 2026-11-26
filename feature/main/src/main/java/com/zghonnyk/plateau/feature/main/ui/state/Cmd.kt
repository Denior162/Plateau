package com.zghonnyk.plateau.feature.main.ui.state

import android.net.Uri
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.WateringEventId

sealed interface Cmd {
    data object None : Cmd
    data class ExportCsv(val uri: Uri) : Cmd
    data class ImportCsv(val uri: Uri) : Cmd

    data class SavePruning(
        val plantId: PlantId,
        val timestampMs: Long,
        val removedGrams: Double?,
    ) : Cmd

    data class SaveRepotting(
        val plantId: PlantId,
        val timestampMs: Long,
        val potName: String,
        val weightAfterGrams: Double?,
    ) : Cmd

    data class SaveMeasurement(
        val plantId: PlantId,
        val timestampMs: Long,
        val weightGrams: Double,
    ) : Cmd

    data class SaveWatering(
        val plantId: PlantId,
        val timestampMs: Long,
        val volume: Double?,
        val weightGrams: Double?,
    ) : Cmd

    data class SavePlant(
        val name: String,
        val speciesId: Long?,
        val createdAtMs: Long,
        val photoPath: String?,
    ) : Cmd
    data class CopyPhoto(val uri: Uri) : Cmd
    data class DeletePhoto(val path: String) : Cmd
    data class UpdatePlant(
        val plantId: PlantId,
        val name: String,
        val speciesId: Long?,
    ) : Cmd

    data object LoadEditingSpecies : Cmd
    data object LoadCreatingSpecies : Cmd

    data class UpdateMeasurement(
        val measurementId: MeasurementId,
        val weightGrams: Double,
        val timestampMs: Long,
    ) : Cmd

    data class UpdateWatering(
        val wateringId: WateringEventId,
        val plantId: PlantId,
        val oldTimestampMs: Long,
        val newTimestampMs: Long,
        val volumeMl: Double?,
    ) : Cmd
    data class DeleteMeasurement(val id: MeasurementId) : Cmd
    data class DeleteWatering(val id: WateringEventId) : Cmd
    data class CopyToClipboard(val label: String, val text: String) : Cmd
    data class StartDeleteTimer(val plantId: PlantId, val delayMs: Long) : Cmd
    data class CancelDeleteTimer(val plantId: PlantId) : Cmd
    data class DeletePlant(val plantId: PlantId) : Cmd
    data class CopyPlantPhoto(val plantId: PlantId, val uri: Uri): Cmd
    data class SavePlantPhoto(val plantId: PlantId, val relativePath: String): Cmd
}

data class Next(val state: PlantState.Active, val cmd: Cmd) {
    companion object {
        fun just(state: PlantState.Active) = Next(state, Cmd.None)
    }
}
