package org.example.project

import kotlin.math.roundToInt

/**
 * Phase 2 universal ROM interpretation. Uses deficits vs reference normals only
 * (no risk_level, booleans, abnormalities, or suggestion text for scoring).
 *
 * Formula documentation for Phase 6 reporting lives in [RomInterpretationDocumentation].
 */
object RomUniversalInterpreter {

    fun interpretElbow(report: RomReportElbow): List<InterpretedJointRom> = buildList {
        report.leftElbow?.let { add(interpretElbowSide("${it.side}_elbow", it, report.reference)) }
        report.rightElbow?.let { add(interpretElbowSide("${it.side}_elbow", it, report.reference)) }
    }

    fun interpretKnee(report: RomReportKnee): List<InterpretedJointRom> = buildList {
        report.leftKnee?.let { add(interpretKneeSide("${it.side}_knee", it, report.normalReference)) }
        report.rightKnee?.let { add(interpretKneeSide("${it.side}_knee", it, report.normalReference)) }
    }

    fun interpretShoulder(report: RomReportShoulder): List<InterpretedJointRom> = buildList {
        report.leftShoulder?.let { add(interpretShoulderSide("${it.side}_shoulder", it, report.reference)) }
        report.rightShoulder?.let { add(interpretShoulderSide("${it.side}_shoulder", it, report.reference)) }
    }

    fun interpretWrist(report: RomReportWrist): List<InterpretedJointRom> = buildList {
        report.leftWrist?.let { add(interpretWristSide("${it.side}_wrist", it, report.reference)) }
        report.rightWrist?.let { add(interpretWristSide("${it.side}_wrist", it, report.reference)) }
    }

    fun aggregateSession(
        sessionId: String,
        timestampIso: String,
        elbow: RomReportElbow,
        knee: RomReportKnee,
        shoulder: RomReportShoulder,
        wrist: RomReportWrist
    ): InterpretedRaLensSession {
        val joints = buildList {
            addAll(interpretElbow(elbow))
            addAll(interpretKnee(knee))
            addAll(interpretShoulder(shoulder))
            addAll(interpretWrist(wrist))
        }
        require(joints.isNotEmpty()) { "No joints to interpret" }
        val overall = joints.map { it.romScore }.average().roundToInt().coerceIn(0, 100)
        val weakest = joints.minByOrNull { it.romScore }!!
        val strongest = joints.maxByOrNull { it.romScore }!!
        val summary =
            "Combined ROM index $overall/100 across ${joints.size} sides. " +
                "Most limited: ${weakest.jointName} (${weakest.romScore}). " +
                "Strongest: ${strongest.jointName} (${strongest.romScore})."
        return InterpretedRaLensSession(
            sessionId = sessionId,
            timestamp = timestampIso,
            joints = joints,
            overallRomScore = overall,
            weakestJoint = weakest.jointName,
            strongestJoint = strongest.jointName,
            summary = summary
        )
    }

    private fun interpretElbowSide(name: String, s: RomReportElbowSide, ref: RomReportElbowRef): InterpretedJointRom {
        val flexAxis = MotionRomAxis(s.maxFlexion, ref.flexionNormalDeg, s.flexionDeficit)
        val extAxis = MotionRomAxis(s.minExtension, ref.extensionUpperLimitDeg, s.extensionDeficit)
        val flexScore = scoreFromDeficit(ref.flexionNormalDeg, s.flexionDeficit)
        val extScore = scoreFromDeficit(ref.extensionUpperLimitDeg, s.extensionDeficit)
        val rom = ((flexScore + extScore) / 2.0).roundToInt().coerceIn(0, 100)
        return InterpretedJointRom(
            jointName = name,
            side = s.side,
            romScore = rom,
            severity = severityFromScore(rom),
            motionBreakdown = mapOf("flexion" to flexAxis, "extension" to extAxis),
            summary = "${name.replace('_', ' ')}: flexion ${flexAxis.measured.toInt()}° vs ref ${flexAxis.normal.toInt()}° " +
                "(Δ ${flexAxis.deficit.toInt()}°); extension plane ${extAxis.measured.toInt()}° vs cap ${extAxis.normal.toInt()}° " +
                "(Δ ${extAxis.deficit.toInt()}°). Index $rom/100."
        )
    }

    private fun interpretKneeSide(name: String, s: RomReportKneeSide, ref: RomReportKneeNormalRef): InterpretedJointRom {
        val flexAxis = MotionRomAxis(s.maxFlexion, ref.flexionNormalDeg, s.flexionDeficit)
        val extAxis = MotionRomAxis(s.minExtension, 10.0, s.extensionDeficit)
        val rom = (100.0 - s.impairmentPct).roundToInt().coerceIn(0, 100)
        return InterpretedJointRom(
            jointName = name,
            side = s.side,
            romScore = rom,
            severity = severityFromScore(rom),
            motionBreakdown = mapOf(
                "flexion" to flexAxis,
                "extension" to extAxis,
                "total_arc" to MotionRomAxis(s.totalArc, 135.0, maxOf(0.0, 135.0 - s.totalArc))
            ),
            summary = "${name.replace('_', ' ')}: AMA-style impairment ${s.impairmentPct}% → ROM index $rom/100; arc ${s.totalArc.toInt()}°."
        )
    }

    private fun interpretShoulderSide(name: String, s: RomReportShoulderSide, ref: RomReportShoulderRef): InterpretedJointRom {
        val flexAxis = MotionRomAxis(s.maxFlexion, ref.flexionNormalDeg, s.flexionDeficit)
        val extAxis = MotionRomAxis(s.maxExtension, ref.extensionNormalDeg, s.extensionDeficit)
        val abdAxis = MotionRomAxis(s.maxAbduction, ref.abductionNormalDeg, s.abductionDeficit)
        val s1 = scoreFromDeficit(ref.flexionNormalDeg, s.flexionDeficit)
        val s2 = scoreFromDeficit(ref.extensionNormalDeg, s.extensionDeficit)
        val s3 = scoreFromDeficit(ref.abductionNormalDeg, s.abductionDeficit)
        val rom = ((s1 + s2 + s3) / 3.0).roundToInt().coerceIn(0, 100)
        return InterpretedJointRom(
            jointName = name,
            side = s.side,
            romScore = rom,
            severity = severityFromScore(rom),
            motionBreakdown = mapOf(
                "flexion" to flexAxis,
                "extension" to extAxis,
                "abduction" to abdAxis
            ),
            summary = "${name.replace('_', ' ')}: averaged flexion/extension/abduction deficits vs AAOS refs → $rom/100."
        )
    }

    private fun interpretWristSide(name: String, s: RomReportWristSide, ref: RomReportWristRef): InterpretedJointRom {
        val flexAxis = MotionRomAxis(s.maxFlexion, ref.flexionNormalDeg, s.flexionDeficit)
        val extAxis = MotionRomAxis(s.maxExtension, ref.extensionNormalDeg, s.extensionDeficit)
        val radAxis = MotionRomAxis(s.maxRadial, ref.radialNormalDeg, s.radialDeficit)
        val ulnAxis = MotionRomAxis(s.maxUlnar, ref.ulnarNormalDeg, s.ulnarDeficit)
        val scores = listOf(
            scoreFromDeficit(ref.flexionNormalDeg, s.flexionDeficit),
            scoreFromDeficit(ref.extensionNormalDeg, s.extensionDeficit),
            scoreFromDeficit(ref.radialNormalDeg, s.radialDeficit),
            scoreFromDeficit(ref.ulnarNormalDeg, s.ulnarDeficit)
        )
        val rom = scores.average().roundToInt().coerceIn(0, 100)
        return InterpretedJointRom(
            jointName = name,
            side = s.side,
            romScore = rom,
            severity = severityFromScore(rom),
            motionBreakdown = mapOf(
                "flexion" to flexAxis,
                "extension" to extAxis,
                "radial_deviation" to radAxis,
                "ulnar_deviation" to ulnAxis
            ),
            summary = "${name.replace('_', ' ')}: mean of four deviation axes vs AAOS refs → $rom/100."
        )
    }

    private fun scoreFromDeficit(normalReferenceDeg: Double, deficitDeg: Double): Double {
        if (normalReferenceDeg <= 1e-6) return 100.0
        return (100.0 - (deficitDeg / normalReferenceDeg) * 100.0).coerceIn(0.0, 100.0)
    }

    fun severityFromScore(score: Int): RomInterpretSeverity = when {
        score >= 88 -> RomInterpretSeverity.NORMAL
        score >= 72 -> RomInterpretSeverity.MILD
        score >= 52 -> RomInterpretSeverity.MODERATE
        else -> RomInterpretSeverity.SEVERE
    }
}

/** Strings for product/docs — mirrors scoring implemented above. */
object RomInterpretationDocumentation {
    val scoringRules: List<String> = listOf(
        "Elbow (per side): motion_score_flex = 100 − (flexion_deficit / flexion_normal_deg)×100; " +
            "motion_score_ext = 100 − (extension_deficit / extension_upper_limit_deg)×100; rom_score = round(mean of both), clamped 0–100.",
        "Shoulder (per side): independent flexion / extension / abduction motion scores using each deficit over its reference normal; rom_score = mean of three.",
        "Wrist (per side): flexion, extension, radial, ulnar motion scores from deficits vs reference normals; rom_score = mean of four.",
        "Knee (per side): rom_score = round(100 − impairment_pct), clamped 0–100. impairment_pct recomputed from total_arc via AMA knee LE cue " +
            "(matches backend estimate_impairment: max(0, (1 − min(total_arc/135,1))×7) rounded to 0.1).",
        "Severity bands on rom_score: ≥88 normal, ≥72 mild, ≥52 moderate, else severe.",
        "Explicitly excluded from quantitative scoring: risk_level, flexion_normal booleans, abnormalities[], suggestions[], clinical_notes text."
    )

    val rawBackendNotes: List<String> = listOf(
        "Elbow extension uses min_extension compared to extension_upper_limit_deg (hyperextension tolerance), not flexion-style normals.",
        "Shoulder extension_normal_deg in JSON is a minimum expectation threshold; deficit = max(0, reference − max_extension).",
        "Wrist radial/ulnar measured angles are large (camera-frame geometry); deficits clamp when measured exceeds reference so score stays stable.",
        "Knee impairment_pct in historical JSON may differ slightly from recomputed impairment after perturbing angles — synthetic timeline uses recomputed values end-to-end."
    )
}
