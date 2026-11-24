package com.zghonnyk.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PlateauTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicColorScheme(darkTheme, context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@RequiresApi(Build.VERSION_CODES.S)
fun dynamicColorScheme(darkTheme: Boolean, context: Context) =
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()