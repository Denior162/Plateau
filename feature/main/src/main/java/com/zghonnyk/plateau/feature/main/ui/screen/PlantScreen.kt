package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.percentInt
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.components.DryingChart
import com.zghonnyk.plateau.feature.main.ui.components.MeasurementDateTimePicker
import com.zghonnyk.plateau.feature.main.ui.components.daysText
import com.zghonnyk.plateau.feature.main.ui.components.statusText
import com.zghonnyk.plateau.feature.main.ui.state.MonthGroupUiModel
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.LoggingWatering.SetWateringTimestamp
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Pruning.SetPruningTimestamp
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Repotting.SetRepottingTimestamp
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.RecordUiModel
import com.zghonnyk.plateau.feature.main.ui.state.placeholderText
import com.zghonnyk.ui.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme as Theme

@Composable
fun PlantScreen(
    plant: PlantState.Details.Plant,
    modal: PlantState.PlantModal?,
    onIntent: (PlantIntent) -> Unit,
) {
    val zipper = plant.zipper
    val pagerState = rememberPagerState(
        initialPage = zipper.index,
        pageCount = { zipper.list.size },
    )

    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(zipper.index) {
        if (pagerState.currentPage != zipper.index) {
            isProgrammaticScroll = true
            try {
                pagerState.animateScrollToPage(zipper.index)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val latestIndex by rememberUpdatedState(zipper.index)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .drop(1)
            .collect {
                val settled = pagerState.settledPage
                if (!isProgrammaticScroll && settled != latestIndex) {
                    onIntent(PlantIntent.Details.GoTo(settled))
                }
            }
    }

    val adding = modal as? PlantState.PlantModal.AddMeasurement
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                PlantAppBar(
                    scrollBehavior = scrollBehavior,
                    onClose = { onIntent(PlantIntent.Back) },
                )
            },
            bottomBar = {
                BottomButtonGroup(
                    isEmptyLefts = zipper.isEmptyLefts,
                    isEmptyRights = zipper.isEmptyRights,
                    isInputActive = adding != null,
                    weightInput = adding?.weightInput ?: remember { TextFieldState() },
                    customTimestamp = adding?.customTimestamp,
                    isDateTimePickerOpen = adding?.isDateTimePickerOpen == true,
                    prediction = plant.focus.prediction?.placeholderText(),
                    onGoLeft = { onIntent(PlantIntent.Details.GoLeft) },
                    onGoRight = { onIntent(PlantIntent.Details.GoRight) },
                    onOpenAddMeasurement = { onIntent(PlantIntent.Details.OpenAddMeasurement) },
                    onOpenWateringDialog = { onIntent(PlantIntent.Details.OpenWateringDialog) },
                    onOpenPrunDialog = { onIntent(PlantIntent.Details.OpenPruning) },
                    onSaveMeasurement = { onIntent(PlantIntent.AddingMeasurement.Save) },
                    onCancelMeasurement = { onIntent(PlantIntent.Back) },
                    onOpenDateTimePicker = { onIntent(PlantIntent.AddingMeasurement.OpenDateTimePicker) },
                    onCloseDateTimePicker = { onIntent(PlantIntent.AddingMeasurement.CloseDateTimePicker) },
                    onRePotDialog = { onIntent(PlantIntent.Details.OpenRepottingDialog) },
                    onSetTimestamp = { instant ->
                        onIntent(
                            PlantIntent.AddingMeasurement.SetMeasurementTimestamp(instant)
                        )
                    },
                )
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                key = { zipper.list[it].id.value },
                beyondViewportPageCount = 1,
                userScrollEnabled = modal == null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                PlantScreenContent(
                    Modifier.padding(horizontal = spacing.large),
                    plant = zipper.list[page],
                    onIntent = onIntent,
                    onOpenMeasurementsHistory = { onIntent(PlantIntent.Details.OpenMeasurementHistory) },
                    onOpenWateringHistory = { onIntent(PlantIntent.Details.OpenWateringHistory) },
                    onRemove = { id -> onIntent(PlantIntent.Details.DeleteMeasurement(id)) },
                    onCopy = { ym -> onIntent(PlantIntent.Details.CopyMonth(ym)) },
                )
            }
        }
        DetailsDialogs(modal = modal, onIntent = onIntent)
    }
}

@Composable
fun WateringDialog(
    state: PlantState.PlantModal.LogWatering,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpenDateTimePicker: () -> Unit,
    onCloseDateTimePicker: () -> Unit,
    onSetTimestamp: (Instant) -> Unit
) {
    val weightText = state.weightInput.text.toString()
    val weightValid = weightText.isBlank() || weightText.trim().replace(',', '.').toDoubleOrNull()
        ?.let { it > 0 } == true

    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = onCloseDateTimePicker,
            onDateTimeSelected = onSetTimestamp
        )
    }
    AlertDialog(onDismissRequest = onCancel, title = { Text(stringResource(R.string.watering_dialog_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                stringResource(R.string.watering_dialog_desc),
                style = Theme.typography.bodySmall
            )
            OutlinedTextField(
                state = state.volumeInput,
                label = { Text(stringResource(R.string.watering_volume_label)) },
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                state = state.weightInput,
                label = { Text(stringResource(R.string.watering_weight_label)) },
                isError = !weightValid,
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = onOpenDateTimePicker) {
                Icon(
                    painterResource(R.drawable.edit_calendar),
                    contentDescription = stringResource(R.string.cd_specify_datetime)
                )
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = onSave, enabled = weightValid
        ) { Text(stringResource(R.string.save_button)) }
    }, dismissButton = {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel_button))
        }
    })
}

@Composable
fun PruningDialog(
    state: PlantState.PlantModal.Pruning,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpenDateTimePicker: () -> Unit,
    onCloseDateTimePicker: () -> Unit,
    onSetTimestamp: (Instant) -> Unit
) {
    val weightText = state.weightInput.text.toString()
    val weightValid = weightText.isBlank() || weightText.trim().replace(',', '.').toDoubleOrNull()
        ?.let { it > 0 } == true

    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = onCloseDateTimePicker,
            onDateTimeSelected = onSetTimestamp
        )
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(R.string.specify_pruning),
                style = Theme.typography.titleSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(
                    stringResource(R.string.prune_dialog_title),
                    style = Theme.typography.bodySmall
                )
                OutlinedTextField(
                    state = state.weightInput,
                    label = { Text(stringResource(R.string.weight_after_pruning)) },
                    isError = !weightValid,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(onClick = onOpenDateTimePicker) {
                    Icon(
                        painterResource(R.drawable.edit_calendar),
                        contentDescription = stringResource(R.string.cd_specify_datetime)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave, enabled = weightValid
            ) { Text("Зберегти") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Скасувати")
            }
        })
}

@Composable
private fun DetailsDialogs(modal: PlantState.PlantModal?, onIntent: (PlantIntent) -> Unit) {
    when (modal) {
        is PlantState.PlantModal.LogWatering -> {
            WateringDialog(
                state = modal,
                onSave = { onIntent(PlantIntent.LoggingWatering.Save) },
                onCancel = { onIntent(PlantIntent.Back) },
                onOpenDateTimePicker = { onIntent(PlantIntent.LoggingWatering.OpenDateTimePicker) },
                onCloseDateTimePicker = { onIntent(PlantIntent.LoggingWatering.CloseDateTimePicker) },
                onSetTimestamp = { instant ->
                    onIntent(SetWateringTimestamp(instant))
                }
            )
        }

        is PlantState.PlantModal.EditMeasurement -> EditWeightDialog(
            title = "Редагувати вимір",
            input = modal.weightInput,
            onSave = { onIntent(PlantIntent.EditingMeasurement.Save) },
            onCancel = { onIntent(PlantIntent.Back) },
        )

        is PlantState.PlantModal.EditPlant -> EditPlantDialog(
            modal = modal,
            onSave = { onIntent(PlantIntent.EditingPlant.Save) },
            onCancel = { onIntent(PlantIntent.Back) },
            onToggleDropdown = { expanded -> onIntent(PlantIntent.EditingPlant.ToggleDropdown(expanded)) },
            onSelectSpecies = { speciesId -> onIntent(PlantIntent.EditingPlant.SelectSpecies(speciesId)) },
        )

        is PlantState.PlantModal.AddMeasurement -> Unit

        null -> Unit
        is PlantState.PlantModal.Pruning -> PruningDialog(
            state = modal,
            onSave = { onIntent(PlantIntent.Pruning.Save) },
            onCancel = { onIntent(PlantIntent.Back) },
            onOpenDateTimePicker = { onIntent(PlantIntent.Pruning.OpenDateTimePicker) },
            onCloseDateTimePicker = { onIntent(PlantIntent.Pruning.CloseDateTimePicker) },
            onSetTimestamp = { instant ->
                onIntent(SetPruningTimestamp(instant))
            }
        )

        is PlantState.PlantModal.Repotting -> RepottingDialog(
            state = modal,
            onSave = { onIntent(PlantIntent.Repotting.Save) },
            onCancel = { onIntent(PlantIntent.Back) },
            onOpenDateTimePicker = { onIntent(PlantIntent.Repotting.OpenDateTimePicker) },
            onCloseDateTimePicker = { onIntent(PlantIntent.Repotting.CloseDateTimePicker) },
            onSetTimestamp = { instant ->
                onIntent(SetRepottingTimestamp(instant))
            }
        )

    }
}

@Composable
private fun EditPlantDialog(
    modal: PlantState.PlantModal.EditPlant,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onSelectSpecies: (Long?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.edit_plant_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    state = modal.nameInput,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                ExposedDropdownMenuBox(
                    expanded = modal.isDropdownExpanded,
                    onExpandedChange = { onToggleDropdown(!modal.isDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedSpeciesName =
                        modal.speciesList.find { it.id.value == modal.selectedSpeciesId }?.latinName
                            ?: "Incertae sedis"

                    OutlinedTextField(
                        value = selectedSpeciesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modal.isDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable,
                                enabled = true
                            )
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modal.isDropdownExpanded,
                        onDismissRequest = { onToggleDropdown(false) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.unknown_species_label)) },
                            onClick = { onSelectSpecies(null) })
                        modal.speciesList.forEach { species ->
                            DropdownMenuItem(
                                text = { Text(species.latinName) },
                                onClick = { onSelectSpecies(species.id.value) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun RepottingDialog(
    state: PlantState.PlantModal.Repotting,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpenDateTimePicker: () -> Unit,
    onCloseDateTimePicker: () -> Unit,
    onSetTimestamp: (Instant) -> Unit
) {
    val weightText = state.weightInput.text.toString()
    val weightValid = weightText.isBlank() || weightText.trim().replace(',', '.').toDoubleOrNull()
        ?.let { it > 0 } == true

    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = onCloseDateTimePicker,
            onDateTimeSelected = onSetTimestamp
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(R.string.repotting_dialog_title),
                style = Theme.typography.titleSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(
                    stringResource(R.string.repotting_dialog_desc),
                    style = Theme.typography.bodySmall
                )
                OutlinedTextField(
                    state = state.weightInput,
                    label = { Text(stringResource(R.string.repotting_weight_label)) },
                    isError = !weightValid,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(onClick = onOpenDateTimePicker) {
                    Icon(
                        painterResource(R.drawable.edit_calendar),
                        contentDescription = stringResource(R.string.cd_specify_datetime)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave, enabled = weightValid
            ) { Text("Зберегти") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Скасувати")
            }
        }
    )
}

@Composable
fun BottomButtonGroup(
    modifier: Modifier = Modifier,
    isEmptyLefts: Boolean,
    isEmptyRights: Boolean,
    isInputActive: Boolean,
    weightInput: TextFieldState,
    customTimestamp: Instant?,
    isDateTimePickerOpen: Boolean,
    prediction: String?,
    onGoLeft: () -> Unit,
    onGoRight: () -> Unit,
    onOpenAddMeasurement: () -> Unit,
    onOpenWateringDialog: () -> Unit,
    onOpenPrunDialog: () -> Unit,
    onSaveMeasurement: () -> Unit,
    onCancelMeasurement: () -> Unit,
    onOpenDateTimePicker: () -> Unit,
    onCloseDateTimePicker: () -> Unit,
    onSetTimestamp: (Instant) -> Unit,
    onRePotDialog: () -> Unit,

    ) {
    val focusRequester = remember { FocusRequester() }

    if (isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onCloseDateTimePicker, onSetTimestamp
        )
    }

    LaunchedEffect(isInputActive) {
        if (isInputActive) focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = spacing.medium, vertical = spacing.small
            ), verticalAlignment = Alignment.CenterVertically
    ) {
        if (isInputActive) {
            TextField(
                state = weightInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.pot_mass_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                placeholder = prediction?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onOpenDateTimePicker) {
                            Icon(
                                painterResource(R.drawable.edit_calendar),
                                contentDescription = stringResource(R.string.cd_specify_datetime),
                                tint = if (customTimestamp != null) Theme.colorScheme.primary
                                else Theme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onCancelMeasurement
                        ) {
                            Icon(
                                painterResource(R.drawable.close), contentDescription = stringResource(R.string.cd_cancel)
                            )
                        }

                        FilledIconButton(
                            onSaveMeasurement, enabled = weightInput.text.isNotBlank()
                        ) {
                            Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.cd_save))
                        }
                        Spacer(Modifier.width(spacing.extraSmall))
                    }
                })
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(
                    spacing.small, Alignment.CenterHorizontally
                ), verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onGoLeft,
                    enabled = !isEmptyLefts,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Theme.colorScheme.secondaryContainer,
                        contentColor = Theme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(painterResource(R.drawable.left), contentDescription = stringResource(R.string.cd_left))
                }
                Column(Modifier.weight(1F)) {
                    Button(
                        onOpenAddMeasurement, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painterResource(R.drawable.assignment_add),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(stringResource(R.string.add_measurement))
                    }
                    Row {
                        FilledTonalButton(
                            modifier = Modifier.weight(1F),
                            onClick = onOpenWateringDialog, colors = ButtonDefaults.buttonColors(
                                containerColor = Theme.colorScheme.tertiaryContainer,
                                contentColor = Theme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(
                                painterResource(R.drawable.water_full),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(stringResource(R.string.cu_watered),     maxLines = 1)
                        }
                        Spacer(Modifier.width(spacing.small))
                        FilledIconButton(
                            onClick = onOpenPrunDialog,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Theme.colorScheme.secondaryContainer,
                                contentColor = Theme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.cut),
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(spacing.small))
                        FilledIconButton(
                            onClick = onRePotDialog,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Theme.colorScheme.secondaryContainer,
                                contentColor = Theme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.pot),
                                contentDescription = null
                            )
                        }
                    }

                }
                FilledIconButton(
                    onClick = onGoRight,
                    enabled = !isEmptyRights,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Theme.colorScheme.secondaryContainer,
                        contentColor = Theme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.right), contentDescription = "Вправо"
                    )
                }
            }
        }
    }
}

@Composable
private fun PlantScreenContent(
    modifier: Modifier,
    plant: PlantUi,
    onIntent: (PlantIntent) -> Unit,
    onOpenMeasurementsHistory: () -> Unit,
    onOpenWateringHistory: () -> Unit,
    onRemove: (MeasurementId) -> Unit,
    onCopy: (YearMonth) -> Unit
) = when (plant.records.isEmpty()) {
    true -> {

        Box(
            modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Column {
                Row {
                    Button(
                        onClick = onOpenMeasurementsHistory,
                        modifier = Modifier.weight(1F)
                    ) {
                        Text(stringResource(R.string.view_measurement_history))
                    }
                    Spacer(Modifier.size(spacing.small))

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Theme.colorScheme.secondary),
                        onClick = onOpenWateringHistory,
                        modifier = Modifier.weight(1F),
                        enabled = plant.hasWatering
                    ) {
                        Text(
                            if (plant.hasWatering) {
                                stringResource(R.string.view_watering_history)
                            } else {
                                stringResource(R.string.not_watered_yet)
                            }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.no_meas),
                )
            }

        }
    }

    false -> {
        val records = remember(plant.records) {
            plant.records
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            if (plant.dryingForecast != null) {
                item(key = "analytics_card") {
                    Column {
                        PlantTitle(plant.name)
                        Spacer(modifier.height(spacing.small))
                        DryingChart(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(horizontal = 16.dp),
                            points = plant.activeMeasurementPoints,
                            expFit = (plant.dryingForecast as? DryingForecast.Active)?.expFit

                        )
                        Spacer(modifier.height(spacing.small))
                        UsefulAnalyticsCard(
                            forecast = plant.dryingForecast,
                            moisture = plant.moisture?.percentInt()
                        )
                        Spacer(modifier.height(spacing.small))
                        Row {
                            Button(
                                onClick = onOpenMeasurementsHistory,
                                modifier = Modifier.weight(1F)
                            ) {
                                Text(stringResource(R.string.view_measurement_history))
                            }
                            Spacer(Modifier.size(spacing.small))

                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Theme.colorScheme.secondary),
                                onClick = onOpenWateringHistory,
                                modifier = Modifier.weight(1F),
                                enabled = plant.hasWatering
                            ) {
                                Text(
                                    if (plant.hasWatering) {
                                        stringResource(R.string.view_watering_history)
                                    } else {
                                        stringResource(R.string.not_watered_yet)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            items(records) { record ->
                OneMonthBlock(
                    monthGroup = record,
                    onEdit = {
                        onIntent(
                            PlantIntent.Details.StartEditingMeasurement(
                                it.id,
                                it.weight,
                                timestamp = it.timestamp
                            )
                        )
                    },
                    onRemove = onRemove,
                    onCopy = onCopy
                )
            }
        }
    }
}

@Composable
fun UsefulAnalyticsCard(
    forecast: DryingForecast, moisture: String?, modifier: Modifier = Modifier
) = Card(
    modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = Theme.colorScheme.tertiaryContainer
    )
) {
    Column(
        modifier = Modifier.padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(
                painter = painterResource(R.drawable.analytics),
                contentDescription = null,
                tint = Theme.colorScheme.onTertiaryContainer
            )
            Text(
                text = stringResource(R.string.drying_analytics_title),
                style = Theme.typography.labelLarge,
                color = Theme.colorScheme.onTertiaryContainer
            )
        }

        Text(
            text = forecast.statusText(),
            style = Theme.typography.bodyLarge
        )

        Text(
            text = forecast.daysText(),
            style = Theme.typography.bodyMedium,
        )
        forecast.recommendedInterval?.inWholeHours?.let { hours ->
            Text(text = stringResource(R.string.recommended_weighing_interval, hours))
        }
        moisture?.let { formattedMoisture ->
            Text(text = stringResource(R.string.current_moisture, formattedMoisture))
        }
        val expFit = (forecast as? DryingForecast.Active)?.expFit
        val predictedWeight = expFit?.plateauWeight
        val rmseVal = expFit?.rmse

        if (predictedWeight != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = spacing.small),
                color = Theme.colorScheme.onTertiaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.predicted_plateau_weight, predictedWeight.roundToInt()),
                    style = Theme.typography.bodySmall,
                    color = Theme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.rmse_format, rmseVal ?: 0.0),
                    style = Theme.typography.bodySmall,
                    color = Theme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun OneMonthBlock(
    monthGroup: MonthGroupUiModel,
    onEdit: (RecordUiModel) -> Unit,
    onRemove: (MeasurementId) -> Unit,
    onCopy: (YearMonth) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = monthGroup.monthNameAndYearTitle,
                style = Theme.typography.titleSmall
            )
            Spacer(modifier = Modifier.weight(1F))
            IconButton(onClick = { onCopy(monthGroup.month) }) {
                Icon(
                    painterResource(R.drawable.content_copy),
                    contentDescription = null
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            monthGroup.records.forEach { record ->
                RecordCard(
                    record,
                    onEdit = { onEdit(record) },
                    onRemove = { onRemove(record.id) }
                )
            }
        }
    }
}


@Composable
fun RecordCard(record: RecordUiModel, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.small),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(record.dayOfMonthAndTime, modifier = Modifier.weight(1f))
            VerticalDivider(Modifier.height(24.dp))
            Text(record.weight, modifier = Modifier.weight(1f))
            VerticalDivider(Modifier.height(24.dp))
            Text(record.delta, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.edit), contentDescription = "Редагувати"
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.delete), contentDescription = "Видалити"
                )
            }
        }
    }
}

@Composable
fun PlantAppBar(scrollBehavior: TopAppBarScrollBehavior, onClose: () -> Unit) =
    TopAppBar(
        { },
        navigationIcon = { GoUpButton { onClose() } },
        scrollBehavior = scrollBehavior
    )

@Composable
fun GoUpButton(goUpAction: () -> Unit) = TextButton(goUpAction) {
    Icon(
        painter = painterResource(R.drawable.back),
        contentDescription = "Go up",
    )
    Spacer(Modifier.width(8.dp))
    Text("To Pots")
}

@Composable
fun PlantTitle(title: String) = Text(
    text = title,
    style = Theme.typography.displayLarge.copy(
        fontWeight = FontWeight.ExtraLight
    ),
    modifier = Modifier
        .clipToBounds()
        .wrapContentWidth(align = Alignment.Start, unbounded = true)
)

@Composable
fun EditMeasurementDialog(state: PlantState.PlantModal.EditMeasurement, onIntent: (PlantIntent) -> Unit) {
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
        confirmButton = { TextButton(onClick = { onIntent(PlantIntent.EditingMeasurement.Save) }) { Text(stringResource(R.string.save_button)) } },
        dismissButton = { TextButton(onClick = { onIntent(PlantIntent.Back) }) { Text(stringResource(R.string.cancel_button)) } },
    )
    if (state.isDateTimePickerOpen) {
        MeasurementDateTimePicker(
            onDismiss = { onIntent(PlantIntent.EditingMeasurement.CloseDateTimePicker) },
            onDateTimeSelected = { onIntent(PlantIntent.EditingMeasurement.SetTimestamp(it)) },
        )
    }
}