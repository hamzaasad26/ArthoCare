package org.example.project

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Paints the root background gradient using the active [LocalAppColors] tokens.
 * Scaffolds that use a transparent container show this wash from [AppNavigation].
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val colors = LocalAppColors.current
    return this.background(
        Brush.verticalGradient(
            colors = listOf(colors.gradientTop, colors.gradientBottom),
            startY = 0f,
            endY = 1600f
        )
    )
}
