package com.zghonnyk.plateau.feature.main.ui.state

import android.net.Uri

sealed interface Cmd {
    data object None : Cmd
    data class ExportCsv(val uri: Uri) : Cmd
    data class ImportCsv(val uri: Uri) : Cmd
}

data class Next(val state: PlantState.Active, val cmd: Cmd = Cmd.None)

