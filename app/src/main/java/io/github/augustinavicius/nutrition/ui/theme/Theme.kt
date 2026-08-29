package io.github.augustinavicius.nutrition.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Leaf40,
    onPrimary = Color.White,
    primaryContainer = Leaf90,
    onPrimaryContainer = Leaf10,
    secondary = Bark40,
    onSecondary = Color.White,
    secondaryContainer = Bark90,
    onSecondaryContainer = Bark10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    error = Rose40,
    onError = Color.White,
    errorContainer = Rose90,
    onErrorContainer = Rose30,
)

private val DarkColors = darkColorScheme(
    primary = Leaf80,
    onPrimary = Leaf20,
    primaryContainer = Leaf30,
    onPrimaryContainer = Leaf90,
    secondary = Bark80,
    onSecondary = Bark10,
    secondaryContainer = Bark30,
    onSecondaryContainer = Bark90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = Rose80,
    onError = Rose30,
    errorContainer = Rose30,
    onErrorContainer = Rose90,
)

@Composable
fun NutritionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colours, where the platform offers them. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NutritionTypography,
        content = content,
    )
}
