package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import com.zghonnyk.domain.Species
import com.zghonnyk.domain.SpeciesId
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.state.AddPlantForm
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlantScreen(
    form: AddPlantForm,
    onIntent: (PlantIntent) -> Unit,
    onAddPhoto: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    selectedSpeciesName: String?,
    speciesList: List<Species>,
    onSelectSpecies: (SpeciesId?) -> Unit

) = Scaffold(
    modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.adding_plant)) }, navigationIcon = {
            IconButton(onClick = { onIntent(PlantIntent.Back) }) {
                Icon(painterResource(R.drawable.back), null)
                //back to pots if everything is empty and if something is entered close cross button
                // the logic will be more complex like saving draft to buffer
                // and 10 second message on main pots screen to back to creating but not now
            }
        })
    }) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(horizontal = spacing.large, vertical = spacing.medium)
            .fillMaxWidth()
            .navigationBarsPadding()

    ) {
        AddPlantPhotoButton(onAddPhoto = onAddPhoto)
        TextField(
            form.customName,
            modifier = Modifier,
            label = { Text("Write plant name there") },
            lineLimits = TextFieldLineLimits.SingleLine,
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Button(
            onClick = { onIntent(PlantIntent.Creating.Save) }, modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Plant")
        }
        ExposedDropdownMenuBox(
            expanded = form.isDropdownExpanded,
            onExpandedChange = onToggleDropdown,
            modifier = Modifier.fillMaxWidth()
        ) {

                Extracted(selectedSpeciesName, form)

            ExposedDropdownMenu(
                expanded = form.isDropdownExpanded,
                onDismissRequest = { onToggleDropdown(false) }) {
                speciesList.forEach { species ->
                    DropdownMenuItem(
                        text = { Text(species.latinName) },
                        onClick = { onSelectSpecies(species.id) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExposedDropdownMenuBoxScope.Extracted(
    selectedSpeciesName: String?,
    form: AddPlantForm
) {
    Row {
        OutlinedTextField(
            value = selectedSpeciesName ?: " вид не обрано",
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = form.isDropdownExpanded)
            },
            modifier = Modifier
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryEditable,
                    enabled = true
                )
                .weight(1F)
        )
        IDoNotKnowSpeciesButton()
    }
}

@Composable
fun IDoNotKnowSpeciesButton() {
Button({TODO()}) { Text("I don't know")}}

@Composable
fun AddPlantPhotoButton(onAddPhoto: () -> Unit) =
    IconButton(
        onClick = onAddPhoto,
        modifier = Modifier.size(width = spacing.massive, height = spacing.gigantic), colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer

            )
    ) { Icon(painterResource(R.drawable.add_photo), stringResource(R.string.add_picture)) }

@Preview(wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Composable
fun AddPlantButtonPreview() = MaterialTheme {
    AddPlantPhotoButton { }
}