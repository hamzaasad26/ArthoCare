package org.example.project

import kotlin.math.abs
import kotlin.math.roundToInt

enum class UnifiedRiskLevel(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High");

    companion object {
        fun fromPercent(percent: Int): UnifiedRiskLevel = when {
            percent < 34 -> LOW
            percent < 67 -> MODERATE
            else -> HIGH
        }
    }
}

enum class SymptomTrendDirection {
    IMPROVING,
    STABLE,
    WORSENING,
    UNKNOWN
}

enum class RomTrendDirection {
    IMPROVING,
    STABLE,
    WORSENING,
    UNKNOWN
}

data class HealthStatusSummary(
    val overallRiskLevel: UnifiedRiskLevel,
    /** Primary flare estimate shown across Dashboard / Weather badge — matches cached overall model when available. */
    val flareProbabilityPercent: Int,
    val weatherContributionPercent: Int?,
    val populationRiskPercent: Int?,
    val symptomTrend: SymptomTrendDirection,
    val symptomTrendSentence: String,
    /** Delta vs previous weekly log average burden (pain/stiffness/fatigue), percent points; null if unknown. */
    val symptomBurdenDeltaPercent: Int?,
    val romTrend: RomTrendDirection,
    val romTrendSentence: String,
    val environmentalStressSentence: String,
    val heroHeadline: String,
    val heroSupportingSentence: String,
    val trendChipLabel: String,
    val healthSnapshotParagraph: String,
    val insightsModelInterpretation: String,
    val insightsRomInterpretation: String,
    val weatherTabSummary: String,
    val weatherPreviewHeadline: String,
    val weatherPreviewSupporting: String,
    val symptomPreviewHeadline: String,
    val symptomPreviewDeltaLabel: String,
    val predictionFreshnessSentence: String,
    val symptomSeverityShortLabel: String,
)

data class RecommendedCareStep(
    val title: String,
    val body: String,
    val actionLabel: String,
    val destination: Screen
)

private fun WeeklyLogSnapshot.averageBurdenTriplet(): Double =
    (pain + stiffness + fatigue) / 3.0

private fun estimateRiskPercentFromBurden(burden: Double?): Int {
    if (burden == null) return 22
    val scaled = ((burden - 1.0) / 9.0).coerceIn(0.0, 1.0)
    return (scaled * 85).roundToInt().coerceIn(12, 92)
}

private fun symptomBurdenShortLabel(burden: Double?): String = when {
    burden == null -> "Unknown"
    burden <= 3.5 -> "Mild"
    burden <= 6.5 -> "Moderate"
    else -> "Moderate-Severe"
}

private fun describeRomTrend(
    rom: RaLensAnalyzeResponseApi?,
    previousBurden: Double?
): Pair<RomTrendDirection, String> {
    if (rom == null || rom.jointScores.isEmpty()) {
        return RomTrendDirection.UNKNOWN to "ROM check has not been run yet. Run RA Lens to link mobility data with flare context."
    }
    val current = rom.overallRomBurden
    val prev = previousBurden
    val burdenPct = current.roundToInt()
    if (prev == null) {
        return RomTrendDirection.STABLE to
            "Latest ROM burden sits near $burdenPct%; repeat RA Lens over time to establish a mobility trend."
    }
    val trend = when {
        current > prev + 3.0 -> RomTrendDirection.WORSENING
        current < prev - 3.0 -> RomTrendDirection.IMPROVING
        else -> RomTrendDirection.STABLE
    }
    val sentence = when (trend) {
        RomTrendDirection.IMPROVING ->
            "Combined ROM burden eased to about $burdenPct% versus your prior capture — mobility tracking looks improved."
        RomTrendDirection.WORSENING ->
            "Combined ROM burden rose to about $burdenPct% versus your prior capture — worth revisiting guided movement and symptoms."
        RomTrendDirection.STABLE ->
            "Combined ROM burden is holding near $burdenPct% compared with your last session."
        RomTrendDirection.UNKNOWN ->
            "Latest ROM burden is about $burdenPct%."
    }
    return trend to sentence
}

private fun romInsightsDeepSentence(rom: RaLensAnalyzeResponseApi?): String {
    if (rom == null || rom.jointScores.isEmpty()) {
        return "No ROM analysis yet. Completing RA Lens adds joint-specific context next to model risk."
    }
    val rows = rom.jointScores
    val worst = rows.maxByOrNull { it.deficitPct } ?: return "ROM data is available; open RA Predictions for ranking detail."
    val burden = rom.overallRomBurden.roundToInt()
    val worstName = worst.joint.toDisplayJointName()
    return when (worst.status) {
        "HIGH_DEFICIT" ->
            "$worstName shows the largest ROM gap right now; overall modeled ROM burden is about $burden%."
        "MODERATE_DEFICIT" ->
            "Mobility is moderately reduced on $worstName; combined burden sits near $burden%."
        "NORMAL" ->
            if (rows.all { it.status == "NORMAL" }) {
                "Measured joints look within reference ROM ranges (combined burden about $burden%)."
            } else {
                "$worstName leads remaining deficits; combined ROM burden is about $burden%."
            }
        else ->
            "Leading limitation on $worstName; combined ROM burden is about $burden%."
    }
}

object HealthIntelligence {

    private fun formatGaugePercentLabel(percent: Int): String =
        if (percent < 0) "—" else "$percent%"

    fun computeSummary(): HealthStatusSummary {
        val weekly = WeeklyLogStore.latest
        val prevWeekly = WeeklyLogStore.previous
        val gauges = PredictionStore.cachedGauges
        val rom = RaLensStore.latestRomAnalysis

        val burdenNow = weekly?.averageBurdenTriplet()
        val burdenPrev = prevWeekly?.averageBurdenTriplet()

        val flarePct = gauges?.overallPct ?: estimateRiskPercentFromBurden(burdenNow)
        val level = UnifiedRiskLevel.fromPercent(flarePct)

        val symptomTrend: SymptomTrendDirection
        val symptomSentence: String
        val deltaPct: Int?
        if (burdenNow != null && burdenPrev != null) {
            // Preferred path — uses the canonical 2-slot WeeklyLogStore.
            val rawDelta = ((burdenNow - burdenPrev) / burdenPrev.coerceAtLeast(0.15)) * 100.0
            deltaPct = rawDelta.roundToInt().coerceIn(-99, 99)
            symptomTrend = when {
                rawDelta <= -8.0 -> SymptomTrendDirection.IMPROVING
                rawDelta >= 8.0 -> SymptomTrendDirection.WORSENING
                else -> SymptomTrendDirection.STABLE
            }
            symptomSentence = when (symptomTrend) {
                SymptomTrendDirection.IMPROVING ->
                    "Symptom burden improved versus your previous weekly log."
                SymptomTrendDirection.WORSENING ->
                    "Symptom burden increased versus your previous weekly log."
                SymptomTrendDirection.STABLE ->
                    "Symptom burden is steady compared with your previous weekly log."
                SymptomTrendDirection.UNKNOWN ->
                    "Symptom trend needs another weekly entry."
            }
        } else {
            // Fallback path — the 2-slot WeeklyLogStore lacks a previous (or
            // even latest) snapshot, but the longitudinal SymptomLogStore may
            // still have enough rows from the last 30 days to derive a real
            // trend. This avoids the "needs more data" UI when ≥2 rows exist
            // in Supabase but only one made it into WeeklyLogStore's slots.
            val storeWindow = SymptomLogStore.recentSnapshots
            if (storeWindow.size >= 2) {
                val recentBurden = storeWindow.takeLast(3)
                    .map { it.second.averageBurdenTriplet() }
                    .average()
                val priorBurden = storeWindow.dropLast(3).takeLast(3)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.second.averageBurdenTriplet() }
                    ?.average()
                    ?: storeWindow.first().second.averageBurdenTriplet()
                val rawDelta = ((recentBurden - priorBurden) / priorBurden.coerceAtLeast(0.15)) * 100.0
                deltaPct = rawDelta.roundToInt().coerceIn(-99, 99)
                symptomTrend = when {
                    rawDelta <= -8.0 -> SymptomTrendDirection.IMPROVING
                    rawDelta >= 8.0 -> SymptomTrendDirection.WORSENING
                    else -> SymptomTrendDirection.STABLE
                }
                symptomSentence = when (symptomTrend) {
                    SymptomTrendDirection.IMPROVING ->
                        "Symptom burden has been easing across your recent logs."
                    SymptomTrendDirection.WORSENING ->
                        "Symptom burden has been climbing across your recent logs."
                    SymptomTrendDirection.STABLE ->
                        "Symptom burden has held steady across your recent logs."
                    SymptomTrendDirection.UNKNOWN ->
                        "Symptom trend needs another weekly entry."
                }
            } else {
                // Genuinely no longitudinal data either — keep the existing
                // "log to anchor" copy that the Recommended Next Action card
                // depends on.
                symptomTrend = SymptomTrendDirection.UNKNOWN
                symptomSentence = when {
                    weekly == null -> "Log this week's symptoms to anchor severity trends."
                    else -> "Keep logging weekly to unlock week-over-week symptom trends."
                }
                deltaPct = null
            }
        }

        val (romTrend, romSentence) = describeRomTrend(rom, RaLensStore.previousOverallRomBurden)

        val environmental = SharedEnvironmentalSignals.humidityLinkedComfortSentence()

        val heroHeadline = "${level.label} flare risk"
        val heroSupporting = buildString {
            append(environmental)
            append(" ")
            append(symptomSentence.trimEnd('.'))
            append(".")
            when (symptomTrend) {
                SymptomTrendDirection.IMPROVING -> append(" Trend: improving.")
                SymptomTrendDirection.WORSENING -> append(" Trend: worsening.")
                SymptomTrendDirection.STABLE -> append(" Trend: stable.")
                SymptomTrendDirection.UNKNOWN -> Unit
            }
        }.trim()

        val trendChip = when (symptomTrend) {
            SymptomTrendDirection.IMPROVING -> "Trend: improving"
            SymptomTrendDirection.WORSENING -> "Trend: worsening"
            SymptomTrendDirection.STABLE -> "Trend: stable"
            SymptomTrendDirection.UNKNOWN -> "Trend: needs data"
        }

        val snapshot = buildString {
            append(if (weekly == null) "Symptom log not completed yet. " else "Symptom log recorded. ")
            append(
                when {
                    gauges == null -> "Refresh RA Predictions to sync modeled flare drivers. "
                    WeeklyLogStore.latestUpdatedAtMillis != null &&
                        gauges.capturedAtMillis < WeeklyLogStore.latestUpdatedAtMillis!! ->
                        "Model snapshot is older than your latest log — refresh predictions. "
                    else -> "Modeled risk snapshot matches your latest tracked inputs. "
                }
            )
            append(
                when {
                    rom == null || rom.jointScores.isEmpty() -> "ROM check has not been run yet."
                    RaLensStore.previousOverallRomBurden == null ->
                        "Latest ROM burden: ${rom.overallRomBurden.roundToInt()}% (baseline capture)."
                    else ->
                        "Latest ROM burden: ${rom.overallRomBurden.roundToInt()}% (${romTrend.name.lowercase()} vs prior capture)."
                }
            )
        }

        val insightsModel = if (gauges == null && weekly == null) {
            // No log + no model snapshot — show a neutral placeholder rather
            // than a duplicate "complete your log / open RA Predictions" nudge
            // (the canonical nudge lives only on the Dashboard Recommended
            // Next Action card via [computeRecommendedStep]).
            "No data yet for this period."
        } else {
            buildString {
                append("Combined flare estimate is ${flarePct}% (${level.label}), ")
                append(
                    if (gauges != null) {
                        val pop = formatGaugePercentLabel(gauges.populationPct)
                        val wx = formatGaugePercentLabel(gauges.weatherFlarePct)
                        "with population context ~$pop and weather-linked contribution ~$wx."
                    } else {
                        "grounded in your latest symptom burden until the full model refreshes."
                    }
                )
                // Suppress the trailing symptom sentence whenever the
                // longitudinal SymptomLogStore has *any* data — even one
                // row makes the "Keep logging weekly to unlock trends"
                // nudge stale (the user clearly is logging). The previous
                // `hasMinimumData(3)`-only gate failed for users with 1–2
                // rows where the nudge looked dishonest.
                //
                // The two clauses are intentionally redundant: hasMinimumData
                // implies !isEmpty, but we keep both so the gate's intent
                // ("never nudge once symptom data exists") stays explicit.
                val suppressNudge =
                    !SymptomLogStore.isEmpty() || SymptomLogStore.hasMinimumData(3)
                if (!suppressNudge) {
                    append(" ")
                    append(symptomSentence)
                }
            }.trim()
        }

        val insightsRom = romInsightsDeepSentence(rom)

        val wxTab = buildString {
            append(environmental)
            append(" Overall modeled flare estimate: ${flarePct}% (${level.label}).")
        }.trim()

        val wxPreviewHeadline = when {
            SharedEnvironmentalSignals.anyElevatedForecast(70) -> "Elevated flare-load windows ahead"
            SharedEnvironmentalSignals.maxForecastRisk() >= 50 -> "Environmental stress rising mid-week"
            else -> "Environmental conditions relatively steady"
        }

        val wxPreviewSupporting = "${environmental.trimEnd('.')}; modeled flare estimate remains ${flarePct}% (${level.label})."

        val symptomPreviewHeadline = when (symptomTrend) {
            SymptomTrendDirection.IMPROVING -> "Symptom burden easing"
            SymptomTrendDirection.WORSENING -> "Symptom burden rising"
            SymptomTrendDirection.STABLE -> "Symptom burden steady"
            SymptomTrendDirection.UNKNOWN -> "Symptom trend"
        }

        val symptomPreviewDelta = when {
            deltaPct == null -> "Log weekly entries to trend symptoms."
            deltaPct < 0 -> "↓ ${abs(deltaPct)}% vs last log"
            deltaPct > 0 -> "↑ ${deltaPct}% vs last log"
            else -> "Matches prior log"
        }

        val freshness = when {
            gauges == null ->
                "Predictions not synced — open RA Predictions after saving your weekly log."
            WeeklyLogStore.latestUpdatedAtMillis != null &&
                gauges.capturedAtMillis < WeeklyLogStore.latestUpdatedAtMillis!! ->
                "Last model refresh predates your newest symptom log."
            else ->
                "Risk narrative reflects your latest synced weekly log and prediction run."
        }

        return HealthStatusSummary(
            overallRiskLevel = level,
            flareProbabilityPercent = flarePct,
            weatherContributionPercent = gauges?.weatherFlarePct,
            populationRiskPercent = gauges?.populationPct,
            symptomTrend = symptomTrend,
            symptomTrendSentence = symptomSentence,
            symptomBurdenDeltaPercent = deltaPct,
            romTrend = romTrend,
            romTrendSentence = romSentence,
            environmentalStressSentence = environmental,
            heroHeadline = heroHeadline,
            heroSupportingSentence = heroSupporting,
            trendChipLabel = trendChip,
            healthSnapshotParagraph = snapshot.trim(),
            insightsModelInterpretation = insightsModel,
            insightsRomInterpretation = insightsRom,
            weatherTabSummary = wxTab,
            weatherPreviewHeadline = wxPreviewHeadline,
            weatherPreviewSupporting = wxPreviewSupporting,
            symptomPreviewHeadline = symptomPreviewHeadline,
            symptomPreviewDeltaLabel = symptomPreviewDelta,
            predictionFreshnessSentence = freshness,
            symptomSeverityShortLabel = symptomBurdenShortLabel(burdenNow)
        )
    }

    /**
     * Single recommendation pipeline for Dashboard + Actions "today" card.
     * Pass a precomputed [summary] when the caller already materialized it for composition efficiency.
     */
    fun computeRecommendedStep(summary: HealthStatusSummary): RecommendedCareStep {
        val latestLogAt = WeeklyLogStore.latestUpdatedAtMillis
        val latestPredictionAt = PredictionStore.latestUpdatedAtMillis

        if (latestLogAt == null) {
            return RecommendedCareStep(
                title = "Complete your symptom log",
                body = summary.predictionFreshnessSentence,
                actionLabel = "Start log",
                destination = Screen.DAILY_LOG
            )
        }

        if (latestPredictionAt == null || latestPredictionAt < latestLogAt) {
            return RecommendedCareStep(
                title = "Refresh modeled flare drivers",
                body = "Your symptoms updated since the last prediction sync. Run RA Predictions to realign gauges.",
                actionLabel = "Refresh predictions",
                destination = Screen.RA_PREDICTIONS
            )
        }

        return when {
            summary.overallRiskLevel == UnifiedRiskLevel.HIGH ||
                SharedEnvironmentalSignals.anyElevatedForecast(70) -> RecommendedCareStep(
                title = "Review environmental flare stress",
                body = "${summary.environmentalStressSentence} Combined estimate ${summary.flareProbabilityPercent}% (${summary.overallRiskLevel.label}).",
                actionLabel = "Open weather context",
                destination = Screen.WEATHER_ALERTS
            )

            summary.symptomTrend == SymptomTrendDirection.WORSENING -> RecommendedCareStep(
                title = "Recheck symptom severity",
                body = "Burden moved versus your prior weekly log — capture an updated check-in.",
                actionLabel = "Update weekly log",
                destination = Screen.DAILY_LOG
            )

            summary.romTrend == RomTrendDirection.WORSENING -> RecommendedCareStep(
                title = "Repeat guided ROM capture",
                body = summary.romTrendSentence,
                actionLabel = "Start RA Lens",
                destination = Screen.RA_LENS
            )

            summary.romTrend == RomTrendDirection.UNKNOWN -> RecommendedCareStep(
                title = "Add ROM measurement",
                body = "RA Lens hasn't recorded mobility yet — pair it with your refreshed risk snapshot.",
                actionLabel = "Start RA Lens",
                destination = Screen.RA_LENS
            )

            else -> RecommendedCareStep(
                title = "Stay ahead with ROM tracking",
                body = "Insights are current. Capture ROM to validate mobility against modeled flare risk.",
                actionLabel = "Start RA Lens",
                destination = Screen.RA_LENS
            )
        }
    }

    fun weatherRiskBadgeLabel(summary: HealthStatusSummary): String =
        "Overall Flare Risk: ${summary.flareProbabilityPercent}% • ${summary.overallRiskLevel.label}"

    fun weatherSymptomBurdenBadge(summary: HealthStatusSummary): String =
        "SYMPTOM BURDEN: ${summary.symptomSeverityShortLabel.uppercase()}"
}
