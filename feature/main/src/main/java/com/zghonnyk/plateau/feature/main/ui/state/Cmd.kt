package com.zghonnyk.plateau.feature.main.ui.state

sealed interface Cmd {
    data object None : Cmd
}

data class Next(val state: PlantState.Active, val cmd: Cmd = Cmd.None)

