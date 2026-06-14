package org.example.project

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private val splashGlowDiameter = 260.dp
private val splashLogoWidth = 180.dp

@Composable
fun SplashScreen(onNavigateToLogin: () -> Unit) {
    val palette = LocalAppColors.current
    val splashBackground = palette.gradientBottom
    var fadeStarted by remember { mutableStateOf(false) }
    val fadeAlpha by animateFloatAsState(
        targetValue = if (fadeStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "splashFade"
    )

    LaunchedEffect(Unit) {
        fadeStarted = true
        delay(1500L)
        onNavigateToLogin()
    }

    val primaryGlow = palette.linkAccent.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fadeAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(splashGlowDiameter)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryGlow, Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension / 2f
                            ),
                            radius = size.minDimension / 2f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = "ArthoCare logo",
                        colorFilter = ColorFilter.tint(palette.sectionHighlight),
                        modifier = Modifier
                            .width(splashLogoWidth)
                            .wrapContentHeight(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
            Text(
                text = "ArthoCare © — FYP 2025",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceMuted.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
