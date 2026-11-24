package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zghonnyk.domain.MonthlyStatItem
import com.zghonnyk.domain.PlantPeakWeight
import com.zghonnyk.domain.Species
import com.zghonnyk.domain.SpeciesMeasurementStats
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.ui.theme.spacing
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Composable
fun AnalyticsScreen(
    globalMedianFlow: Flow<Double>,
    speciesStatsFlow: Flow<List<SpeciesMeasurementStats>>,
    speciesList: List<Species>,
    onGetEvaporatedForSpecies: (Long) -> Flow<Double>,
    onGetPlantsWithMinWeight: (Double) -> Flow<List<PlantPeakWeight>>,
    onGetMonthlyStats: (Long, Long) -> Flow<List<MonthlyStatItem>>,
    onBack: () -> Unit
) {
    val globalMedianEvaporation by globalMedianFlow.collectAsStateWithLifecycle(initialValue = 0.0)
    val speciesStats by speciesStatsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedSpeciesId by remember(speciesList) { mutableStateOf(speciesList.firstOrNull()?.id?.value) }
    val evaporatedFlow = remember(selectedSpeciesId) {
        selectedSpeciesId?.let { onGetEvaporatedForSpecies(it) }
    }
    val evaporatedGrams by (evaporatedFlow?.collectAsStateWithLifecycle(initialValue = 0.0)
        ?: remember { mutableDoubleStateOf(0.0) })

    var minWeightThreshold by remember { mutableFloatStateOf(500f) }
    val minWeightPlantsFlow = remember(minWeightThreshold) {
        onGetPlantsWithMinWeight(minWeightThreshold.toDouble())
    }
    val minWeightPlants by minWeightPlantsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.back),
                            contentDescription = stringResource(R.string.analytics_back),
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.analytics_global_title),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.analytics_global_median,
                                globalMedianEvaporation
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.analytics_global_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.analytics_moisture_loss_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        if (speciesList.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedSpecies =
                                speciesList.find { it.id.value == selectedSpeciesId }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedSpecies?.latinName
                                        ?: stringResource(R.string.analytics_select_species),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = expanded
                                        )
                                    },
                                    modifier = Modifier
                                        .menuAnchor(
                                            type = ExposedDropdownMenuAnchorType.PrimaryEditable,
                                            enabled = true
                                        )
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    speciesList.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.latinName) },
                                            onClick = {
                                                selectedSpeciesId = s.id.value
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(spacing.small))
                            Text(
                                text = stringResource(
                                    R.string.analytics_total_evaporated,
                                    evaporatedGrams
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Text(
                                stringResource(R.string.analytics_species_empty),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.analytics_species_stats_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.analytics_species_stats_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        if (speciesStats.isEmpty()) {
                            Text(
                                stringResource(R.string.analytics_insufficient_data),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            speciesStats.forEach { stat ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            stat.speciesName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.analytics_stat_plants,
                                                    stat.activePlantsCount
                                                ), style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.analytics_stat_measurements,
                                                    stat.totalMeasurements
                                                ), style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.analytics_stat_avg_weight,
                                                    stat.avgWeightGrams ?: 0.0
                                                ), style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.analytics_stat_max_weight,
                                                    stat.maxWeightGrams ?: 0.0
                                                ), style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.analytics_large_plants_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.analytics_min_peak_weight,
                                minWeightThreshold.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            state = rememberSliderState(
                                value = minWeightThreshold,
                                steps = 29, trackRange = 100f..3000f
                            ), onValueChange = { minWeightThreshold = it })
                        Spacer(Modifier.height(8.dp))

                        if (minWeightPlants.isEmpty()) {
                            Text(
                                stringResource(
                                    R.string.analytics_no_large_plants,
                                    minWeightThreshold.toInt()
                                ), style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            minWeightPlants.forEach { plant ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            plant.plantName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            plant.speciesName ?: "—",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${plant.peakWeight ?: 0.0} g",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(
                                                R.string.analytics_plant_measurements,
                                                plant.totalMeasurements
                                            ), style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            item {
                MonthlyStatsCard(onGetMonthlyStats)
            }
        }
    }
}

@Composable
fun MonthlyStatsCard(
    onGetMonthlyStats: (Long, Long) -> Flow<List<MonthlyStatItem>>
) {
    val calendar = Calendar.getInstance()
    val toTs = calendar.timeInMillis
    calendar.add(Calendar.YEAR, -1)
    val fromTs = calendar.timeInMillis

    val stats by onGetMonthlyStats(fromTs, toTs).collectAsState(initial = emptyList())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_dynamics_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Button(onClick = { }) {
                    Text(stringResource(R.string.analytics_from_date))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { }) {
                    Text(stringResource(R.string.analytics_to_date))
                }
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            if (stats.isEmpty()) {
                Text(
                    stringResource(R.string.analytics_no_data_period),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                stats.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stat.monthYear, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(
                                R.string.analytics_period_summary,
                                stat.totalMeasurements
                            )
                        )
                    }
                }
            }
        }
    }
}