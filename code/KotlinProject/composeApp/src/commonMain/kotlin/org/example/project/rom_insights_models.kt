package org.example.project

/**
 * Dashboard-ready chart / analytics slices derived from [InterpretedRaLensSession] history.
 * Computed in-memory after load — not persisted separately.
 */
data class RomTrendPoint(
    val dayIndex: Int,
    val overallScore: Int,
    val timestamp: String
)

data class WeakestJointPoint(
    val dayIndex: Int,
    val joint: String,
    val score: Int
)

/** Per-session severity histogram over joints (one row per calendar day index). */
data class SeverityDistribution(
    val dayIndex: Int,
    val normal: Int,
    val mild: Int,
    val moderate: Int,
    val severe: Int
)

/** Derived flare-like deterioration vs the prior ordered session. */
data class FlareMoment(
    val dayIndex: Int,
    val reason: String,
    val scoreDrop: Int
)

/** Convenience aggregate when callers want all longitudinal derivatives at once. */
data class RomDashboardLongitudinalState(
    val allSessions: List<InterpretedRaLensSession>,
    val latestSession: InterpretedRaLensSession?,
    val romTrendPoints: List<RomTrendPoint>,
    val weakestJointHistory: List<WeakestJointPoint>,
    val severityProgression: List<SeverityDistribution>,
    val flareDetectionMoments: List<FlareMoment>
)
