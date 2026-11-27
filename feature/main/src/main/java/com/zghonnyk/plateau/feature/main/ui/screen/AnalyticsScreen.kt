package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zghonnyk.domain.MonthlyStatItem
import com.zghonnyk.plateau.feature.main.R
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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