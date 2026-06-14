package org.example.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Elbow (matches RAlens elbow_report.json) ---

@Serializable
data class RomReportElbowRef(
    @SerialName("flexion_normal_deg") val flexionNormalDeg: Double = 145.0,
    @SerialName("extension_upper_limit_deg") val extensionUpperLimitDeg: Double = 15.0
)

@Serializable
data class RomReportElbowSide(
    @SerialName("side") val side: String,
    @SerialName("max_flexion") val maxFlexion: Double,
    @SerialName("min_extension") val minExtension: Double,
    @SerialName("flexion_deficit") val flexionDeficit: Double = 0.0,
    @SerialName("extension_deficit") val extensionDeficit: Double = 0.0
)

@Serializable
data class RomReportElbow(
    @SerialName("patient_id") val patientId: String = "P001",
    @SerialName("test_date") val testDate: String = "",
    @SerialName("left_elbow") val leftElbow: RomReportElbowSide? = null,
    @SerialName("right_elbow") val rightElbow: RomReportElbowSide? = null,
    @SerialName("reference") val reference: RomReportElbowRef = RomReportElbowRef()
)

// --- Knee ---

@Serializable
data class RomReportKneeNormalRef(
    @SerialName("flexion_normal_deg") val flexionNormalDeg: Double = 135.0
)

@Serializable
data class RomReportKneeSide(
    @SerialName("side") val side: String,
    @SerialName("max_flexion") val maxFlexion: Double,
    @SerialName("min_extension") val minExtension: Double,
    @SerialName("flexion_deficit") val flexionDeficit: Double = 0.0,
    @SerialName("extension_deficit") val extensionDeficit: Double = 0.0,
    @SerialName("total_arc") val totalArc: Double = 0.0,
    @SerialName("impairment_pct") val impairmentPct: Double = 0.0
)

@Serializable
data class RomReportKnee(
    @SerialName("patient_id") val patientId: String = "P001",
    @SerialName("test_date") val testDate: String = "",
    @SerialName("left_knee") val leftKnee: RomReportKneeSide? = null,
    @SerialName("right_knee") val rightKnee: RomReportKneeSide? = null,
    @SerialName("normal_reference") val normalReference: RomReportKneeNormalRef = RomReportKneeNormalRef()
)

// --- Shoulder ---

@Serializable
data class RomReportShoulderRef(
    @SerialName("flexion_normal_deg") val flexionNormalDeg: Double = 160.0,
    @SerialName("extension_normal_deg") val extensionNormalDeg: Double = 50.0,
    @SerialName("abduction_normal_deg") val abductionNormalDeg: Double = 160.0
)

@Serializable
data class RomReportShoulderSide(
    @SerialName("side") val side: String,
    @SerialName("max_flexion") val maxFlexion: Double,
    @SerialName("max_extension") val maxExtension: Double,
    @SerialName("max_abduction") val maxAbduction: Double,
    @SerialName("flexion_deficit") val flexionDeficit: Double = 0.0,
    @SerialName("extension_deficit") val extensionDeficit: Double = 0.0,
    @SerialName("abduction_deficit") val abductionDeficit: Double = 0.0
)

@Serializable
data class RomReportShoulder(
    @SerialName("patient_id") val patientId: String = "P001",
    @SerialName("test_date") val testDate: String = "",
    @SerialName("left_shoulder") val leftShoulder: RomReportShoulderSide? = null,
    @SerialName("right_shoulder") val rightShoulder: RomReportShoulderSide? = null,
    @SerialName("reference") val reference: RomReportShoulderRef = RomReportShoulderRef()
)

// --- Wrist ---

@Serializable
data class RomReportWristRef(
    @SerialName("flexion_normal_deg") val flexionNormalDeg: Double = 80.0,
    @SerialName("extension_normal_deg") val extensionNormalDeg: Double = 70.0,
    @SerialName("radial_normal_deg") val radialNormalDeg: Double = 20.0,
    @SerialName("ulnar_normal_deg") val ulnarNormalDeg: Double = 30.0
)

@Serializable
data class RomReportWristSide(
    @SerialName("side") val side: String,
    @SerialName("max_flexion") val maxFlexion: Double,
    @SerialName("max_extension") val maxExtension: Double,
    @SerialName("max_radial") val maxRadial: Double,
    @SerialName("max_ulnar") val maxUlnar: Double,
    @SerialName("flexion_deficit") val flexionDeficit: Double = 0.0,
    @SerialName("extension_deficit") val extensionDeficit: Double = 0.0,
    @SerialName("radial_deficit") val radialDeficit: Double = 0.0,
    @SerialName("ulnar_deficit") val ulnarDeficit: Double = 0.0
)

@Serializable
data class RomReportWrist(
    @SerialName("patient_id") val patientId: String = "P001",
    @SerialName("test_date") val testDate: String = "",
    @SerialName("left_wrist") val leftWrist: RomReportWristSide? = null,
    @SerialName("right_wrist") val rightWrist: RomReportWristSide? = null,
    @SerialName("reference") val reference: RomReportWristRef = RomReportWristRef()
)
