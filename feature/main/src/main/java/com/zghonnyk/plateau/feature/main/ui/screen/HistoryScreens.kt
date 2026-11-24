package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.zghonnyk.domain.PhaseBreak
import com.zghonnyk.domain.Watering
import com.zghonnyk.domain.Weighing
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.components.MeasurementDateTimePicker
import com.zghonnyk.plateau.feature.main.ui.state.HistoryUIModel
import com.zghonnyk.plateau.feature.main.ui.state.MeasurementUiModel
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.WateringUiModel
import com.zghonnyk.ui.theme.spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    plant: PlantUi,
    modal: PlantState.PlantModal.HistoryModal?,
    onIntent: (PlantIntent) -> Unit,
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant.name) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(PlantIntent.Back) }) {
                        Icon(painterResource(R.drawable.back), contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            items(plant.timeline) { entry ->
                when (entry) {
                    is Weighing -> {
                        val rawWeight = entry.m.weightGrams.formatNumber()
                        HistoryRowCard(
                            text = stringResource(R.string.history_weighing_item, dateFormatter.format(entry.at), rawWeight),
                            onEdit = {
                                onIntent(
                                    PlantIntent.Details.StartEditingMeasurement(
                                        id = entry.m.id,
                                        currentWeightString = rawWeight,
                                        timestamp = entry.at,
                                    )
                                )
                            },
                            onDelete = {
                                onIntent(PlantIntent.Details.DeleteMeasurement(entry.m.id))
                            }
                        )
                    }

                    is Watering -> {
                        val rawVolume = entry.volumeMl?.formatNumber().orEmpty()
                        val volumeLabel =
                            if (rawVolume.isNotEmpty()) stringResource(R.string.history_volume_ml, rawVolume) else stringResource(R.string.history_volume_not_specified)
                        HistoryRowCard(
                            text = stringResource(R.string.history_watering_item, dateFormatter.format(entry.at), volumeLabel),
                            onEdit = {
                                onIntent(
                                    PlantIntent.Details.StartEditingWatering(
                                        wateringEventId = entry.id,
                                        currentMl = rawVolume,
                                        timestamp = entry.at,
                                    )
                                )
                            },
                            onDelete = {
                                onIntent(PlantIntent.Details.DeleteWatering(entry.id))
                            }
                        )
                    }

                    is PhaseBreak -> Unit
                }
            }
        }
    }

    when (modal) {
        is PlantState.PlantModal.HistoryModal.EditMeasurement -> EditMeasurementDialog(
            state = modal,
            onIntent = onIntent
        )

        is PlantState.PlantModal.HistoryModal.EditWatering -> EditWateringDialog(
            state = modal,
            onIntent = onIntent
        )

        null -> Unit
    }
}

@Composable
fun EditMeasurementDialog(
    state: PlantState.PlantModal.HistoryModal.EditMeasurement,
    onIntent: (PlantIntent) -> Unit,
) {
    val formattedDate = remember(state.timestamp) {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(state.timestamp)
    }

    AlertDialog(
        onDismissRequest = { onIntent(PlantIntent.Back) },
        title = { Text(stringResource(R.string.history_edit_measurement)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                OutlinedTextField(
                    state = state.weightInput,
                    label = { Text(stringResource(R.string.history_weight_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = { onIntent(PlantIntent.EditingMeasurement.OpenDateTimePicker) }) {
                    Icon(painterResource(R.drawable.edit_calendar), contentDescription = null)
                    Text(formattedDate)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(PlantIntent.EditingMeasurement.Save) }) { Text(stringResource(R.string.save_button)) }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(PlantIntent.Back) }) { Text(stringResource(R.string.cancel_button)) }
        },
    )

    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = { onIntent(PlantIntent.EditingMeasurement.CloseDateTimePicker) },
            onDateTimeSelected = { onIntent(PlantIntent.EditingMeasurement.SetTimestamp(it)) },
        )
    }
}

@Composable
fun EditWateringDialog(
    state: PlantState.PlantModal.HistoryModal.EditWatering,
    onIntent: (PlantIntent) -> Unit,
) {
    val formattedDate = remember(state.timestamp) {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(state.timestamp)
    }

    AlertDialog(
        onDismissRequest = { onIntent(PlantIntent.Back) },
        title = { Text(stringResource(R.string.history_edit_watering)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                OutlinedTextField(
                    state = state.volumeInput,
                    label = { Text(stringResource(R.string.history_volume_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = { onIntent(PlantIntent.EditingWatering.OpenDateTimePicker) }) {
                    Icon(painterResource(R.drawable.edit_calendar), contentDescription = null)
                    Text(formattedDate)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(PlantIntent.EditingWatering.Save) }) { Text(stringResource(R.string.save_button)) }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(PlantIntent.Back) }) { Text(stringResource(R.string.cancel_button)) }
        },
    )

    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = { onIntent(PlantIntent.EditingWatering.CloseDateTimePicker) },
            onDateTimeSelected = { onIntent(PlantIntent.EditingWatering.SetTimestamp(it)) },
        )
    }
}

@Composable
private fun HistoryRowCard(
    text: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.edit), contentDescription = stringResource(R.string.history_cd_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.history_cd_delete))
            }
        }
    }
}

private fun Double.formatNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

@Composable
fun HistoryMeasScreen(
    onBack: () -> Unit,
    screenName: String,
    elements: List<MeasurementUiModel>,
    onEdit: (MeasurementUiModel) -> Unit,
    onDelete: (MeasurementUiModel) -> Unit,
) = HistoryContent(onBack, screenName, elements, onEdit, onDelete)

@Composable
fun HistoryWatersScreen(
    onBack: () -> Unit,
    screenName: String,
    elements: List<WateringUiModel>,
    onEdit: (WateringUiModel) -> Unit,
    onDelete: (WateringUiModel) -> Unit,
) = HistoryContent(onBack, screenName, elements, onEdit, onDelete)

@Composable
fun <T : HistoryUIModel> HistoryContent(
    onBack: () -> Unit,
    screenName: String,
    elements: List<T>,
    onEdit: (T) -> Unit,
    onDelete: (T) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar({ Text(screenName) }, navigationIcon = {
            IconButton(onBack) {
                Icon(
                    painter = painterResource(R.drawable.back), ""
                )
            }
        })
    }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = spacing.medium),
        ) {
            items(elements) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.small),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.date} — ${item.usefulData}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f) // Текст занимает всё свободное место
                            )
                            IconButton({ onEdit(item) }) {
                                Icon(painterResource(R.drawable.edit), stringResource(R.string.history_cd_edit))
                            }
                            IconButton({ onDelete(item) }) {
                                Icon(painterResource(R.drawable.delete), stringResource(R.string.history_cd_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditWeightDialog(
    title: String,
    input: TextFieldState,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(title) },
    text = {
        OutlinedTextField(
            state = input,
            label = { Text(stringResource(R.string.history_weight_label)) },
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    },
    confirmButton = { TextButton(onSave) { Text(stringResource(R.string.save_button)) } },
    dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel_button)) } },
)