package org.example.project

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private fun riskAccentColor(level: String, colors: AppColorTokens): Color = when (level.lowercase()) {
    "low" -> colors.riskLow
    "moderate" -> colors.riskModerate
    "high" -> colors.riskHigh
    else -> colors.onSurfaceMuted
}

private fun weatherEmoji(condition: String): String = when {
    condition.contains("Clear", ignoreCase = true) -> "☀️"
    condition.contains("Broken", ignoreCase = true) -> "🌤️"
    condition.contains("Overcast", ignoreCase = true) -> "☁️"
    else -> "🌤️"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherAlertsScreen(onNavigateBack: () -> Unit) {
    val colors = LocalAppColors.current
    val displayName = remember {
        AuthService.getCurrentUser()?.fullName ?: "User"
    }
    val latestLogAt = WeeklyLogStore.latestUpdatedAtMillis
    val latestPredictionAt = PredictionStore.latestUpdatedAtMillis
    val gaugeAt = PredictionStore.cachedGauges?.capturedAtMillis
    val romBurden = RaLensStore.latestRomAnalysis?.overallRomBurden
    val prevRomBurden = RaLensStore.previousOverallRomBurden
    val summary = remember(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        HealthIntelligence.computeSummary()
    }
    val forecasts = SharedEnvironmentalSignals.standardForecastWindows
    val forecastWindowsScrollState = rememberScrollState()
    val showFlareWarning = SharedEnvironmentalSignals.anyElevatedForecast(70)
    val badgeAccent = when (summary.overallRiskLevel) {
        UnifiedRiskLevel.LOW -> colors.riskLow
        UnifiedRiskLevel.MODERATE -> colors.riskModerate
        UnifiedRiskLevel.HIGH -> colors.riskHigh
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FeatureTopAppBar(title = "Flare Forecast", onNavigateBack = onNavigateBack)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Flare Forecast for $displayName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.sectionHighlight
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeAccent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, badgeAccent.copy(alpha = 0.85f))
                ) {
                    Text(
                        text = HealthIntelligence.weatherRiskBadgeLabel(summary),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeAccent
                    )
                }
                Text(
                    text = HealthIntelligence.weatherSymptomBurdenBadge(summary),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${summary.predictionFreshnessSentence} These tiles use the same environmental fixture that feeds your coordinated flare narrative until live GPS weather replaces it.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceMuted,
                lineHeight = 22.sp
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = LocalAppColors.current.cardSurface
                ),
                border = BorderStroke(
                    1.dp,
                    LocalAppColors.current.cardBorder.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📍",
                            modifier = Modifier.size(22.dp),
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Islamabad, PK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = LocalAppColors.current.onSurfaceStrong
                        )
                    }
                    OutlinedButton(
                        onClick = { },
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalAppColors.current.linkAccent
                        )
                    ) {
                        Text("Change", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Text(
                text = "Forecast windows",
                style = MaterialTheme.typography.titleMedium,
                color = colors.sectionHighlight
            )
            Text(
                text = "Environmental risk only · based on weather conditions",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(forecastWindowsScrollState),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                forecasts.forEach { data ->
                    ForecastCard(
                        data = data,
                        modifier = Modifier.width(184.dp)
                    )
                }
            }

            val density = LocalDensity.current
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider.copy(alpha = 0.45f))
            ) {
                val thumbWidthFraction = 0.35f
                val scrollFraction = if (forecastWindowsScrollState.maxValue > 0) {
                    forecastWindowsScrollState.value.toFloat() /
                        forecastWindowsScrollState.maxValue.toFloat()
                } else {
                    0f
                }
                val trackPx = with(density) { maxWidth.toPx() }
                val thumbPx = trackPx * thumbWidthFraction
                val maxThumbTravelPx = (trackPx - thumbPx).coerceAtLeast(0f)
                val thumbOffsetPx = scrollFraction.coerceIn(0f, 1f) * maxThumbTravelPx
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { thumbPx.toDp() })
                        .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.linkAccent)
                )
            }

            Text(
                text = "Overall Flare Risk combines your symptom history, health profile, and environmental data. " +
                    "Forecast windows reflect weather conditions only and update with live forecasts.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textAlign = TextAlign.Center
            )

            if (showFlareWarning) {
                FlareWarningSection()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FlareWarningSection() {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningSurface),
        border = BorderStroke(1.dp, colors.warningBorder.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚠️", fontSize = 28.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Flare risk detected in the forecast window",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFAB91)
                )
                Text(
                    text = "Consider resting, staying warm, and avoiding strenuous activity during high-risk periods. Consult your rheumatologist if symptoms worsen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun ForecastCard(
    data: WeatherForecast,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppColors.current
    val accent = riskAccentColor(data.riskLevel, theme)
    val animatedProgress by animateFloatAsState(
        targetValue = (data.risk.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 650),
        label = "flareProgress"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = data.label,
                style = MaterialTheme.typography.labelSmall,
                color = theme.onSurfaceMuted,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${data.risk}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = "Weather · ${data.riskLevel.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.divider.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(weatherEmoji(data.condition), fontSize = 22.sp)
                Text(
                    text = data.condition,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = theme.onSurfaceMuted
                )
            }

            WeatherDataRow(
                icon = "🌡",
                label = "Temp",
                value = "${data.temperature}°C",
                tint = Color(0xFFE57373)
            )
            WeatherDataRow(
                icon = "💧",
                label = "Humidity",
                value = "${data.humidity}%",
                tint = Color(0xFF64B5F6)
            )
            WeatherDataRow(
                icon = "⊕",
                label = "Pressure",
                value = "${data.pressure} hPa",
                tint = theme.onSurfaceMuted
            )
            WeatherDataRow(
                icon = "➤",
                label = "Wind",
                value = "${data.windSpeed} km/h",
                tint = theme.onSurfaceMuted
            )

            if (data.risk >= 70) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.riskHigh.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "⚠ FLARE LIKELY",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.riskHigh,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDataRow(
    icon: String,
    label: String,
    value: String,
    tint: Color
) {
    val theme = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 14.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = theme.onSurfaceStrong
        )
    }
}

