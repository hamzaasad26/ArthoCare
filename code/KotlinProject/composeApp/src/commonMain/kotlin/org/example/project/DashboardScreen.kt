package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun DashboardScreen(
    userName: String,
    onNavigate: (Screen) -> Unit
) {
    val colors = LocalAppColors.current
    val latestLogAt = WeeklyLogStore.latestUpdatedAtMillis
    val latestPredictionAt = PredictionStore.latestUpdatedAtMillis
    val gaugeAt = PredictionStore.cachedGauges?.capturedAtMillis
    val romBurden = RaLensStore.latestRomAnalysis?.overallRomBurden
    val prevRomBurden = RaLensStore.previousOverallRomBurden
    val summary = remember(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        HealthIntelligence.computeSummary()
    }
    val nextStep = remember(summary, latestLogAt, latestPredictionAt) {
        HealthIntelligence.computeRecommendedStep(summary)
    }

    var romLongitudinal by remember { mutableStateOf<RomDashboardLongitudinalState?>(null) }
    LaunchedEffect(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        romLongitudinal = withContext(Dispatchers.Default) {
            defaultRomInsightsRepository().loadDashboardState()
        }
    }

    val mergedRom = RaLensStore.latestRomAnalysis
    val symptomLogSize = SymptomLogStore.recentSnapshots.size
    val symptomHydrationEpoch = SymptomLogStore.hydrationEpoch
    val isOfflineMode = OfflineStateHolder.isOfflineMode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.gradientTop,
                        colors.gradientBottom
                    ),
                    startY = 0f,
                    endY = 900f
                )
            )
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item { Spacer(Modifier.height(AppSpacing.sm)) }
            item {
                if (isOfflineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.chartSeries3.copy(alpha = 0.16f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = colors.chartSeries3,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Offline — showing cached data",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.chartSeries3.copy(alpha = 0.92f),
                        )
                    }
                }
            }
            item {
                HeroHealthCard(
                    summary = summary,
                    userName = userName,
                    onViewInsights = { onNavigate(Screen.RA_PREDICTIONS) }
                )
            }
            item {
                DashboardHeroSummaryGrid(
                    summary = summary,
                    mergedRom = mergedRom,
                    longitudinal = romLongitudinal
                )
            }
            item {
                SymptomTrendsPagerCard(
                    latestLogAt = latestLogAt,
                    symptomLogSize = symptomLogSize,
                    symptomHydrationEpoch = symptomHydrationEpoch,
                )
            }
            item {
                DashboardSlimRecommendedCta(
                    nextStep = nextStep,
                    onActionClick = { onNavigate(nextStep.destination) },
                )
            }
            item { Spacer(Modifier.height(AppSpacing.sm)) }
        }
    }
}

/**
 * Shared section heading composable used across Dashboard, Insights and
 * Actions tabs. Restyled to use the shared dark palette so headings pop in
 * the logo-purple highlight against the dark gradient.
 */
@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppColors.current.sectionHighlight
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onSurfaceMuted
        )
    }
}

@Composable
private fun HeroHealthCard(
    summary: HealthStatusSummary,
    userName: String,
    onViewInsights: () -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.heroSurface
        ),
        border = BorderStroke(0.5.dp, colors.heroBorder.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSpacing.sm)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text(
                "Hello $userName",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted
            )
            val flareHeadlineColor = when (summary.overallRiskLevel) {
                UnifiedRiskLevel.LOW -> colors.riskLow
                UnifiedRiskLevel.MODERATE -> colors.riskModerate
                UnifiedRiskLevel.HIGH -> colors.riskHigh
            }
            Text(
                summary.heroHeadline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = flareHeadlineColor
            )
            Text(
                summary.heroSupportingSentence,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceSecondary
            )
            Button(
                onClick = onViewInsights,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryButton,
                    contentColor = colors.onAccent
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    "View Full Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DashboardSlimRecommendedCta(
    nextStep: RecommendedCareStep,
    onActionClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(0.5.dp, colors.cardBorder.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = nextStep.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceStrong,
                    maxLines = 2,
                )
                Text(
                    text = nextStep.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 2,
                )
            }
            TextButton(
                onClick = onActionClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.linkAccent,
                ),
            ) {
                Text(
                    text = nextStep.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
