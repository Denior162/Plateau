package com.zghonnyk.plateau.feature.main.ui.screen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Wallpapers
import coil.compose.AsyncImage
import com.zghonnyk.domain.Species
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.state.AddPlantForm
import com.zghonnyk.plateau.feature.main.ui.state.SpeciesSelection
import com.zghonnyk.ui.theme.spacing
import java.io.File

@Composable
fun AddPlantScreen(
    form: AddPlantForm,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    speciesList: List<Species>,
    onSelectionChange: (SpeciesSelection) -> Unit,
    onUnknownSpecies: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AddPlantTopBar(onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = spacing.large, vertical = spacing.medium)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            PlantPhotoPickerSection(onAddPhoto = onAddPhoto, form.photoPath)
            PlantNameField(state = form.customName)
            SpeciesDropdownField(
                isExpanded = form.isDropdownExpanded,
                onToggleDropdown = onToggleDropdown,
                selection = form.speciesSelection,
                speciesList = speciesList,
                onSelectionChange = onSelectionChange,
                onUnknownSpecies = onUnknownSpecies
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSave, modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Plant")
            }
        }
    }
}

@Composable
private fun PlantPhotoPickerSection(
    onAddPhoto: (Uri) -> Unit, addedPicture: String?
) {
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddPhoto(uri)
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }
    Row {
        AddPlantPhotoButton(
            onClick = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            })


        val photoFile = rememberPhotoFile(addedPicture)

        if (photoFile != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = spacing.massive, height = spacing.gigantic)
                    .clip(RoundedCornerShape(spacing.medium))
                    .clickable {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            )
        }

    }

}
@Composable
fun rememberPhotoFile(relativePath: String?): File? {
    val context = LocalContext.current
    return remember(relativePath) {
        relativePath?.let { File(context.filesDir, it) }
    }
}
@Composable
private fun PlantNameField(
    state: TextFieldState, modifier: Modifier = Modifier
) {
    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text("Write plant name there") },
        lineLimits = TextFieldLineLimits.SingleLine
    )
}

@Composable
private fun SpeciesDropdownField(
    isExpanded: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    selection: SpeciesSelection,
    speciesList: List<Species>,
    onSelectionChange: (SpeciesSelection) -> Unit,
    onUnknownSpecies: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded, onExpandedChange = onToggleDropdown, modifier = modifier
    ) {
        SpeciesSelectorRow(
            selection = selection, isExpanded = isExpanded, onUnknownSpecies = onUnknownSpecies
        )
        ExposedDropdownMenu(
            expanded = isExpanded, onDismissRequest = { onToggleDropdown(false) }) {
            speciesList.forEach { species ->
                DropdownMenuItem(text = { Text(species.latinName) }, onClick = {
                    onSelectionChange(SpeciesSelection.Selected(species))
                    onToggleDropdown(false)
                })
            }
        }
    }
}

@Composable
private fun ExposedDropdownMenuBoxScope.SpeciesSelectorRow(
    selection: SpeciesSelection, isExpanded: Boolean, onUnknownSpecies: () -> Unit
) {
    val label = when (selection) {
        SpeciesSelection.Unselected -> stringResource(R.string.species_unselected)
        SpeciesSelection.Unknown -> stringResource(R.string.species_unknown)
        is SpeciesSelection.Selected -> selection.species.latinName
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true, trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
        }, modifier = Modifier.menuAnchor(
            type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true
        )
        )
        IDoNotKnowSpeciesButton(onClick = onUnknownSpecies)
    }
}

@Composable
fun IDoNotKnowSpeciesButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(stringResource(R.string.i_dont_know))
    }
}

@Composable
private fun AddPlantTopBar(
    onBack: () -> Unit
) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.adding_plant)) }, navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.back),
                contentDescription = stringResource(R.string.go_back)
            )
        }
    }, expandedHeight = spacing.gigantic
    )
}

@Composable
fun AddPlantPhotoButton(onClick: () -> Unit) = IconButton(
    onClick = onClick,
    modifier = Modifier.size(width = spacing.massive, height = spacing.gigantic),
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
) {
    Icon(
        painterResource(R.drawable.add_photo),
        contentDescription = stringResource(R.string.add_picture)
    )
}


@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Composable
fun AddPlantButtonPreview() {
    MaterialTheme {
        AddPlantPhotoButton {}
    }
}