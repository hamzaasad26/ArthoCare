package org.example.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Stage1ApiRequest(
    @SerialName("age_years") val ageYears: Int,
    @SerialName("smoking_status") val smokingStatus: String,
    val gender: String,
    @SerialName("physical_activity") val physicalActivity: String,
    @SerialName("calories_per_day") val caloriesPerDay: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("caffeine_g") val caffeineG: Double,
    @SerialName("fiber_g") val fiberG: Double,
    val hypertension: String,
    val diabetes: String,
    val hyperlipidemia: String,
    val bmi: Double? = null,
    val race: String? = null,
    @SerialName("drinking_status") val drinkingStatus: String? = null
)

@Serializable
data class Stage3ApiRequest(
    @SerialName("temp_C") val tempC: Double,
    val humidity: Double,
    @SerialName("pressure_hPa") val pressureHpa: Double,
    @SerialName("wind_speed_kmh") val windSpeedKmh: Double,
    @SerialName("precip_intensity") val precipIntensity: Double,
    @SerialName("delta_temp_C") val deltaTempC: Double,
    @SerialName("delta_humidity") val deltaHumidity: Double,
    @SerialName("delta_pressure_hPa") val deltaPressureHpa: Double,
    @SerialName("delta_wind_speed_kmh") val deltaWindSpeedKmh: Double,
    @SerialName("pressure_drop_flag") val pressureDropFlag: Double,
    @SerialName("humidity_spike_flag") val humiditySpikeFlag: Double,
    val pain: Double,
    val stiffness: Double,
    val fatigue: Double,
    @SerialName("symptom_burden") val symptomBurden: Double,
    @SerialName("delta_symptom_burden") val deltaSymptomBurden: Double
)

@Serializable
data class OverallApiRequest(
    val population: Stage1ApiRequest,
    val stage3: Stage3ApiRequest,
    @SerialName("symptom_derived_probability") val symptomDerivedProbability: Double? = null
)

@Serializable
data class ApiPredictionResponse(
    @SerialName("model_name") val modelName: String,
    val probability: Double,
    val threshold: Double,
    @SerialName("risk_level") val riskLevel: String
)

@Serializable
data class OverallApiResponse(
    val population: ApiPredictionResponse,
    @SerialName("symptom_derived") val symptomDerived: ApiPredictionResponse,
    @SerialName("weather_flare") val weatherFlare: ApiPredictionResponse,
    @SerialName("overall_probability") val overallProbability: Double,
    @SerialName("overall_risk_level") val overallRiskLevel: String
)

@Serializable
data class JointRomInputApi(
    val joint: String,
    @SerialName("measured_rom_deg") val measuredRomDeg: Double,
    @SerialName("baseline_rom_deg") val baselineRomDeg: Double? = null
)

@Serializable
data class RaLensAnalyzeRequestApi(
    val joints: List<JointRomInputApi>
)

@Serializable
data class JointRomScoreApi(
    val joint: String,
    @SerialName("measured_rom_deg") val measuredRomDeg: Double,
    @SerialName("baseline_rom_deg") val baselineRomDeg: Double,
    @SerialName("deficit_deg") val deficitDeg: Double,
    @SerialName("deficit_pct") val deficitPct: Double,
    val status: String,
    val score: Double
)

@Serializable
data class RaLensAnalyzeResponseApi(
    @SerialName("most_affected_joint") val mostAffectedJoint: JointRomScoreApi? = null,
    @SerialName("overall_rom_burden") val overallRomBurden: Double,
    @SerialName("joint_scores") val jointScores: List<JointRomScoreApi>,
    @SerialName("available_scripts") val availableScripts: List<String> = emptyList()
)

@Serializable
data class RaLensDesktopStartRequestApi(
    val joint: String? = null,
    val joints: List<String>? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("camera_choice") val cameraChoice: String = "1"
)

@Serializable
data class RaLensDesktopStartResponseApi(
    @SerialName("run_id") val runId: String,
    val joint: String,
    @SerialName("patient_id") val patientId: String,
    val status: String,
    @SerialName("log_file") val logFile: String
)

@Serializable
data class RaLensDesktopStatusResponseApi(
    @SerialName("run_id") val runId: String,
    val status: String,
    @SerialName("exit_code") val exitCode: Int? = null,
    val joint: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("log_file") val logFile: String,
    @SerialName("report_path") val reportPath: String? = null,
    @SerialName("report_parse_error") val reportParseError: String? = null,
    val analysis: RaLensAnalyzeResponseApi? = null
)

