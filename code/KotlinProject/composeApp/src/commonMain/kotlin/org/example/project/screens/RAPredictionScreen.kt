package org.example.project.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.LocalAppColors
import org.example.project.AuthService
import org.example.project.FeatureTopAppBar
import org.example.project.OverallApiRequest
import org.example.project.toDisplayJointName
import org.example.project.MlApiService
import org.example.project.MlRiskEngine
import org.example.project.PopulationRawInput
import org.example.project.PredictionStore
import org.example.project.RaDashboardUserInfo
import org.example.project.FlareMoment
import org.example.project.InterpretedRaLensSession
import org.example.project.RomDashboardLongitudinalState
import org.example.project.RomInterpretedSessionsSupabaseSync
import org.example.project.SeverityDistribution
import org.example.project.defaultRomInsightsRepository
import org.example.project.SupabaseClient
import org.example.project.SymptomRawInput
import org.example.project.WeeklyLogStore
import org.example.project.ageFromIsoDate
import org.example.project.formatRiskPercent
import org.example.project.hardcodedWeatherNow
import org.example.project.mapToPopulationInput
import org.example.project.mapToSymptomInput
import org.example.project.mapToWeatherInput
import org.example.project.toStage1ApiRequest
import org.example.project.toStage3ApiRequest
private val ChartGridColor = Color(0x33FFFFFF)
private val ChartAxisColor = Color(0x66FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RAPredictionScreen(onNavigateBack: () -> Unit) {
    val currentUser = AuthService.getCurrentUser()
    var dashboardInfo by remember { mutableStateOf<RaDashboardUserInfo?>(null) }
    var displayAge by remember { mutableStateOf(0) }
    var symptomChartLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var symptomChartValues by remember { mutableStateOf<List<Int>>(emptyList()) }
    var populationPercent by remember { mutableStateOf(0) }
    var severityPercent by remember { mutableStateOf(0) }
    var overallPercent by remember { mutableStateOf(0) }
    var predictionStatus by remember { mutableStateOf("Loading…") }
    var predictionError by remember { mutableStateOf<String?>(null) }
    var romLongitudinal by remember { mutableStateOf<RomDashboardLongitudinalState?>(null) }

    LaunchedEffect(currentUser?.id) {
        val repo = defaultRomInsightsRepository()
        val uid = currentUser?.id
        if (!uid.isNullOrBlank()) {
            RomInterpretedSessionsSupabaseSync.syncLocalInterpretedHistory(uid)
            repo.invalidateCache()
        }
        romLongitudinal = withContext(Dispatchers.Default) {
            repo.loadDashboardState()
        }
    }

    LaunchedEffect(currentUser?.id) {
        predictionError = null
        predictionStatus = "Loading…"
        dashboardInfo = null
        symptomChartLabels = emptyList()
        symptomChartValues = emptyList()
        populationPercent = 0
        severityPercent = 0
        overallPercent = 0

        val userId = currentUser?.id
        if (userId.isNullOrBlank()) {
            predictionError = "No logged-in user found."
            predictionStatus = ""
            return@LaunchedEffect
        }

        val weekly = WeeklyLogStore.latest
        if (weekly == null) {
            predictionError = "Weekly log is missing. Save your Weekly Log first."
            predictionStatus = ""
            return@LaunchedEffect
        }

        val profileResult = SupabaseClient.getUserProfileByUserId(userId)
        val profile = profileResult.getOrNull()
        if (profile == null) {
            predictionError = "Profile data missing. Please complete signup profile fields."
            predictionStatus = ""
            return@LaunchedEffect
        }

        val ageYears = profile.dateOfBirth?.let { ageFromIsoDate(it) }
        dashboardInfo = RaDashboardUserInfo(
            fullName = currentUser?.fullName ?: "User",
            dateOfBirth = profile.dateOfBirth ?: "",
            gender = profile.gender ?: "—",
            bmi = null,
            physicalActivity = profile.physicalActivity ?: "—",
            location = "—"
        )
        displayAge = ageYears ?: 0

        symptomChartLabels = listOf("Pain", "Stiffness", "Fatigue", "Difficulty")
        symptomChartValues = listOf(
            (weekly.pain * 10).toInt().coerceIn(0, 100),
            (weekly.stiffness * 10).toInt().coerceIn(0, 100),
            (weekly.fatigue * 10).toInt().coerceIn(0, 100),
            (weekly.physicalDifficulty * 10).toInt().coerceIn(0, 100)
        )
        val populationMapped = mapToPopulationInput(
            PopulationRawInput(
                ageYears = ageYears,
                gender = profile.gender,
                smokingStatus = profile.smoking,
                physicalActivity = profile.physicalActivity,
                caloriesPerDay = profile.caloriesPerDay?.toDouble(),
                proteinG = profile.proteinG,
                carbsG = profile.carbsG,
                fatG = profile.fatG,
                caffeineG = profile.caffeineG,
                fiberG = profile.fiberG,
                hypertension = profile.hypertension,
                diabetes = profile.diabetes,
                hyperlipidemia = profile.hyperlipidemia,
                bmi = null,
                race = profile.raceEthnicity,
                drinkingStatus = profile.drinking
            )
        )
        when (populationMapped) {
            is org.example.project.MapperResult.Success -> { }
            is org.example.project.MapperResult.Failure -> {
                predictionError = "Population inputs missing: ${populationMapped.issue.fields.joinToString(", ")}"
                predictionStatus = ""
                return@LaunchedEffect
            }
        }

        val popInput = (populationMapped as org.example.project.MapperResult.Success).value

        val previousBurden = WeeklyLogStore.previous?.let {
            (it.pain + it.stiffness + it.fatigue) / 3.0
        } ?: ((weekly.pain + weekly.stiffness + weekly.fatigue) / 3.0)

        val symptomMapped = mapToSymptomInput(
            SymptomRawInput(
                pain = weekly.pain,
                stiffness = weekly.stiffness,
                fatigue = weekly.fatigue,
                physicalDifficulty = weekly.physicalDifficulty,
                vigorousDays = weekly.vigorousDays,
                vigorousHours = weekly.vigorousHours,
                moderateDays = weekly.moderateDays,
                moderateHours = weekly.moderateHours,
                walkingDays = weekly.walkingDays,
                walkingHours = weekly.walkingHours,
                sittingHoursPerWeekday = weekly.sittingHoursPerWeekday,
                previousSymptomBurden = previousBurden
            )
        )
        val symptomInput = when (symptomMapped) {
            is org.example.project.MapperResult.Success -> symptomMapped.value
            is org.example.project.MapperResult.Failure -> {
                predictionError = "Symptom inputs missing: ${symptomMapped.issue.fields.joinToString(", ")}"
                predictionStatus = ""
                return@LaunchedEffect
            }
        }

        val weatherMapped = mapToWeatherInput(
            hardcodedWeatherNow(
                pain = symptomInput.pain,
                stiffness = symptomInput.stiffness,
                fatigue = symptomInput.fatigue,
                previousSymptomBurden = previousBurden
            )
        )
        val weatherInput = when (weatherMapped) {
            is org.example.project.MapperResult.Success -> weatherMapped.value
            is org.example.project.MapperResult.Failure -> {
                predictionError = "Weather inputs missing: ${weatherMapped.issue.fields.joinToString(", ")}"
                predictionStatus = ""
                return@LaunchedEffect
            }
        }

        val symptomDerived = MlRiskEngine.deriveSymptomRisk(symptomInput)
        val overallRequest = OverallApiRequest(
            population = popInput.toStage1ApiRequest(),
            stage3 = weatherInput.toStage3ApiRequest(),
            symptomDerivedProbability = symptomDerived.probability
        )

        val overallResult = MlApiService.predictOverall(overallRequest)
        val overall = overallResult.getOrNull()
        if (overall == null) {
            predictionError = "Prediction failed: ${overallResult.exceptionOrNull()?.message ?: "unknown error"}"
            predictionStatus = ""
            return@LaunchedEffect
        }

        populationPercent = formatRiskPercent(overall.population.probability)
        severityPercent = formatRiskPercent(overall.weatherFlare.probability)
        overallPercent = formatRiskPercent(overall.overallProbability)
        val stage3Payload = weatherInput.toStage3ApiRequest()
        PredictionStore.cachePredictionGaugeSnapshot(
            populationPct = populationPercent,
            weatherFlarePct = severityPercent,
            overallPct = overallPercent,
            overallResponse = overall,
            stage3Payload = stage3Payload
        )
        predictionStatus = "Live model predictions loaded."
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FeatureTopAppBar(title = "RA Predictions", onNavigateBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (dashboardInfo != null) {
                UserSummaryCard(dashboardInfo!!, displayAge)
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Loading profile…",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.onSurfaceSecondary
                    )
                }
            }

            SectionHeading("Weekly symptom trend (from your log, 1–10 → 0–100%)")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (symptomChartLabels.size >= 2 && symptomChartValues.size == symptomChartLabels.size) {
                        SymptomLineChart(
                            labels = symptomChartLabels,
                            values = symptomChartValues,
                            lineColor = LocalAppColors.current.chartSeries1,
                            pointColor = LocalAppColors.current.chartSeries2
                        )
                    } else {
                        Text(
                            "Save your Weekly Log to see this chart.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.onSurfaceSecondary
                        )
                    }
                }
            }

            SectionHeading("Risk overview")
            predictionError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (predictionStatus.isNotBlank()) {
                Text(
                    text = predictionStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.onSurfaceMuted
                )
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Current risk snapshot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.sectionHighlight
                    )
                    val gaugeSnap = PredictionStore.cachedGauges
                    val popGauge = gaugeSnap?.populationPct ?: populationPercent
                    val weatherGauge = gaugeSnap?.weatherFlarePct ?: severityPercent
                    val overallGauge = gaugeSnap?.overallPct ?: overallPercent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RiskGauge(
                            title = "Population",
                            value = popGauge,
                            color = LocalAppColors.current.riskLow,
                            modifier = Modifier.weight(1f)
                        )
                        RiskGauge(
                            title = "Weather flare",
                            value = weatherGauge,
                            color = LocalAppColors.current.riskHigh,
                            modifier = Modifier.weight(1f)
                        )
                        RiskGauge(
                            title = "Overall",
                            value = overallGauge,
                            color = LocalAppColors.current.riskModerate,
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                    }
                    if (gaugeSnap?.isFallbackEstimate == true) {
                        Text(
                            text = "Estimated from symptom history — run RA Predictions for full analysis",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            InterpretedRomLongitudinalSection(state = romLongitudinal)
        }
    }
}

/**
 * Shared section heading for this screen — keeps every label looking like a
 * dashboard-style highlighted section title (logo-purple, SemiBold).
 */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.sectionHighlight
    )
}

@Composable
private fun InterpretedRomLongitudinalSection(state: RomDashboardLongitudinalState?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Interpreted ROM (last 7 days · repository)")
        when {
            state == null -> Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Loading longitudinal ROM…",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.onSurfaceSecondary
                )
            }

            state.romTrendPoints.isEmpty() -> Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No interpreted longitudinal sessions yet. Local bootstrap runs on app start; Supabase fills when logged in.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary
                )
            }

            else -> {
                state.latestSession?.let { latest ->
                    LatestInterpretedRomCard(session = latest)
                }

                WeeklySeveritySummaryCard(progression = state.severityProgression)

                if (state.flareDetectionMoments.isNotEmpty()) {
                    SubSectionHeading("Flare signals")
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.warningSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.flareDetectionMoments.takeLast(5).forEach { f ->
                                FlareMomentRow(f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Smaller, in-section heading variant that still pops on the dark background.
 */
@Composable
private fun SubSectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.sectionHighlight
    )
}

@Composable
private fun LatestInterpretedRomCard(session: InterpretedRaLensSession) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Latest interpreted status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.sectionHighlight
            )
            SummaryCell("Overall ROM score", "${session.overallRomScore}")
            SummaryCell("Weakest joint", session.weakestJoint.toDisplayJointName())
            SummaryCell("Strongest joint", session.strongestJoint.toDisplayJointName())
        }
    }
}

/**
 * For each of the latest seven [SeverityDistribution] rows (one row per day index from
 * the repository), assigns that day a single dominant tier, then counts how many days
 * fell in each tier. Does not sum joint-level histogram buckets across days.
 */
private enum class DominantDayTier { Normal, Mild, Moderate, Severe }

private fun dominantTierForDay(d: SeverityDistribution): DominantDayTier =
    when {
        d.severe > 0 -> DominantDayTier.Severe
        d.moderate > 0 -> DominantDayTier.Moderate
        d.mild > 0 -> DominantDayTier.Mild
        else -> DominantDayTier.Normal
    }

private fun aggregateDominantSeverityDayCountsLast7Days(progression: List<SeverityDistribution>): IntArray {
    val slice = progression.takeLast(7)
    var normal = 0
    var mild = 0
    var moderate = 0
    var severe = 0
    for (d in slice) {
        when (dominantTierForDay(d)) {
            DominantDayTier.Normal -> normal++
            DominantDayTier.Mild -> mild++
            DominantDayTier.Moderate -> moderate++
            DominantDayTier.Severe -> severe++
        }
    }
    return intArrayOf(normal, mild, moderate, severe)
}

private fun dayCountLabel(n: Int): String =
    if (n == 1) "1 day" else "$n days"

/** Highest day-count wins; ties prefer Severe → Moderate → Mild → Normal (clinical priority). */
private fun mostCommonSeverityLabelFromDayCounts(counts: IntArray): String? {
    val normal = counts[0]
    val mild = counts[1]
    val moderate = counts[2]
    val severe = counts[3]
    val total = normal + mild + moderate + severe
    if (total <= 0) return null
    val tiers = listOf(
        "Severe" to severe,
        "Moderate" to moderate,
        "Mild" to mild,
        "Normal" to normal,
    )
    val max = tiers.maxOf { it.second }
    return tiers.first { it.second == max }.first
}

/** Template line from the dominant tier only (no model / backend calls). */
private fun weeklySeverityContextLine(mostCommon: String?): String? =
    when (mostCommon) {
        "Normal" -> "Most sessions landed in the normal range this week."
        "Mild" -> "Most sessions remained within mild impairment ranges."
        "Moderate" -> "Moderate limitation showed up on most days this week."
        "Severe" -> "Severe-band joint labels dominated most days this week."
        else -> null
    }

@Composable
private fun WeeklySeveritySummaryCard(progression: List<SeverityDistribution>) {
    val counts = aggregateDominantSeverityDayCountsLast7Days(progression)
    val normal = counts[0]
    val mild = counts[1]
    val moderate = counts[2]
    val severe = counts[3]
    val total = normal + mild + moderate + severe
    val mostCommon = mostCommonSeverityLabelFromDayCounts(counts)
    val context = weeklySeverityContextLine(mostCommon)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Weekly Severity Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.sectionHighlight
            )
            Text(
                text = "🟢 Normal: ${dayCountLabel(normal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                text = "🟡 Mild: ${dayCountLabel(mild)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                text = "🟠 Moderate: ${dayCountLabel(moderate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                text = "🔴 Severe: ${dayCountLabel(severe)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (mostCommon != null) {
                    "Most common severity this week: $mostCommon"
                } else {
                    "Most common severity this week: —"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.linkAccent
            )
            if (total > 0 && context != null) {
                Text(
                    text = context,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.onSurfaceMuted
                )
            } else if (total == 0) {
                Text(
                    text = "No severity progression rows in the last 7 days of longitudinal data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun FlareMomentRow(moment: FlareMoment) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Day ${moment.dayIndex} · Δscore ${moment.scoreDrop}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppColors.current.onSurfaceStrong
        )
        Text(
            moment.reason,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onSurfaceSecondary
        )
    }
}

@Composable
private fun UserSummaryCard(info: RaDashboardUserInfo, age: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Patient snapshot",
                style = MaterialTheme.typography.labelLarge,
                color = LocalAppColors.current.sectionHighlight,
                fontWeight = FontWeight.SemiBold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCell("Name", info.fullName)
                    SummaryCell("Age", "$age yrs")
                    SummaryCell("Gender", info.gender)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCell("BMI", info.bmi?.let { String.format("%.1f", it) } ?: "N/A")
                    SummaryCell("Activity", info.physicalActivity)
                    SummaryCell("Location", info.location)
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.onSurfaceMuted
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LocalAppColors.current.onSurfaceStrong
        )
    }
}

@Composable
fun SymptomLineChart(
    labels: List<String>,
    values: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    pointColor: Color,
) {
    require(labels.size == values.size) { "labels and values must match" }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padL = 40.dp.toPx()
            val padR = 16.dp.toPx()
            val padT = 16.dp.toPx()
            val padB = 36.dp.toPx()
            val innerW = size.width - padL - padR
            val innerH = size.height - padT - padB
            val maxV = (values.maxOrNull() ?: 100).coerceAtLeast(1)

            // Horizontal grid
            for (i in 0..4) {
                val y = padT + innerH * i / 4f
                drawLine(
                    color = ChartGridColor,
                    start = Offset(padL, y),
                    end = Offset(size.width - padR, y),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = ChartAxisColor,
                start = Offset(padL, padT),
                end = Offset(padL, padT + innerH),
                strokeWidth = 2f
            )
            drawLine(
                color = ChartAxisColor,
                start = Offset(padL, padT + innerH),
                end = Offset(size.width - padR, padT + innerH),
                strokeWidth = 2f
            )

            val n = values.size
            if (n < 2) return@Canvas
            val xs = values.indices.map { i -> padL + innerW * i / (n - 1f) }
            val ys = values.map { v -> padT + innerH * (1f - v / maxV.toFloat()) }

            val path = Path().apply {
                moveTo(xs.first(), ys.first())
                for (i in 1 until n) {
                    lineTo(xs[i], ys[i])
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            xs.forEachIndexed { i, x ->
                drawCircle(color = pointColor, radius = 5.dp.toPx(), center = Offset(x, ys[i]))
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = Offset(x, ys[i]))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun RiskGauge(
    title: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val unknown = value < 0
    val v = if (unknown) 0 else value.coerceIn(0, 100)
    Card(
        modifier = modifier.height(if (emphasized) 150.dp else 130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) LocalAppColors.current.heroSurface else LocalAppColors.current.cardSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val r = size.minDimension / 2f - stroke
                    val cx = size.width / 2f
                    val cy = size.height * 0.92f
                    drawArc(
                        color = Color.White.copy(alpha = 0.18f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (!unknown) {
                        drawArc(
                            color = color,
                            startAngle = 180f,
                            sweepAngle = 180f * (v / 100f),
                            useCenter = false,
                            topLeft = Offset(cx - r, cy - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    text = if (unknown) "—" else "$v%",
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (unknown) LocalAppColors.current.onSurfaceMuted else color
                )
            }
        }
    }
}
