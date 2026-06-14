package org.example.project

import kotlin.math.abs
import kotlin.math.roundToInt

enum class DashboardInsightTone { POSITIVE, NEUTRAL, ATTENTION }

data class DashboardInsightUi(
    val headline: String,
    val body: String,
    val tone: DashboardInsightTone,
    /** Higher sorts first (anomalies / risks). */
    val sortPriority: Int
)

/**
 * Builds ordered, single-focus insight cards from existing analytics only
 * ([HealthIntelligence] summary, merged RA Lens analysis, longitudinal ROM state, weekly logs).
 */
fun buildDashboardInsights(
    summary: HealthStatusSummary,
    mergedRom: RaLensAnalyzeResponseApi?,
    longitudinal: RomDashboardLongitudinalState?,
    weekly: WeeklyLogSnapshot?,
    previousWeekly: WeeklyLogSnapshot?
): List<DashboardInsightUi> {
    val cards = mutableListOf<DashboardInsightUi>()

    longitudinal?.flareDetectionMoments?.lastOrNull()?.let { flare ->
        cards.add(
            DashboardInsightUi(
                headline = "Potential flare signal",
                body = flare.reason.take(160).let { if (flare.reason.length > 160) "$it…" else it },
                tone = DashboardInsightTone.ATTENTION,
                sortPriority = 100
            )
        )
    }

    if (summary.symptomTrend == SymptomTrendDirection.WORSENING) {
        cards.add(
            DashboardInsightUi(
                headline = "Symptom burden rising",
                body = summary.symptomTrendSentence,
                tone = DashboardInsightTone.ATTENTION,
                sortPriority = 90
            )
        )
    }

    if (summary.romTrend == RomTrendDirection.WORSENING) {
        cards.add(
            DashboardInsightUi(
                headline = "ROM trajectory softening",
                body = summary.romTrendSentence,
                tone = DashboardInsightTone.ATTENTION,
                sortPriority = 88
            )
        )
    }

    val wj = longitudinal?.weakestJointHistory
    if (wj != null && wj.size >= 2) {
        val last = wj.last()
        val prev = wj[wj.lastIndex - 1]
        if (last.joint == prev.joint && last.score < prev.score - 2) {
            val drop = prev.score - last.score
            cards.add(
                DashboardInsightUi(
                    headline = "${last.joint.toDisplayJointName()} ROM slipped",
                    body = "Weakest-joint ROM eased by about $drop pts vs your prior longitudinal capture.",
                    tone = DashboardInsightTone.ATTENTION,
                    sortPriority = 82
                )
            )
        }
    }

    if (summary.overallRiskLevel == UnifiedRiskLevel.HIGH) {
        cards.add(
            DashboardInsightUi(
                headline = "Elevated modeled flare load",
                body = "Combined estimate is ${summary.flareProbabilityPercent}% (${summary.overallRiskLevel.label}). ${summary.environmentalStressSentence}",
                tone = DashboardInsightTone.ATTENTION,
                sortPriority = 70
            )
        )
    }

    if (summary.symptomTrend == SymptomTrendDirection.IMPROVING) {
        cards.add(
            DashboardInsightUi(
                headline = "Symptom burden easing",
                body = summary.symptomTrendSentence,
                tone = DashboardInsightTone.POSITIVE,
                sortPriority = 40
            )
        )
    }

    if (summary.romTrend == RomTrendDirection.IMPROVING) {
        cards.add(
            DashboardInsightUi(
                headline = "ROM recovery signal",
                body = summary.romTrendSentence,
                tone = DashboardInsightTone.POSITIVE,
                sortPriority = 38
            )
        )
    }

    val sessions = longitudinal?.allSessions.orEmpty().size
    if (sessions >= 3) {
        cards.add(
            DashboardInsightUi(
                headline = "Consistent ROM capture rhythm",
                body = "$sessions longitudinal assessments are on file — longitudinal charts stay well grounded.",
                tone = DashboardInsightTone.POSITIVE,
                sortPriority = 25
            )
        )
    }

    if (weekly != null && previousWeekly != null) {
        val delta = summary.symptomBurdenDeltaPercent
        if (delta != null && abs(delta) < 8) {
            cards.add(
                DashboardInsightUi(
                    headline = "Weekly check-in stable",
                    body = "Latest weekly log stayed close to your prior entry — good cadence for trend detection.",
                    tone = DashboardInsightTone.NEUTRAL,
                    sortPriority = 15
                )
            )
        }
    }

    mergedRom?.jointScores?.maxByOrNull { it.deficitPct }?.let { worst ->
        cards.add(
            DashboardInsightUi(
                headline = "Focus joint",
                body = "${worst.joint.toDisplayJointName()} carries the largest deficit signal in the latest merged capture.",
                tone = DashboardInsightTone.NEUTRAL,
                sortPriority = 10
            )
        )
    }

    if (cards.isEmpty()) {
        cards.add(
            DashboardInsightUi(
                headline = "Recovery picture steady",
                body = summary.healthSnapshotParagraph.take(200).let {
                    if (summary.healthSnapshotParagraph.length > 200) "$it…" else it
                },
                tone = DashboardInsightTone.POSITIVE,
                sortPriority = 5
            )
        )
    }

    return cards.sortedByDescending { it.sortPriority }
}

fun romPercentChangeVsPrior(
    currentBurden: Double?,
    priorBurden: Double?
): Int? {
    if (currentBurden == null || priorBurden == null || priorBurden < 1.0) return null
    return (((currentBurden - priorBurden) / priorBurden) * 100.0).roundToInt().coerceIn(-99, 99)
}
