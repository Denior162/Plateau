package com.zghonnyk.plateau.ui.previews

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.plateau.feature.main.ui.screen.Cardec
import com.zghonnyk.plateau.feature.main.ui.state.MeasurementUiModel

@Preview(showBackground = true)
@Composable
fun CardecPreview() {
    MaterialTheme {
        Cardec(
            elements = listOf(
                MeasurementUiModel(
                    id = MeasurementId(1),
                    formattedDate = "30.10.2026",
                    formattedWeight = "2450 г"
                ),
                MeasurementUiModel(
                    id = MeasurementId(2),
                    formattedDate = "29.10.2026",
                    formattedWeight = "2430 г"
                ),
                MeasurementUiModel(
                    id = MeasurementId(3),
                    formattedDate = "28.10.2026",
                    formattedWeight = "2415 г"
                ),
                MeasurementUiModel(
                    id = MeasurementId(4),
                    formattedDate = "27.10.2026",
                    formattedWeight = "2390 г"
                ),
                MeasurementUiModel(
                    id = MeasurementId(5),
                    formattedDate = "26.10.2026",
                    formattedWeight = "2368 г"
                )
            )
        )
    }
}