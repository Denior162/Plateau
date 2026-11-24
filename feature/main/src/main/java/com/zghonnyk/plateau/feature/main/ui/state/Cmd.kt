package com.zghonnyk.plateau.feature.main.ui.state

import android.net.Uri
import com.zghonnyk.domain.PlantId

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
}

data class Next(val state: PlantState.Active, val cmd: Cmd) {
    companion object {
        fun just(state: PlantState.Active) = Next(state, Cmd.None)
    }
}
