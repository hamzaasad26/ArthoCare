package org.example.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standardized per-motion numeric snapshot used for dashboard trends (Phase 1).
 * Ignores qualitative backend flags — deficits vs reference normals only.
 */
@Serializable
data class MotionRomAxis(
    @SerialName("measured") val measured: Double,
    @SerialName("normal") val normal: Double,
    @SerialName("deficit") val deficit: Double
)

@Serializable
enum class RomInterpretSeverity {
    @SerialName("normal") NORMAL,
    @SerialName("mild") MILD,
    @SerialName("moderate") MODERATE,
    @SerialName("severe") SEVERE
}

@Serializable
data class InterpretedJointRom(
    @SerialName("joint_name") val jointName: String,
    @SerialName("side") val side: String,
    @SerialName("rom_score") val romScore: Int,
    @SerialName("severity") val severity: RomInterpretSeverity,
    @SerialName("motion_breakdown") val motionBreakdown: Map<String, MotionRomAxis>,
    @SerialName("summary") val summary: String
)

@Serializable
data class InterpretedRaLensSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("joints") val joints: List<InterpretedJointRom>,
    @SerialName("overall_rom_score") val overallRomScore: Int,
    @SerialName("weakest_joint") val weakestJoint: String,
    @SerialName("strongest_joint") val strongestJoint: String,
    @SerialName("summary") val summary: String
)

@Serializable
data class DummyRaLensSessionBundle(
    @SerialName("session_day_index") val sessionDayIndex: Int,
    @SerialName("epoch_ms") val epochMs: Long,
    @SerialName("raw_reports") val rawReports: RawReportsForDay,
    @SerialName("interpreted_snapshot") val interpretedSnapshot: InterpretedRaLensSession
)

@Serializable
data class RawReportsForDay(
    @SerialName("elbow") val elbow: RomReportElbow,
    @SerialName("knee") val knee: RomReportKnee,
    @SerialName("shoulder") val shoulder: RomReportShoulder,
    @SerialName("wrist") val wrist: RomReportWrist
)
