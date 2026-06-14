@file:OptIn(ExperimentalFoundationApi::class)

package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.datetime.Instant

/**
 * Dashboard sub-section dark palette — mirrors [DashboardScreen]'s palette so
 * section labels, metric tiles, and the activity strip stay legible against
 * the dark gradient. Hard-coded; do NOT swap to MaterialTheme.colorScheme here.
 */
private object CompactDark {
    val SectionHeading = Color(0xFFCFB3FF)         // matches the ArthoCare logo tint
    val LinkAccent = Color(0xFFB08AFF)
    val TileSurface = Color(0xFF1E1630)
    val ActivityStripSurface = Color(0xFF1E1630).copy(alpha = 0.7f)
    val OnSurfaceStrong = Color.White
    val OnSurfaceSecondary = Color.White.copy(alpha = 0.78f)
    val OnSurfaceMuted = Color.White.copy(alpha = 0.6f)
    val OnSurfaceFaint = Color.White.copy(alpha = 0.45f)
}

@Composable
fun DashboardHeroSummaryGrid(
    summary: HealthStatusSummary,
    mergedRom: RaLensAnalyzeResponseApi?,
    longitudinal: RomDashboardLongitudinalState?,
    modifier: Modifier = Modifier
) {
    val romScore = mergedRom?.overallRomBurden?.roundToInt()
        ?: longitudinal?.latestSession?.overallRomScore
    val romScoreText = when {
        romScore == null -> "—"
        romScore > 80 -> "80+/100"
        else -> "$romScore/100"
    }
    val romScoreColor = when {
        romScore == null -> CompactDark.OnSurfaceStrong
        romScore > 80 -> Color(0xFF4CAF50)
        romScore < 50 -> Color(0xFFE57373)
        else -> Color(0xFFFFC107)
    }
    val romDelta = romPercentChangeVsPrior(
        mergedRom?.overallRomBurden,
        RaLensStore.previousOverallRomBurden
    )
    val romTrendArrow = when {
        romDelta == null -> ""
        romDelta > 0 -> "↑ $romDelta%"
        romDelta < 0 -> "↓ ${kotlin.math.abs(romDelta)}%"
        else -> "→ 0%"
    }
    val weakestRaw = mergedRom?.jointScores?.maxByOrNull { it.deficitPct }?.joint
        ?: longitudinal?.latestSession?.weakestJoint
    val weakest = weakestRaw?.toDisplayJointName() ?: "—"

    val romTrendDirection: String = run {
        val pts = longitudinal?.romTrendPoints.orEmpty()
        if (pts.size < 2) return@run "—"
        val prevScore = pts[pts.lastIndex - 1].overallScore
        val latestScore = pts.last().overallScore
        when {
            latestScore > prevScore -> "Improving"
            latestScore < prevScore -> "Worsening"
            else -> "Stable"
        }
    }
    val lastAssessmentLine = when {
        longitudinal?.latestSession?.timestamp != null ->
            formatAssessmentTime(longitudinal.latestSession!!.timestamp)
        WeeklyLogStore.latestUpdatedAtMillis != null -> "Weekly log on file"
        else -> "—"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = "Overview",
            style = MaterialTheme.typography.labelLarge,
            color = CompactDark.SectionHeading,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            DashboardRomHealthHeroCard(
                romScoreText = romScoreText,
                valueColor = romScoreColor,
                trendDirection = romTrendDirection,
                trendArrow = romTrendArrow,
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            )
            DashboardSeveritySecondaryCard(
                severityLabel = summary.symptomSeverityShortLabel,
                symptomHint = summary.symptomPreviewDeltaLabel,
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            DashboardSupportMetricCard(
                label = "Weakest joint",
                value = weakest,
                modifier = Modifier.weight(1f)
            )
            DashboardSupportMetricCard(
                label = "Last assessment",
                value = lastAssessmentLine,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardRomHealthHeroCard(
    romScoreText: String,
    valueColor: Color,
    trendDirection: String,
    trendArrow: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CompactDark.TileSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ROM Health",
                style = MaterialTheme.typography.labelSmall,
                color = CompactDark.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = romScoreText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val statusLine = when (trendDirection) {
                "Improving" -> "Improving this week"
                "Worsening" -> "Declining this week"
                "Stable" -> "Stable this week"
                else -> "Trend needs more sessions"
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = CompactDark.OnSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val chip = when {
                trendArrow.isNotBlank() && trendArrow != "flat" && trendArrow != "→ 0%" -> trendArrow
                trendDirection != "—" -> when (trendDirection) {
                    "Improving" -> "↑ Improving"
                    "Worsening" -> "↓ Declining"
                    "Stable" -> "→ Stable"
                    else -> ""
                }
                else -> ""
            }
            if (chip.isNotBlank()) {
                Text(
                    text = chip,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CompactDark.LinkAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun DashboardSeveritySecondaryCard(
    severityLabel: String,
    symptomHint: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CompactDark.TileSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Severity",
                style = MaterialTheme.typography.labelSmall,
                color = CompactDark.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = severityLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CompactDark.OnSurfaceStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = symptomHint,
                style = MaterialTheme.typography.labelSmall,
                color = CompactDark.OnSurfaceFaint,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardSupportMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CompactDark.TileSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CompactDark.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = CompactDark.OnSurfaceStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun DashboardInsightSwipeStack(
    insights: List<DashboardInsightUi>,
    modifier: Modifier = Modifier
) {
    if (insights.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "Insights will appear as ROM and symptom data accumulate.",
                style = MaterialTheme.typography.bodySmall,
                color = CompactDark.OnSurfaceMuted,
                modifier = Modifier.padding(vertical = AppSpacing.sm)
            )
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { insights.size })
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.labelLarge,
            color = CompactDark.SectionHeading,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            contentPadding = PaddingValues(horizontal = AppSpacing.lg),
            pageSpacing = AppSpacing.sm
        ) { page ->
            val card = insights[page]
            val toneColor = when (card.tone) {
                DashboardInsightTone.POSITIVE ->
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                DashboardInsightTone.ATTENTION ->
                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                DashboardInsightTone.NEUTRAL ->
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = toneColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        text = card.headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CompactDark.OnSurfaceStrong,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = CompactDark.OnSurfaceSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SymptomTrendsPagerCard(
    latestLogAt: Long?,
    symptomLogSize: Int,
    symptomHydrationEpoch: Int,
    modifier: Modifier = Modifier,
) {
    val snapshots = remember(latestLogAt, symptomLogSize, symptomHydrationEpoch) {
        SymptomLogStore.recentSnapshots
    }
    val symptomPages = remember {
        listOf(
            SymptomPage("Pain Level", Color(0xFFF44336)) { snap -> snap.second.pain * 10.0 },
            SymptomPage("Fatigue Level", Color(0xFFFF9800)) { snap -> snap.second.fatigue * 10.0 },
            SymptomPage("Stiffness Level", Color(0xFF7B5EA7)) { snap -> snap.second.stiffness * 10.0 },
            SymptomPage("Difficulty", Color(0xFFCFB3FF)) { snap -> snap.second.physicalDifficulty * 10.0 },
        )
    }
    val pagerState = rememberPagerState(pageCount = { 4 })

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1630)),
        border = BorderStroke(0.5.dp, Color(0xFF6B4FA0).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Symptom Trends",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFCFB3FF),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Swipe to view Pain · Fatigue · Stiffness · Difficulty",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            if (SymptomLogStore.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No symptom data yet — complete your daily log",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    pageSpacing = 12.dp,
                ) { page ->
                    val pageDef = symptomPages[page]
                    val dataPoints = snapshots.map { snap ->
                        snap.first to pageDef.valueExtractor(snap)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = pageDef.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFCFB3FF),
                            fontWeight = FontWeight.SemiBold,
                        )
                        SymptomLineGraph(
                            dataPoints = dataPoints,
                            lineColor = pageDef.lineColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        if (dataPoints.isNotEmpty()) {
                            val firstDate = formatAssessmentTime(
                                Instant.fromEpochMilliseconds(dataPoints.first().first).toString(),
                            )
                            val lastDate = formatAssessmentTime(
                                Instant.fromEpochMilliseconds(dataPoints.last().first).toString(),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    firstDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.3f),
                                )
                                Text(
                                    lastDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.3f),
                                )
                            }
                        }
                        SymptomStatsRow(dataPoints = dataPoints, lineColor = pageDef.lineColor)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (pagerState.currentPage == index) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) Color(0xFFB08AFF)
                                    else Color.White.copy(alpha = 0.25f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

data class SymptomPage(
    val title: String,
    val lineColor: Color,
    val valueExtractor: (Pair<Long, WeeklyLogSnapshot>) -> Double,
)

@Composable
fun SymptomLineGraph(
    dataPoints: List<Pair<Long, Double>>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No data yet", color = Color.White.copy(alpha = 0.3f))
        }
    } else {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val minVal = dataPoints.minOf { it.second }.toFloat()
            val maxVal = dataPoints.maxOf { it.second }.toFloat()
            val range = (maxVal - minVal).coerceAtLeast(1f)
            val padding = range * 0.15f
            val minBound = (minVal - padding).coerceAtLeast(0f)
            val maxBound = (maxVal + padding).coerceAtMost(100f)
            val boundRange = (maxBound - minBound).coerceAtLeast(1f)

            listOf(25f, 50f, 75f).forEach { gridVal ->
                if (gridVal in minBound..maxBound) {
                    val y = h - ((gridVal - minBound) / boundRange * h * 0.85f) - h * 0.075f
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
            }

            val points = dataPoints.mapIndexed { i, (_, value) ->
                val x = i.toFloat() / (dataPoints.size - 1) * w
                val yNorm = (value.toFloat() - minBound) / boundRange
                val y = h - (yNorm * h * 0.85f) - h * 0.075f
                Offset(x, y)
            }

            val fillPath = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = 0f,
                    endY = h,
                ),
            )

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            points.forEach { pt ->
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = pt)
                drawCircle(color = Color(0xFF0D0B12), radius = 1.5.dp.toPx(), center = pt)
            }
        }
    }
}

@Composable
fun SymptomStatsRow(dataPoints: List<Pair<Long, Double>>, lineColor: Color) {
    if (dataPoints.isEmpty()) {
        return
    }
    val avg = dataPoints.map { it.second }.average()
    val peak = dataPoints.maxOf { it.second }
    val trend = when {
        dataPoints.size >= 4 -> {
            val recent = dataPoints.takeLast(3).map { it.second }.average()
            val earlier = dataPoints.dropLast(3).takeLast(3).map { it.second }.average()
            when {
                recent < earlier - 3 -> "↓ Improving"
                recent > earlier + 3 -> "↑ Worsening"
                else -> "→ Stable"
            }
        }
        else -> "→ Stable"
    }
    val trendColor = when {
        trend.startsWith("↓") -> Color(0xFF4CAF50)
        trend.startsWith("↑") -> Color(0xFFF44336)
        else -> Color.White.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatChip("Avg", "${avg.roundToInt()}/100", lineColor)
        StatChip("Peak", "${peak.roundToInt()}/100", lineColor)
        StatChip("Trend", trend, trendColor)
    }
}

@Composable
fun StatChip(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
@Composable
fun DashboardActivityStrip(
    longitudinal: RomDashboardLongitudinalState?,
    @Suppress("UNUSED_PARAMETER") summary: HealthStatusSummary,
    modifier: Modifier = Modifier
) {
    val sessions = longitudinal?.allSessions?.size ?: 0
    val logStreakLabel = when {
        WeeklyLogStore.latest != null && WeeklyLogStore.previous != null -> "Multi-week"
        WeeklyLogStore.latest != null -> "Active"
        else -> "Needs log"
    }
    val assessmentsLabel = "${longitudinal?.allSessions?.size ?: 0} captures"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                color = CompactDark.ActivityStripSurface,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardStripCell("Sessions", "$sessions")
        DashboardStripCell("Logs", logStreakLabel)
        DashboardStripCell("ROM data", assessmentsLabel)
    }
}

@Composable
private fun DashboardStripCell(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = CompactDark.OnSurfaceStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CompactDark.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
