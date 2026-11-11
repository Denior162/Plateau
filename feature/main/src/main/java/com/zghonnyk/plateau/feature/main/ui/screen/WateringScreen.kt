package com.zghonnyk.plateau.feature.main.ui.screen

import androidx.compose.foundation.layout.Column
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
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.state.HistoryUIModel
import com.zghonnyk.ui.theme.spacing

@Composable
fun HistoryWatersScreen(onBack: () -> Unit, screenName: String, elements: List<HistoryUIModel>) =
    HistoryContent(onBack, screenName, elements)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(onBack: () -> Unit, screenName: String, elements: List<HistoryUIModel>) {
    Scaffold(topBar = {
        TopAppBar(
            { Text(screenName) },
            navigationIcon = {
                IconButton(onBack) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        ""
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
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.small),
                    ) {
                        Text(
                            text = "${item.date} — ${item.usefulData}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Cardec(elements: List<HistoryUIModel>): Unit = when (elements.isEmpty()) {
    true -> Unit

    false -> {
        val head = elements.first()
        val tail = elements.drop(1)
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.small),
            ) {
                Text(
                    text = "${head.date} — ${head.usefulData}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Cardec(elements = tail)
            }
        }

    }
}

