package com.zghonnyk.plateau.feature.main.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zghonnyk.domain.PlantId
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.ui.theme.spacing

@Composable
fun PotsScreen(
    plants: List<PlantUi>,
    editingPlant: PlantState.EditingName?,
    onSelect: (PlantId) -> Unit,
    onEdit: (PlantId) -> Unit,
    onDelete: (PlantId) -> Unit,
    onAddPlantClick: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onSelectSpecies: (Long?) -> Unit,
    pendingDelete: PendingDelete?,
    onUndoDelete: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val lazyListState = rememberLazyListState()
    val isFabExpanded by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PlantsTopBar(
                scrollBehavior = scrollBehavior,
                onExport = onExport,
                onImport = onImport,
            )

        },
        floatingActionButton = {
            AddPlantFab(
                onClick = onAddPlantClick, expanded = isFabExpanded
            )
        }
    ) { innerPadding ->
        PotsContent(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = spacing.large),
            plants = plants,
            pendingDelete = pendingDelete,
            onSelect = onSelect,
            onEdit = onEdit,
            onDelete = onDelete,
            onUndoDelete = onUndoDelete,
            lazyListState = lazyListState
        )


        if (editingPlant != null) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            AlertDialog(
                onDismissRequest = onCancelEdit,
                title = { Text(stringResource(R.string.edit_plant_title)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            state = editingPlant.nameInput,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )

                        ExposedDropdownMenuBox(
                            expanded = editingPlant.isDropdownExpanded,
                            onExpandedChange = { onToggleDropdown(!editingPlant.isDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedSpeciesName =
                                editingPlant.speciesList.find { it.id.value == editingPlant.selectedSpeciesId }?.latinName
                                    ?: "Incertae sedis"

                            OutlinedTextField(
                                value = selectedSpeciesName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = editingPlant.isDropdownExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = editingPlant.isDropdownExpanded,
                                onDismissRequest = { onToggleDropdown(false) }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.unknown_species_label)) },
                                    onClick = { onSelectSpecies(null) })
                                editingPlant.speciesList.forEach { species ->
                                    val displayName = buildString {
                                        append(species.latinName)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(displayName) },
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
                    TextButton(onClick = onCancelEdit) {
                        Text(stringResource(R.string.cancel_button))
                    }
                })
        }
    }
}

@Composable
fun CsvMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.upload), contentDescription = null)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_csv)) },
            onClick = { open = false; onExport() })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_csv)) },
            onClick = { open = false; onImport() })
    }
}

@Composable
fun CsvMenuActions(
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExport) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }

    CsvMenu(
        onExport = { exportLauncher.launch(DEFAULT_CSV_NAME) },
        onImport = { importLauncher.launch(CSV_MIME_TYPES) },
    )
}

private const val DEFAULT_CSV_NAME = "plateau.csv"
private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/vnd.ms-excel",
)

@Preview
@Composable
private fun CsvMenuPreview() = CsvMenu(onExport = {}, onImport = {})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantsTopBar(
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) = CenterAlignedTopAppBar(
    title = { Text(stringResource(R.string.app_name)) },
    actions = {
        CsvMenuActions(
            onExport = onExport,
            onImport = onImport,
        )
    },
    scrollBehavior = scrollBehavior
)

@Composable
fun AddPlantFab(
    modifier: Modifier = Modifier, onClick: () -> Unit, expanded: Boolean
) = ExtendedFloatingActionButton(
    onClick = onClick,
    modifier = modifier,
    expanded = expanded,
    icon = {
        Icon(
            painter = painterResource(R.drawable.add), contentDescription = null
        )
    },
    text = {
        Text(text = stringResource(R.string.add_plant))
    })

@Composable
fun PotsContent(
    modifier: Modifier = Modifier,
    plants: List<PlantUi>,
    pendingDelete: PendingDelete?,
    onSelect: (PlantId) -> Unit,
    onEdit: (PlantId) -> Unit,
    onDelete: (PlantId) -> Unit,
    onUndoDelete: () -> Unit,
    lazyListState: LazyListState
) = LazyColumn(
    modifier = modifier.fillMaxSize(),
    state = lazyListState,
    verticalArrangement = Arrangement.spacedBy(spacing.small)
) {
    items(items = plants, key = { it.id.value }) { plant ->
        val cardState = pendingDelete?.takeIf { it.plantId == plant.id }
            ?.let { PlantCardState.Deletion(it.progress) } ?: PlantCardState.Default

        @Composable
        fun Long.formatElapsedTime(): String {
            return if (this >= 24) {
                stringResource(R.string.elapsed_days, this / 24)
            } else {
                stringResource(R.string.elapsed_hours, this)
            }
        }

        OnePlantItem(
            plantName = plant.name,
            plantSpecieName = plant.latinName,
            photo = plant.plantPhoto,
            elapsedSinceLastWeighing = plant.hoursLeft?.formatElapsedTime()
                ?: stringResource(R.string.weighting_interval_is_not_calculated),
            state = cardState,
            isReadyForAction = plant.isReadyForAction,
            onSelect = { onSelect(plant.id) },
            onEdit = { onEdit(plant.id) },
            onDelete = { onDelete(plant.id) },
            onUndoDelete = onUndoDelete,

            )
    }
}


sealed class OnePlantItemState {

    data class Default(
        val plantName: String,
        val plantSpecieName: String,
        val onSelect: () -> Unit,
        val onEdit: () -> Unit,
        val onDelete: () -> Unit,
    ) : OnePlantItemState()

    data class Deletion(
        val plantName: String,
        val plantSpecieName: String,
        val onBackDeletedPlant: () -> Unit,
        val deleteProgress: Float
    ) : OnePlantItemState()
}


@Immutable
sealed interface PlantCardState {
    data object Default : PlantCardState
    data class Deletion(val progress: Float) : PlantCardState
}

@Composable
fun OnePlantItem(
    plantName: String,
    plantSpecieName: String,
    photo: String?,
    elapsedSinceLastWeighing: String,
    state: PlantCardState,
    isReadyForAction: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val photoFile = rememberPhotoFile(photo)
        if (photo != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(8.dp))
        }
        PlantCard(
            name = plantName,
            species = plantSpecieName,
            modifier = Modifier.weight(1F),
            isReadyForAction = isReadyForAction,
            onClick = if (state is PlantCardState.Default) onSelect else null,
            bottom = {
                if (state is PlantCardState.Deletion) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .graphicsLayer(scaleX = -1f)
                    )
                }
            },
            elapsedSinceLastWeighing = elapsedSinceLastWeighing
        )

        when (state) {
            PlantCardState.Default -> {
                IconButton(
                    onClick = onEdit, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(painterResource(R.drawable.edit), stringResource(R.string.cd_edit_plant))
                }
                IconButton(
                    onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.delete),
                        stringResource(R.string.cd_delete_plant)
                    )
                }
            }

            is PlantCardState.Deletion -> Button(onClick = onUndoDelete) { Text(stringResource(R.string.undo_delete)) }
        }
    }
}

@Composable
private fun PlantCard(
    name: String,
    species: String,
    elapsedSinceLastWeighing: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    isReadyForAction: Boolean,
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    val containerColor = if (isReadyForAction) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        CardDefaults.cardColors().containerColor
    }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.padding(spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(spacing.small))
            Column(verticalArrangement = Arrangement.Center) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(species, style = MaterialTheme.typography.bodyMedium)
                if (elapsedSinceLastWeighing != null) {
                    Text(elapsedSinceLastWeighing)
                }
            }
        }
        bottom()
    }

    if (onClick != null) Card(onClick = onClick, modifier = modifier, content = body)
    else Card(modifier = modifier, content = body, colors = colors)
}