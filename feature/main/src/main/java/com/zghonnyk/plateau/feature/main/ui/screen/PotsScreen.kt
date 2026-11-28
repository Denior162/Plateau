package com.zghonnyk.plateau.feature.main.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.state.DELETE_DELAY_MS
import com.zghonnyk.domain.state.PendingDelete
import com.zghonnyk.domain.state.PlantState
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.L3
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.L4
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.Level
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestButton
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestCard
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestGroupRow
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestRoot
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestScope
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.S
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.inner
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.nestPaddingHorizontal
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.ui.theme.Nest
import com.zghonnyk.ui.theme.spacing
import kotlinx.coroutines.launch
import java.io.File

private const val CSV_FILE_NAME = "plateau.csv"

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
    onSave: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onSelectSpecies: (Long?) -> Unit,
    pendingDeletes: List<PendingDelete>,
    onUndoDelete: (PlantId) -> Unit,
    onAddPhoto: (PlantId, Uri) -> Unit
) {
    val name = rememberTextFieldState(editingPlant?.nameInput ?: "")
    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()
    val lazyListState = rememberLazyListState()
    val isFabExpanded by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PlantsSearchTopBar(
                searchBarState = searchBarState,
                textFieldState = searchFieldState,
                onExport = onExport,
                onImport = onImport,
                plants = plants,
                onSelectPlant = onSelect,
                modifier = Modifier,
            )
        },
        floatingActionButton = {
            AddPlantFab(
                onClick = onAddPlantClick, expanded = isFabExpanded
            )
        },
    ) { innerPadding ->
        NestRoot {
            inner().PotsContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .nestPaddingHorizontal(this),
                plants = plants,
                pendingDeletes = pendingDeletes,
                onSelect = onSelect,
                onEdit = onEdit,
                onDelete = onDelete,
                onUndoDelete = onUndoDelete,
                lazyListState = lazyListState,
                onAddPhoto = onAddPhoto,
            )
        }


        editingPlant?.let {
            EditPlantDialog(
                it, onToggleDropdown, onSelectSpecies,
                { onSave(name.text.toString()) }, onCancel = onCancelEdit
            )
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
        onExport = { exportLauncher.launch(CSV_FILE_NAME) },
        onImport = { importLauncher.launch(CSV_MIME_TYPES) },
    )
}

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/vnd.ms-excel",
)

@Preview
@Composable
private fun CsvMenuPreview() = CsvMenu(onExport = {}, onImport = {})


@Composable
fun PlantsSearchTopBar(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    plants: List<PlantUi>,
    onSelectPlant: (PlantId) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val collapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }
    val inputField: @Composable () -> Unit = {
        PlantSearchInputField(searchBarState, textFieldState, collapse, onExport, onImport)
    }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = spacing.large, vertical = spacing.small),
    )
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        PlantSearchResults(
            plants = filterPlants(plants, textFieldState.text),
            onSelect = { collapse(); onSelectPlant(it) },
        )
    }
}

private fun filterPlants(plants: List<PlantUi>, query: CharSequence): List<PlantUi> =
    if (query.isBlank()) plants
    else plants.filter {
        it.name.contains(query, ignoreCase = true) ||
                it.latinName.contains(query, ignoreCase = true)
    }

private val SEARCH_PHOTO_NEST = Nest(2)

@Composable
private fun PlantSearchResults(
    plants: List<PlantUi>,
    onSelect: (PlantId) -> Unit,
) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(spacing.medium),
    verticalArrangement = Arrangement.spacedBy(spacing.small),
) {
    items(plants, key = { it.id.value }) { plant ->
        ListItem(
            modifier = Modifier.clickable { onSelect(plant.id) },
            leadingContent = {
                rememberPhotoFile(plant.plantPhoto)?.let {
                    PlantPhoto(it, spacing.large, SEARCH_PHOTO_NEST.innerShape)
                }
            },
            supportingContent = { Text(plant.latinName) },
            content = { Text(plant.name) },
        )
    }
}

@Composable
private fun PlantSearchInputField(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    onCollapse: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
) = SearchBarDefaults.InputField(
    textFieldState = textFieldState,
    searchBarState = searchBarState,
    onSearch = { onCollapse() },
    leadingIcon = {
        when (searchBarState.targetValue) {
            SearchBarValue.Expanded -> IconButton(
                onClick = { textFieldState.clearText(); onCollapse() },
            ) {
                Icon(painterResource(R.drawable.back), stringResource(R.string.go_back))
            }

            SearchBarValue.Collapsed -> Icon(
                painterResource(R.drawable.pot),
                contentDescription = null
            )
        }
    },
    trailingIcon = {
        when {
            textFieldState.text.isNotEmpty() -> IconButton(onClick = { textFieldState.clearText() }) {
                Icon(painterResource(R.drawable.close), stringResource(R.string.cd_cancel))
            }

            searchBarState.targetValue == SearchBarValue.Collapsed ->
                CsvMenuActions(onExport, onImport)
        }
    },
)

@Composable
fun AddPlantFab(onClick: () -> Unit, expanded: Boolean) = ExtendedFloatingActionButton(
    onClick = onClick,
    expanded = expanded,
    icon = { Icon(painterResource(R.drawable.add), contentDescription = null) },
    text = { Text(stringResource(R.string.add_plant)) },
)

private const val HOURS_PER_DAY = 24

sealed interface PlantCardState {
    data object Default : PlantCardState
    data class Deletion(val deadlineMs: Long) : PlantCardState
}

@Composable
private fun Long.formatElapsedTime(): String = when {
    this >= HOURS_PER_DAY -> stringResource(R.string.elapsed_days, this / HOURS_PER_DAY)
    else -> stringResource(R.string.elapsed_hours, this)
}

@Composable
private fun plantCardColors(isReadyForAction: Boolean): CardColors =
    (if (isReadyForAction) MaterialTheme.colorScheme.tertiaryContainer
    else CardDefaults.cardColors().containerColor).let { container ->
        CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = contentColorFor(container),
        )
    }

@Composable
fun NestScope<L4>.PotsContent(
    modifier: Modifier,
    plants: List<PlantUi>,
    pendingDeletes: List<PendingDelete>,
    onSelect: (PlantId) -> Unit,
    onEdit: (PlantId) -> Unit,
    onDelete: (PlantId) -> Unit,
    onUndoDelete: (PlantId) -> Unit,
    onAddPhoto: (PlantId, Uri) -> Unit,
    lazyListState: LazyListState,
) = LazyColumn(
    modifier = modifier.fillMaxSize(),
    state = lazyListState,
    verticalArrangement = Arrangement.spacedBy(padding),
    contentPadding = PaddingValues(bottom = spacing.massive),
) {
    items(items = plants, key = { it.id.value }) { plant ->
        inner().PlantItem(
            plant = plant,
            photoFile = rememberPhotoFile(plant.plantPhoto),
            state = pendingDeletes.firstOrNull { it.plantId == plant.id }
                ?.let { PlantCardState.Deletion(it.deadlineMs) } ?: PlantCardState.Default,
            onSelect = { onSelect(plant.id) },
            onEdit = { onEdit(plant.id) },
            onDelete = { onDelete(plant.id) },
            onUndoDelete = { onUndoDelete(plant.id) },
            onAddPhoto = { uri -> onAddPhoto(plant.id, uri) },
            modifier = Modifier,
        )
    }
}

@Composable
private fun <P : Level> NestScope<S<P>>.AddPhotoButton(
    onPicked: (Uri) -> Unit,
    enabled: Boolean,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPicked) }

    FilledTonalIconButton(
        onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        enabled = enabled,
        modifier = Modifier.size(spacing.huge),
        shape = shape,
    ) {
        Icon(painterResource(R.drawable.add_photo), stringResource(R.string.add_photo))
    }
}

@Composable
private fun PlantPhoto(file: File, size: Dp, shape: Shape) = AsyncImage(
    model = file,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .size(size)
        .clip(shape),
)

@Composable
fun NestScope<L3>.PlantItem(
    plant: PlantUi,
    photoFile: File?,
    state: PlantCardState,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    onAddPhoto: (Uri) -> Unit,
    modifier: Modifier,
) = NestGroupRow(Alignment.Start, modifier, Alignment.CenterVertically) { s ->
    val active = state is PlantCardState.Default

    if (photoFile == null) s.AddPhotoButton(onPicked = onAddPhoto, enabled = active)

    NestCard(
        s = s,
        onClick = onSelect,
        enabled = active,
        colors = plantCardColors(plant.isReadyForAction),
        modifier = Modifier.weight(1f),
    ) { _ ->
        s.NestGroupRow(Alignment.Start, Modifier.fillMaxWidth(), Alignment.CenterVertically) { l1 ->
            if (photoFile != null) PlantPhoto(photoFile, spacing.huge, l1.shape)
            Column(Modifier.weight(1f)) {
                Text(plant.name, style = MaterialTheme.typography.bodyLarge)
                Text(plant.latinName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = plant.hoursLeft?.formatElapsedTime()
                        ?: stringResource(R.string.weighting_interval_is_not_calculated),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state is PlantCardState.Deletion) DeletionProgress(state.deadlineMs)
    }

    when (state) {
        PlantCardState.Default -> {
            FilledIconButton(
                onClick = onEdit,
                shape = s.shape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Icon(painterResource(R.drawable.edit), stringResource(R.string.cd_edit_plant)) }
            FilledIconButton(
                onClick = onDelete,
                shape = s.shape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Icon(painterResource(R.drawable.delete), stringResource(R.string.cd_delete_plant)) }
        }
        is PlantCardState.Deletion ->
            s.NestButton(onClick = onUndoDelete, modifier = Modifier) {
                Text(stringResource(R.string.undo_delete))
            }
    }
}

@Composable
private fun DeletionProgress(deadlineMs: Long) {
    val progress = remember(deadlineMs) { Animatable(fractionLeft(deadlineMs)) }
    LaunchedEffect(deadlineMs) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = remainingMs(deadlineMs).toInt(),
                easing = LinearEasing,
            ),
        )
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.extraSmall)
            .graphicsLayer(scaleX = -1f),
    )
}

@Composable
private fun PlantNameField(state: TextFieldState) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun SpeciesDropdown(
    editing: PlantState.EditingName,
    onToggle: (Boolean) -> Unit,
    onSelect: (Long?) -> Unit,
) = ExposedDropdownMenuBox(
    expanded = editing.isDropdownExpanded,
    onExpandedChange = onToggle,
    modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = editing.speciesList
            .find { it.id.value == editing.selectedSpeciesId }
            ?.latinName
            ?: stringResource(R.string.unknown_species_label),
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(editing.isDropdownExpanded) },
        modifier = Modifier
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth(),
    )
    ExposedDropdownMenu(
        expanded = editing.isDropdownExpanded,
        onDismissRequest = { onToggle(false) },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.unknown_species_label)) },
            onClick = { onSelect(null) },
        )
        editing.speciesList.forEach { species ->
            DropdownMenuItem(
                text = { Text(species.latinName) },
                onClick = { onSelect(species.id.value) },
            )
        }
    }
}

@Composable
private fun EditPlantDialog(
    editing: PlantState.EditingName,
    onToggleDropdown: (Boolean) -> Unit,
    onSelectSpecies: (Long?) -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val name = rememberTextFieldState(editing.nameInput)
    AlertDialog(

        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.edit_plant_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlantNameField(name)
                SpeciesDropdown(editing, onToggleDropdown, onSelectSpecies)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.text.toString()) }) {
                Text(
                    stringResource(R.string.save_button)
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_button)) } },
    )
}

private fun remainingMs(deadlineMs: Long): Long =
    (deadlineMs - System.currentTimeMillis()).coerceIn(0L, DELETE_DELAY_MS)

private fun fractionLeft(deadlineMs: Long): Float =
    remainingMs(deadlineMs) / DELETE_DELAY_MS.toFloat()
