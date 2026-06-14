package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * App-wide theme. [isDarkTheme] selects the legacy dashboard palette vs the
 * clinical light palette; preference is persisted via [ThemePreferenceStore].
 */
@Composable
fun AppTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkTheme) AppPalettes.dark else AppPalettes.light
    MaterialTheme(
        colorScheme = palette.toMaterialColorScheme(isDarkTheme),
        content = {
            CompositionLocalProvider(LocalAppColors provides palette) {
                content()
            }
        }
    )
}
