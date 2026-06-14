package org.example.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SymptomLogInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("pain_level") val painLevel: Int,
    @SerialName("fatigue_level") val fatigueLevel: Int,
    @SerialName("stiffness_level") val stiffnessLevel: Int,
    @SerialName("activity_level") val activityLevel: String,
    val notes: String? = null,
    val source: String = "manual"
)

@Serializable
data class SymptomLogRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("pain_level") val painLevel: Int,
    @SerialName("fatigue_level") val fatigueLevel: Int,
    @SerialName("stiffness_level") val stiffnessLevel: Int,
    @SerialName("activity_level") val activityLevel: String,
    val notes: String? = null,
    val source: String = "manual",
    /** Optional if your Supabase schema adds a top-level difficulty column (notes JSON is still preferred). */
    @SerialName("physical_difficulty_level") val physicalDifficultyLevel: Int? = null,
)

@Serializable
data class WeeklyLogEmbeddedNotes(
    val physicalDifficulty: Double,
    val vigorousDays: Double,
    val vigorousHours: Double,
    val moderateDays: Double,
    val moderateHours: Double,
    val walkingDays: Double,
    val walkingHours: Double,
    val sittingHoursPerWeekday: Double
)

@Serializable
data class RaLensSessionInsertDto(
    @SerialName("user_id") val userId: String,
    val joint: String,
    val side: String,
    @SerialName("rom_score") val romScore: Double? = null,
    @SerialName("joint_scores") val jointScores: JsonElement,
    @SerialName("analysis_result") val analysisResult: JsonElement,
    @SerialName("capture_mode") val captureMode: String,
    @SerialName("media_url") val mediaUrl: String? = null
)

@Serializable
data class RaLensSessionRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    val joint: String,
    val side: String,
    @SerialName("rom_score") val romScore: Double? = null,
    @SerialName("joint_scores") val jointScores: JsonElement,
    @SerialName("analysis_result") val analysisResult: JsonElement,
    @SerialName("capture_mode") val captureMode: String,
    @SerialName("media_url") val mediaUrl: String? = null
)

@Serializable
data class PredictionSnapshotInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("overall_risk") val overallRisk: Double,
    @SerialName("flare_probability") val flareProbability: Double,
    @SerialName("weather_contribution") val weatherContribution: Double,
    @SerialName("symptom_contribution") val symptomContribution: Double? = null,
    @SerialName("model_version") val modelVersion: String = "overall_v1",
    @SerialName("raw_response") val rawResponse: JsonElement? = null
)

@Serializable
data class PredictionSnapshotRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("overall_risk") val overallRisk: Double,
    @SerialName("flare_probability") val flareProbability: Double,
    @SerialName("weather_contribution") val weatherContribution: Double,
    @SerialName("symptom_contribution") val symptomContribution: Double? = null,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("raw_response") val rawResponse: JsonElement? = null
)

@Serializable
data class WeatherSnapshotInsertDto(
    @SerialName("user_id") val userId: String,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val location: String? = null,
    @SerialName("risk_modifier") val riskModifier: Double? = null
)

@Serializable
data class WeatherSnapshotRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val location: String? = null,
    @SerialName("risk_modifier") val riskModifier: Double? = null
)

/** Aggregation bundle for dashboards / future intelligence (no UI here). */
data class HealthTrendBundle(
    val symptomsLast7: List<SymptomLogRowDto>,
    val symptomsLast30: List<SymptomLogRowDto>,
    val recentPredictions: List<PredictionSnapshotRowDto>,
    val recentRaLens: List<RaLensSessionRowDto>
)

data class RecentHealthSummary(
    val latestSymptom: SymptomLogRowDto?,
    val latestPrediction: PredictionSnapshotRowDto?,
    val latestRaLens: RaLensSessionRowDto?,
    val symptomCount7d: Int,
    val symptomCount30d: Int
)
