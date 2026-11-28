package com.zghonnyk.plateau.feature.main.ui.screen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.zghonnyk.domain.AddPlantForm
import com.zghonnyk.domain.Species
import com.zghonnyk.domain.SpeciesSelection
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.L0
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.L1
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestButton
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestContainer
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestRow
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestScope
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.NestScreen
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.S
import com.zghonnyk.plateau.feature.main.ui.nesting_experiments.containerColor
import com.zghonnyk.ui.theme.PlateauTheme
import com.zghonnyk.ui.theme.spacing
import java.io.File

@Composable
fun AddPlantScreen(
    form: AddPlantForm,
    onBack: () -> Unit,
    onSave: (name: String) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    speciesList: List<Species>,
    onSelectionChange: (SpeciesSelection) -> Unit,
    onUnknownSpecies: () -> Unit = {},
) {
    val name = rememberTextFieldState("")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AddPlantTopBar(onBack = onBack) },
    ) { innerPadding ->
        NestScreen(
            level = S(S(L0)), // L2
            modifier = Modifier.padding(innerPadding),
        ) { scope ->
            with(scope) {
                // 1. L2 -> L1
                PlantPhotoPickerSection(
                    onAddPhoto = onAddPhoto,
                    addedPicture = form.photoPath,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                // 2. L2 -> L1
                NestContainer(modifier = Modifier.fillMaxWidth()) {
                    PlantNameField(state = name)
                    SpeciesDropdownContent(
                        isExpanded = form.isDropdownExpanded,
                        onToggleDropdown = onToggleDropdown,
                        selection = form.speciesSelection,
                        speciesList = speciesList,
                        onSelectionChange = onSelectionChange,
                        onUnknownSpecies = onUnknownSpecies,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            with(scope) {
                // 3. L2 -> L1 (кнопка внутри того же дерева)
                NestContainer(modifier = Modifier.fillMaxWidth()) {
                    SavePlantButton(onClick = { onSave(name.text.toString()) })
                }
            }
        }
    }
}

@Composable
private fun NestScope<L1>.SavePlantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NestButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Add Plant")
    }
}

@Composable
private fun NestScope<S<S<L0>>>.PlantPhotoPickerSection(
    onAddPhoto: (Uri) -> Unit,
    addedPicture: String?,
    modifier: Modifier = Modifier,
) {
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddPhoto(uri)
        else Log.d("PhotoPicker", "No media selected")
    }

    val launchPicker = {
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    NestRow(
        alignment = Alignment.CenterHorizontally,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddPlantPhotoButton(onClick = launchPicker)

        val photoFile = rememberPhotoFile(addedPicture)
        if (photoFile != null) {
            PlantPhotoPreview(
                photoFile = photoFile,
                onClick = launchPicker,
            )
        }
    }
}

@Composable
private fun NestScope<L1>.AddPlantPhotoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    onClick = onClick,
    modifier = modifier.size(width = spacing.massive, height = spacing.gigantic),
    shape = shape,
    color = containerColor(),
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.add_photo),
            contentDescription = stringResource(R.string.add_picture),
        )
    }
}

@Composable
private fun NestScope<L1>.PlantPhotoPreview(
    photoFile: File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = AsyncImage(
    model = photoFile,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = modifier
        .size(width = spacing.massive, height = spacing.gigantic)
        .clip(shape)
        .clickable(onClick = onClick),
)

@Composable
fun rememberPhotoFile(relativePath: String?): File? {
    val context = LocalContext.current
    return remember(relativePath) {
        relativePath?.let { File(context.filesDir, it) }
    }
}

@Composable
private fun NestScope<L1>.PlantNameField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        label = { Text("Write plant name there") },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

@Composable
private fun NestScope<L1>.SpeciesDropdownContent(
    isExpanded: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    selection: SpeciesSelection,
    speciesList: List<Species>,
    onSelectionChange: (SpeciesSelection) -> Unit,
    onUnknownSpecies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldScope = this
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onToggleDropdown,
        modifier = modifier.fillMaxWidth(),
    ) {
        SpeciesSelectorRow(
            nest = fieldScope,
            selection = selection,
            isExpanded = isExpanded,
            onUnknownSpecies = onUnknownSpecies,
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onToggleDropdown(false) },
        ) {
            speciesList.forEach { species ->
                DropdownMenuItem(
                    text = { Text(species.latinName) },
                    onClick = {
                        onSelectionChange(SpeciesSelection.Selected(species))
                        onToggleDropdown(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun ExposedDropdownMenuBoxScope.SpeciesSelectorRow(
    nest: NestScope<L1>,
    selection: SpeciesSelection,
    isExpanded: Boolean,
    onUnknownSpecies: () -> Unit,
) {
    val label = when (selection) {
        SpeciesSelection.Unselected -> stringResource(R.string.species_unselected)
        SpeciesSelection.Unknown -> stringResource(R.string.species_unknown)
        is SpeciesSelection.Selected -> selection.species.latinName
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(nest.padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            shape = nest.shape,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryEditable,
                    enabled = true,
                ),
        )
        nest.IDoNotKnowSpeciesButton(onClick = onUnknownSpecies)
    }
}

@Composable
private fun NestScope<L1>.IDoNotKnowSpeciesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NestButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.i_dont_know))
    }
}

@Composable
private fun AddPlantTopBar(
    onBack: () -> Unit,
) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.adding_plant)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = stringResource(R.string.go_back),
                )
            }
        },
        expandedHeight = spacing.gigantic,
    )
}

// TODO: добавьте хотя бы один тестовый Species, иначе Selected/Dark превью упадут на first()
private val previewSpecies = emptyList<Species>()

@Composable
private fun AddPlantPreviewHost(
    form: AddPlantForm,
    darkTheme: Boolean = false,
) = PlateauTheme(darkTheme = darkTheme) {
    AddPlantScreen(
        form = form,
        onBack = {},
        onSave = {},
        onAddPhoto = {},
        onToggleDropdown = {},
        speciesList = previewSpecies,
        onSelectionChange = {},
        onUnknownSpecies = {},
    )
}

@Preview(name = "1. Empty", showBackground = true)
@Composable
private fun AddPlantEmptyPreview() = AddPlantPreviewHost(
    form = AddPlantForm(
        photoPath = null,
        isDropdownExpanded = false,
        speciesSelection = SpeciesSelection.Unselected,
    )
)

@Preview(name = "2. Selected species", showBackground = true)
@Composable
private fun AddPlantSelectedPreview() = AddPlantPreviewHost(
    form = AddPlantForm(
        photoPath = null,
        isDropdownExpanded = false,
        speciesSelection = SpeciesSelection.Selected(previewSpecies.first()),
    )
)

@Preview(name = "3. Unknown species", showBackground = true)
@Composable
private fun AddPlantUnknownPreview() = AddPlantPreviewHost(
    form = AddPlantForm(
        photoPath = null,
        isDropdownExpanded = false,
        speciesSelection = SpeciesSelection.Unknown,
    )
)

@Preview(name = "4. Dropdown expanded", showBackground = true)
@Composable
private fun AddPlantDropdownExpandedPreview() = AddPlantPreviewHost(
    form = AddPlantForm(
        photoPath = null,
        isDropdownExpanded = true,
        speciesSelection = SpeciesSelection.Unselected,
    )
)

@Preview(name = "5. Dark theme", showBackground = true)
@Composable
private fun AddPlantDarkPreview() = AddPlantPreviewHost(
    form = AddPlantForm(
        photoPath = null,
        isDropdownExpanded = false,
        speciesSelection = SpeciesSelection.Selected(previewSpecies.first()),
    ),
    darkTheme = true,
)