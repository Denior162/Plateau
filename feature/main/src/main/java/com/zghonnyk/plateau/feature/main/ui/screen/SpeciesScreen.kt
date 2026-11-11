package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zghonnyk.domain.Species
import com.zghonnyk.plateau.feature.main.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesScreen(
    speciesList: List<Species>,
    onBack: () -> Unit
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Види рослин (Species)") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(
                            R.drawable.back
                        ),
                        contentDescription = "повернутися",
                    )
                }
            }
        )
    }
) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(speciesList) { species ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ID: ${species.id}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}