package com.zghonnyk.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PlateauTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    MaterialTheme(dynamicColorScheme(darkTheme, context), typography = Typography, content = content
    )
}

fun dynamicColorScheme(darkTheme: Boolean, context: Context) =
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
