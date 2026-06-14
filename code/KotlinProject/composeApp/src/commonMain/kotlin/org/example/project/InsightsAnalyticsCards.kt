@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Pure derivations on top of existing analytics — no random / fake data.
// ---------------------------------------------------------------------------

/** Recovery summary derived from consecutive day-to-day overall ROM deltas. */
internal data class RomRecoverySummary(
    val avgWeeklyDelta: Double?,
    val bestImprovementStreakLength: Int,
    val bestImprovementStreakGain: Int
)

internal object InsightsAnalyticsDerivations {

    fun summarizeRecovery(points: List<RomTrendPoint>): RomRecoverySummary {
        if (points.size < 2) return RomRecoverySummary(null, 0, 0)
        val deltas = points.zipWithNext { a, b -> b.overallScore - a.overallScore }
        val avg = deltas.sum().toDouble() / deltas.size
        var bestLen = 0
        var bestGain = 0
        var curLen = 0
        var curGain = 0
        for (d in deltas) {
            if (d >= 0) {
                curLen += 1
                curGain += d
                if (curLen > bestLen || (curLen == bestLen && curGain > bestGain)) {
                    bestLen = curLen
                    bestGain = curGain
                }
            } else {
                curLen = 0
                curGain = 0
            }
        }
        return RomRecoverySummary(avgWeeklyDelta = avg, bestImprovementStreakLength = bestLen, bestImprovementStreakGain = bestGain)
    }

    /** Direction of the weakest-joint trend (last vs first observed score). */
    fun weakestJointTrend(history: List<WeakestJointPoint>): String {
        if (history.size < 2) return "Not enough data"
        val first = history.first().score
        val last = history.last().score
        val delta = last - first
        return when {
            delta >= 5 -> "Improving (+$delta pts)"
            delta <= -5 -> "Worsening ($delta pts)"
            else -> "Stable (Δ ${if (delta >= 0) "+" else ""}$delta)"
        }
    }

    /**
     * Splits the weakest-joint trend into a (arrow, label) pair so the card
     * can color the directional symbol independently of the numeric label.
     * Threshold matches [weakestJointTrend] (±5 pts ROM).
     */
    fun weakestJointTrendDisplay(history: List<WeakestJointPoint>): Pair<String, String> {
        if (history.size < 2) return Pair("", "Not enough data")
        val first = history.first().score
        val last = history.last().score
        val delta = last - first
        return when {
            delta >= 5 -> Pair("↑", "Improving (+$delta pts)")
            delta <= -5 -> Pair("↓", "Worsening ($delta pts)")
            else -> Pair("→", "Stable (Δ ${if (delta >= 0) "+" else ""}$delta)")
        }
    }
}

/**
 * Patient-facing trend arrow with a meaning-conveying color:
 *   ↑ green  → improvement
 *   ↓ red    → decline
 *   →        → neutral / steady (translucent white)
 *
 * Centralised here so the Weakest Joint, Recovery Progress, and ROM
 * Progression Signal cards (and any future surface) all draw the same
 * symbol with the same color rule. Caller supplies the arrow character;
 * we don't try to infer it from a sign so each card can keep its own
 * sensitivity threshold.
 */
@Composable
internal fun TrendArrow(
    arrow: String,
    modifier: Modifier = Modifier
) {
    if (arrow.isBlank()) return
    val arrowColor = when (arrow) {
        "↑" -> Color(0xFF4CAF50)
        "↓" -> Color(0xFFF44336)
        else -> Color.White.copy(alpha = 0.6f)
    }
    Text(
        text = arrow,
        color = arrowColor,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        maxLines = 1,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// Weather analytics card (top of Insights).
// ---------------------------------------------------------------------------

@Composable
fun InsightsWeatherCard(
    forecast: WeatherForecast,
    summary: HealthStatusSummary,
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Single source of truth for the flare-risk number on this card. We use
    // the model-derived value from HealthIntelligence (which prefers the
    // cached PredictionStore gauges, falling back to a symptom-burden estimate)
    // rather than the hardcoded `forecast.risk` baked into the static weather
    // forecast fixture. Both the badge AND the body sentence below now read
    // from this same value.
    val flareRiskPct = summary.flareProbabilityPercent
    val toneAccent = when {
        flareRiskPct >= 67 -> LocalAppColors.current.riskHigh
        flareRiskPct >= 34 -> LocalAppColors.current.riskModerate
        else -> LocalAppColors.current.linkAccent
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LocalAppColors.current.primaryButton.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = LocalAppColors.current.sectionHighlight,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's environment",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.onSurfaceMuted
                    )
                    Text(
                        text = forecast.condition,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.onSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            "RA risk ${flareRiskPct}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = toneAccent.copy(alpha = 0.22f),
                        disabledLabelColor = toneAccent
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                WeatherStat(
                    icon = Icons.Filled.Thermostat,
                    label = "Temperature",
                    value = "${forecast.temperature.roundToInt()}°C",
                    modifier = Modifier.weight(1f)
                )
                WeatherStat(
                    icon = Icons.Filled.WaterDrop,
                    label = "Humidity",
                    value = "${forecast.humidity}%",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = jointDiscomfortContext(forecast, summary),
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenWeather,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = LocalAppColors.current.linkAccent
                    )
                ) { Text("View forecast") }
            }
        }
    }
}

@Composable
private fun WeatherStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalAppColors.current.linkAccent,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.onSurfaceMuted,
                    maxLines = 1
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.onSurfaceStrong,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Body sentence for the Insights weather card. Returns *only* the
 * environmental-context message — the modeled flare percentage is rendered
 * by the badge at the top right of the card, so repeating it here would
 * surface the same number twice.
 *
 * `summary` is intentionally kept on the signature so callers don't need a
 * separate code path; future copy variants may want to weave the modeled
 * level back in (just not as a duplicate "X%" sentence).
 */
@Suppress("UNUSED_PARAMETER")
private fun jointDiscomfortContext(forecast: WeatherForecast, summary: HealthStatusSummary): String {
    val coolHumid = forecast.temperature <= 24.0 && forecast.humidity >= 65
    val hotDry = forecast.temperature >= 30.0 && forecast.humidity < 40
    return when {
        coolHumid ->
            "Cool temperatures and high humidity may increase stiffness today."
        hotDry ->
            "Hot, dry conditions can dehydrate joint tissues — keep fluids steady."
        forecast.humidity >= 70 ->
            "High humidity can prolong morning stiffness; ease into mobility drills."
        forecast.risk >= 67 ->
            "Environmental load looks elevated — pace activity and watch for flare cues."
        else ->
            "Conditions look comfortable for movement; maintain routine pacing."
    }
}

// ---------------------------------------------------------------------------
// Detailed analytics — derived from RomDashboardLongitudinalState.
// ---------------------------------------------------------------------------

@Composable
fun WeeklyRomTrendCard(
    points: List<RomTrendPoint>,
    modifier: Modifier = Modifier
) {
    val latest = points.lastOrNull()?.overallScore
    val first = points.firstOrNull()?.overallScore
    val delta = if (latest != null && first != null) latest - first else null
    val deltaLabel = when {
        delta == null -> "No trend data yet"
        delta > 0 -> "↑ +$delta pts overall"
        delta < 0 -> "↓ ${delta} pts overall"
        else -> "Holding steady"
    }

    // Subtitle is data-derived: "<first date> – <last date> • N sessions".
    // Falls back to a neutral placeholder while the longitudinal repository
    // is still loading, so the card never renders a stale hardcoded string.
    val subtitle = when {
        points.isEmpty() -> "No data yet for this period."
        points.size == 1 -> "${formatAssessmentTime(points.first().timestamp)} • 1 session"
        else -> {
            val firstLabel = formatAssessmentTime(points.first().timestamp)
            val lastLabel = formatAssessmentTime(points.last().timestamp)
            "$firstLabel – $lastLabel • ${points.size} sessions"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = "Weekly ROM Trend",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.sectionHighlight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))

            if (points.isEmpty()) {
                EmptyAnalyticsRow("No data yet for this period.")
            } else {
                RomTrendChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = latest?.let { "Latest $it/100" } ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.onSurfaceStrong
                    )
                    Text(
                        text = deltaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.linkAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Patient-facing weekly ROM line chart with full axis chrome:
 * - Y-axis labels at the actual min / midpoint / max of the displayed series
 *   (derived per-render — see the dynamic-bounds block below). Honesty over
 *   visual symmetry: a tightly-clustered cohort like 90..98 should reveal
 *   its real spread, not look flat against a fixed 0..100 frame.
 * - Horizontal gridlines at 25/50/75% of the plot area (faint, decorative).
 * - X-axis labels at the first / midpoint / last session date (derived from
 *   [RomTrendPoint.timestamp] — never hardcoded).
 * - ROM trend line uses [AppColorTokens.chartSeries1] for contrast on both themes.
 */
@Composable
private fun RomTrendChart(
    points: List<RomTrendPoint>,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppColors.current
    val lineColor = palette.chartSeries1
    val gridColor = palette.divider.copy(alpha = 0.35f)
    val axisLabelColor = palette.onSurfaceMuted
    val firstLabel = points.firstOrNull()?.timestamp?.let { formatAssessmentTime(it) } ?: ""
    val midLabel = points.getOrNull(points.size / 2)?.timestamp?.let { formatAssessmentTime(it) } ?: ""
    val lastLabel = points.lastOrNull()?.timestamp?.let { formatAssessmentTime(it) } ?: ""

    // Dynamic Y-domain — replaces the hard-pinned 0..100 frame. Padding is
    // 40% of the data range, floored at 5 raw points so a single-point or
    // dead-flat series still has a visible band. Bounds are clamped to the
    // ROM-physical 0..100 range so we never print absurd "ROM = 105" ticks.
    val dataMin = if (points.isEmpty()) 0f else points.minOf { it.overallScore.toFloat() }
    val dataMax = if (points.isEmpty()) 100f else points.maxOf { it.overallScore.toFloat() }
    val padding = ((dataMax - dataMin) * 0.4f).coerceAtLeast(5f)
    val minValue = (dataMin - padding).coerceAtLeast(0f)
    val maxValue = (dataMax + padding).coerceAtMost(100f)
    val midValue = (minValue + maxValue) / 2f

    val yLabelGutter = 26.dp
    val xLabelGutter = 18.dp

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Y-axis labels — three ticks honest to the displayed range
                // (top = max, middle = midpoint, bottom = min). Rounded to
                // nearest integer because raw ROM scores are integer-valued.
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf(maxValue, midValue, minValue).forEach { tick ->
                        Text(
                            text = tick.roundToInt().toString(),
                            color = axisLabelColor,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Plot area (gridlines + line + dots).
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@Canvas

                    // Gridlines at 25 / 50 / 75 (skip 0 + 100 — the chart edges
                    // serve as the axis baseline and ceiling).
                    listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                        val y = h * (1f - fraction)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    // Y mapping uses the dynamic [minValue, maxValue] derived
                    // above so a small data range (e.g. 90..95) actually fills
                    // the plot area instead of bunching against the top edge.
                    fun yFor(score: Float): Float {
                        val yNormalized = (score - minValue) / (maxValue - minValue).coerceAtLeast(1f)
                        // 7.5% padding top + bottom so the line never grazes
                        // the canvas edge.
                        return h - (yNormalized.coerceIn(0f, 1f) * h * 0.85f) - (h * 0.075f)
                    }

                    if (points.size < 2) {
                        val v = (points.firstOrNull()?.overallScore ?: 0).toFloat()
                        val x = w / 2f
                        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, yFor(v)))
                        return@Canvas
                    }

                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val t = i / (points.size - 1).toFloat()
                        val x = t * w
                        val y = yFor(p.overallScore.toFloat())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    points.forEachIndexed { i, p ->
                        val t = i / (points.size - 1).toFloat()
                        val x = t * w
                        val y = yFor(p.overallScore.toFloat())
                        drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
                    }
                }
            }

            // X-axis labels (first / mid / last session date), aligned to the
            // plot area by re-applying the same Y-label gutter on the left.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(xLabelGutter)
                    .padding(start = yLabelGutter, top = 2.dp)
            ) {
                Text(
                    text = firstLabel,
                    color = axisLabelColor,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )
                Text(
                    text = midLabel,
                    color = axisLabelColor,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = lastLabel,
                    color = axisLabelColor,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WeakestJointCard(
    latestSession: InterpretedRaLensSession?,
    history: List<WeakestJointPoint>,
    modifier: Modifier = Modifier
) {
    AnalyticsCard(
        title = "Weakest Joint",
        subtitle = "Where ROM is most reduced now",
        modifier = modifier
    ) {
        val weakestEntry = latestSession?.let { session ->
            session.joints.find { it.jointName == session.weakestJoint }
        }
        if (weakestEntry == null) {
            EmptyAnalyticsRow("No data yet for this period.")
        } else {
            Text(
                text = weakestEntry.jointName.toDisplayJointName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.onSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Severity: ${weakestEntry.severity.name.lowercase().replaceFirstChar { it.uppercase() }} · ${weakestEntry.romScore}/100",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            // Colored trend arrow + label so the direction is glanceable at a
            // distance even before the user reads the numeric delta.
            val (jointArrow, jointTrendLabel) =
                InsightsAnalyticsDerivations.weakestJointTrendDisplay(history)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrendArrow(arrow = jointArrow)
                Text(
                    text = jointTrendLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.linkAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Patient-facing flare detector. Reads directly from the longitudinal
 * [SymptomLogStore] window (last 30 days) rather than the ROM-derived
 * `FlareMoment` history — symptom self-report is what actually triggers the
 * flare narrative for the patient, and the ROM-side moments only fire when
 * we have multiple RA Lens captures bracketing the spike (which is rare).
 *
 * A flare is recognised when pain ≥6/10 *and* fatigue ≥6/10 on the same log
 * (see [SymptomLogStore.detectFlares] for rationale). The card surfaces the
 * most recent episode and a count of how many fall inside the 30-day window.
 */
@Composable
fun FlareDetectionCard(
    modifier: Modifier = Modifier
) {
    AnalyticsCard(
        title = "Flare Detection",
        subtitle = "Recent deterioration warnings",
        modifier = modifier
    ) {
        // Distinguish "no data hydrated yet" from "data is here, no flares
        // crossed threshold." Without this guard the card silently looked
        // identical in both states (always "No flare moments…"), which was
        // the source of the "card looks empty after login" report.
        if (SymptomLogStore.isEmpty()) {
            EmptyAnalyticsRow("Loading recent symptom history…")
            return@AnalyticsCard
        }
        val flares = SymptomLogStore.detectFlares()
        if (flares.isEmpty()) {
            EmptyAnalyticsRow("No elevated symptom episodes in the last 30 days.")
            return@AnalyticsCard
        }
        val (_, mostRecentLabel) = flares.last()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LocalAppColors.current.riskHigh.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = LocalAppColors.current.riskHigh,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Most recent flare event",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.onSurfaceStrong
                )
                Text(
                    text = "${flares.size} elevated episode${if (flares.size == 1) "" else "s"} in the last 30 days",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.onSurfaceMuted
                )
            }
        }
        Text(
            text = mostRecentLabel,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onSurfaceSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val jointRomChartTargets = listOf(
    "right_elbow",
    "right_knee",
    "right_shoulder",
    "right_wrist"
)

private fun latestRomScoreByJointName(sessions: List<InterpretedRaLensSession>): Map<String, Int> {
    val sorted = sessions.sortedByDescending { it.timestamp }
    return jointRomChartTargets.associateWith { jointName ->
        sorted
            .asSequence()
            .flatMap { session -> session.joints.asSequence() }
            .firstOrNull { it.jointName == jointName }
            ?.romScore ?: -1
    }
}

fun computeJointFlareRisk(
    jointRomScore: Int,
    allJointScores: List<Int>,
    overallMlProbability: Int,
    symptomBurden: Double,
    profileModifier: Double
): Int {
    val baseRisk = if (overallMlProbability > 0) {
        overallMlProbability.toDouble()
    } else {
        symptomBurden
    }
    val avgRom = if (allJointScores.isEmpty()) {
        75.0
    } else {
        allJointScores.average()
    }
    val romDeficit = (avgRom - jointRomScore).coerceAtLeast(0.0)
    val jointWeight = 1.0 + (romDeficit / 100.0) * 1.5
    val withProfile = baseRisk + (profileModifier * 15.0)
    val withSymptoms = withProfile + (symptomBurden * 0.10)
    return (withSymptoms * jointWeight).toInt().coerceIn(5, 95)
}

fun profileRiskModifier(profile: UserProfile?): Double {
    if (profile == null) return 0.0
    var modifier = 0.0
    if (profile.hypertension.equals("Yes", ignoreCase = true)) modifier += 0.20
    if (profile.diabetes.equals("Diabetic", ignoreCase = true)) modifier += 0.25
    if (profile.hyperlipidemia.equals("Yes", ignoreCase = true)) modifier += 0.10
    if (profile.physicalActivity.equals("Sedentary", ignoreCase = true)) modifier += 0.15
    if (profile.smoking.equals("Current", ignoreCase = true)) modifier += 0.10
    return modifier.coerceIn(0.0, 1.0)
}

@Composable
fun JointFlareRiskCard(
    modifier: Modifier = Modifier,
    latestSessionTimestamp: String?,
    sessionCount: Int
) {
    val c = LocalAppColors.current
    var sessions by remember { mutableStateOf<List<InterpretedRaLensSession>>(emptyList()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    val userId = AuthService.getCurrentUser()?.id
    LaunchedEffect(userId, latestSessionTimestamp, sessionCount) {
        sessions = InterpretedRomLocalHistory.loadInterpretedSessions().orEmpty()
        val user = AuthService.getCurrentUser()
        profile = if (user != null) {
            ProfileRepository.loadSnapshot(user).getOrNull()?.profile
        } else {
            null
        }
    }
    val jointScoreMap = remember(sessions) { latestRomScoreByJointName(sessions) }
    val predictionKey = PredictionStore.latestUpdatedAtMillis ?: 0L
    val overallRisk = PredictionStore.cachedGauges?.overallPct?.takeIf { it >= 0 } ?: 0
    val isFallback = PredictionStore.cachedGauges?.isFallbackEstimate == true
    val symptomBurden = remember(
        SymptomLogStore.recentLogs.size,
        SymptomLogStore.recentSnapshots.lastOrNull()?.first,
        predictionKey
    ) {
        (SymptomLogStore.averagePain() +
            SymptomLogStore.averageFatigue() +
            SymptomLogStore.averageStiffness()) / 30.0 * 100.0
    }
    val profileMod = remember(profile) { profileRiskModifier(profile) }
    val flareRiskMap = remember(jointScoreMap, overallRisk, symptomBurden, profileMod, predictionKey) {
        val scores = jointScoreMap.values.filter { it >= 0 }
        jointRomChartTargets.associateWith { jointName ->
            val romScore = jointScoreMap[jointName] ?: -1
            if (romScore < 0) {
                -1
            } else {
                computeJointFlareRisk(
                    jointRomScore = romScore,
                    allJointScores = scores,
                    overallMlProbability = overallRisk,
                    symptomBurden = symptomBurden,
                    profileModifier = profileMod
                )
            }
        }
    }
    val subtitle = when {
        overallRisk > 0 && profile != null ->
            "ML prediction · ROM · symptoms · health profile"
        overallRisk > 0 ->
            "ML prediction · ROM · symptoms"
        profile != null ->
            "ROM · symptoms · health profile · (run RA Predictions for full analysis)"
        else ->
            "ROM · symptoms · (run RA Predictions for full analysis)"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.cardSurface),
        border = BorderStroke(0.5.dp, c.cardBorder.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Joint Flare Risk",
                style = MaterialTheme.typography.titleSmall,
                color = c.sectionHighlight,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceMuted.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Vertical bar chart: one bar per joint, height = flare risk % (0–100).
            val plotHeight = 96.dp
            val barWidth = 22.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                jointRomChartTargets.forEach { jointName ->
                    val romScore = jointScoreMap[jointName] ?: -1
                    val flareRaw = flareRiskMap[jointName] ?: -1
                    val flareRisk = if (romScore >= 0 && flareRaw >= 0) flareRaw else null
                    val barColor = when {
                        flareRisk == null -> Color.White.copy(alpha = 0.15f)
                        flareRisk <= 20 -> Color(0xFF4CAF50)
                        flareRisk <= 45 -> Color(0xFFFFEB3B)
                        flareRisk <= 70 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (flareRisk != null) "${flareRisk}%" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (flareRisk != null) barColor else Color.White.copy(alpha = 0.45f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(plotHeight)
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(barWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            if (flareRisk != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .width(barWidth)
                                        .fillMaxHeight(flareRisk / 100f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(barColor)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = jointName.toDisplayJointName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
            if (isFallback) {
                Text(
                    text = "⚠ Using symptom estimate — open RA Predictions for ML-powered analysis",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFEB3B).copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (overallRisk > 0) {
                Text(
                    text = "Powered by RA prediction model",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50).copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun JointRomBarChartCard(
    modifier: Modifier = Modifier,
    latestSessionTimestamp: String?,
    sessionCount: Int
) {
    val c = LocalAppColors.current
    var sessions by remember { mutableStateOf<List<InterpretedRaLensSession>>(emptyList()) }
    LaunchedEffect(latestSessionTimestamp, sessionCount) {
        sessions = InterpretedRomLocalHistory.loadInterpretedSessions().orEmpty()
    }
    val jointScores = remember(sessions) { latestRomScoreByJointName(sessions) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.cardSurface),
        border = BorderStroke(0.5.dp, c.cardBorder.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Joint ROM Status",
                style = MaterialTheme.typography.titleSmall,
                color = c.sectionHighlight,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Latest assessment per joint",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceMuted.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            jointScores.forEach { (jointName, score) ->
                JointRomBar(
                    jointLabel = jointName.toDisplayJointName(),
                    score = score
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun JointRomBar(jointLabel: String, score: Int) {
    val barColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFFFFEB3B)
        score >= 40 -> Color(0xFFFF9800)
        score >= 0 -> Color(0xFFF44336)
        else -> Color.White.copy(alpha = 0.15f)
    }
    val fraction = if (score >= 0) score / 100f else 0f
    val displayText = if (score >= 0) "$score/100" else "No data"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = jointLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.width(110.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor)
            )
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = barColor,
            modifier = Modifier
                .width(54.dp)
                .padding(start = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RecoveryProgressCard(
    points: List<RomTrendPoint>,
    modifier: Modifier = Modifier
) {
    val recovery = InsightsAnalyticsDerivations.summarizeRecovery(points)
    AnalyticsCard(
        title = "Recovery Progress",
        subtitle = "Best streak and average movement per session",
        modifier = modifier
    ) {
        if (recovery.avgWeeklyDelta == null) {
            EmptyAnalyticsRow("Need at least two sessions to compute recovery progress.")
            return@AnalyticsCard
        }
        // Directional arrow replaces the static MonitorHeart icon so the
        // card communicates *which way* recovery is going at a glance.
        // Threshold (±0.05 pts/session) is small because formatAvgDelta
        // already rounds to one decimal, so anything that lands outside
        // the rounding band is a meaningful direction.
        val recoveryArrow = when {
            recovery.avgWeeklyDelta > 0.05 -> "↑"
            recovery.avgWeeklyDelta < -0.05 -> "↓"
            else -> "→"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            TrendArrow(arrow = recoveryArrow)
            Text(
                text = formatAvgDelta(recovery.avgWeeklyDelta),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.onSurfaceStrong
            )
        }
        Text(
            text = if (recovery.bestImprovementStreakLength > 0) {
                "Best improvement streak: ${recovery.bestImprovementStreakLength} sessions (+${recovery.bestImprovementStreakGain} pts)."
            } else {
                "No sustained improvement streaks yet — keep ROM cadence steady."
            },
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onSurfaceSecondary
        )
    }
}

// Patient-facing severity palette. Hex values are intentionally fixed
// (not pulled from AppDark) so the four buckets are unambiguous and
// recognisable across the legend, the stacked bar, and any future UI
// surface that needs to colour-code a "Normal/Mild/Moderate/Severe" pill.
private val SeverityColors = mapOf(
    "Normal"   to Color(0xFF4CAF50),
    "Mild"     to Color(0xFFFFEB3B),
    "Moderate" to Color(0xFFFF9800),
    "Severe"   to Color(0xFFF44336)
)

@Composable
fun SeverityDistributionCard(
    distribution: SeverityDistribution?,
    modifier: Modifier = Modifier
) {
    AnalyticsCard(
        title = "Severity Distribution",
        subtitle = "Joint counts in each severity bucket (latest session)",
        modifier = modifier
    ) {
        if (distribution == null) {
            EmptyAnalyticsRow("No data yet for this period.")
            return@AnalyticsCard
        }
        SeverityStackedBar(distribution = distribution)
        // No fixed-width / weight constraints on the legend row — labels render
        // at their intrinsic width with 10sp text so all four ("Normal", "Mild",
        // "Moderate", "Severe") render fully without clipping.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SeverityLegend(label = "Normal",   color = SeverityColors.getValue("Normal"),   count = distribution.normal)
            SeverityLegend(label = "Mild",     color = SeverityColors.getValue("Mild"),     count = distribution.mild)
            SeverityLegend(label = "Moderate", color = SeverityColors.getValue("Moderate"), count = distribution.moderate)
            SeverityLegend(label = "Severe",   color = SeverityColors.getValue("Severe"),   count = distribution.severe)
        }
    }
}

@Composable
private fun SeverityStackedBar(distribution: SeverityDistribution) {
    val total = max(1, distribution.normal + distribution.mild + distribution.moderate + distribution.severe)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LocalAppColors.current.cardSurface)
    ) {
        SeveritySegment(distribution.normal,   total, SeverityColors.getValue("Normal"))
        SeveritySegment(distribution.mild,     total, SeverityColors.getValue("Mild"))
        SeveritySegment(distribution.moderate, total, SeverityColors.getValue("Moderate"))
        SeveritySegment(distribution.severe,   total, SeverityColors.getValue("Severe"))
    }
}

@Composable
private fun RowScope.SeveritySegment(value: Int, total: Int, color: Color) {
    if (value <= 0) return
    val fraction = value.toFloat() / total.toFloat()
    Box(
        modifier = Modifier
            .weight(fraction)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun SeverityLegend(label: String, color: Color, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        // Explicit 10sp + maxLines=1 + no width modifier — guarantees the
        // longest legend label ("Moderate") renders fully without ellipsis,
        // even on the narrowest supported device width.
        Text(
            text = "$label $count",
            color = LocalAppColors.current.onSurfaceSecondary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun AnalyticsCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.sectionHighlight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun EmptyAnalyticsRow(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppColors.current.onSurfaceMuted
    )
}

private fun formatAvgDelta(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    val sign = when {
        rounded > 0 -> "+"
        else -> ""
    }
    return when {
        rounded > 0 -> "Avg gain $sign${rounded} pts/session"
        rounded < 0 -> "Avg loss ${rounded} pts/session"
        else -> "Avg movement 0 pts/session"
    }
}
