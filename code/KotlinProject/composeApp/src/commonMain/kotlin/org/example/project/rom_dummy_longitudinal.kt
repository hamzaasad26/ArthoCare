package org.example.project

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Deterministic 14-day synthetic RA Lens timelines: only **measured angles** are drifted;
 * deficits / impairment / interpretation still flow through [recomputed] + [RomUniversalInterpreter].
 */
object RomDummyLongitudinalGenerator {

    private const val DAYS = 14

    private object Seed {
        val elbow = RomReportElbow(
            leftElbow = RomReportElbowSide("left", 151.03370513204874, 14.556245483155777),
            rightElbow = RomReportElbowSide("right", 79.36398125054241, 6.886738596474885),
            reference = RomReportElbowRef(145.0, 15.0)
        ).recomputed()

        val knee = RomReportKnee(
            leftKnee = RomReportKneeSide("left", 132.3766489198728, 8.412532595245693),
            rightKnee = RomReportKneeSide("right", 121.223835051939, 6.711179804703107),
            normalReference = RomReportKneeNormalRef(135.0)
        ).recomputed()

        val shoulder = RomReportShoulder(
            leftShoulder = RomReportShoulderSide("left", 104.28355690060486, 101.14243190418618, 176.79199789770564),
            rightShoulder = RomReportShoulderSide("right", 169.63887847929615, 178.75322596054872, 160.6892006129759),
            reference = RomReportShoulderRef(160.0, 50.0, 160.0)
        ).recomputed()

        val wrist = RomReportWrist(
            leftWrist = RomReportWristSide("left", 68.29840571475323, 40.20499208425266, 174.70756510398755, 153.9819432090546),
            rightWrist = RomReportWristSide("right", 30.325782902654176, 68.22571875875333, 154.9558215555431, 156.33072868973824),
            reference = RomReportWristRef(80.0, 70.0, 20.0, 30.0)
        ).recomputed()
    }

    /** Tiny deterministic oscillation (~±0.8–1.7°). */
    private fun micro(day: Int, salt: Int): Double {
        val s = salt.toDouble()
        return sin(day * 1.738 + s * 0.913) * 1.05 +
            sin(day * 0.511 + s * 2.217) * 0.72 +
            cos(day * 0.371 + s * 0.577) * 0.42
    }

    /** Peak flare weight at day 8, taper 7 & 9. */
    private fun flareIntensity(day: Int): Double = when (day) {
        7 -> 0.62
        8 -> 1.0
        9 -> 0.74
        else -> 0.0
    }

    /** Bilateral knee flexion hit during flare (degrees). */
    private fun kneeFlareFlexLoss(day: Int): Double = when (day) {
        7 -> 9.2
        8 -> 13.8
        9 -> 11.0
        else -> 0.0
    }

    private fun recoveryT(day: Int): Double =
        if (day < 10) 0.0 else ((day - 9) / 4.0).coerceIn(0.0, 1.0)

    private fun recoverEase(day: Int, gamma: Double): Double = recoveryT(day).pow(gamma)

    /** Fatigue coupling from knee flare into wrists / shoulders. */
    private fun kneeLinkagePenalty(day: Int): Double =
        if (day in 7..9) flareIntensity(day) * 2.65 else 0.0

    /**
     * Per-day ROM index targets (0–13) for a synthetic **improvement arc**:
     * early reduced ROM, gradual recovery, late near-normal with a small dip on the last day.
     * Elbow leads recovery; knee starts lowest and lags; shoulder is mid-track; wrist dips on day 8.
     */
    private val elbowArc = intArrayOf(64, 63, 65, 68, 71, 73, 76, 79, 81, 83, 86, 88, 90, 88)
    private val kneeArc = intArrayOf(52, 53, 55, 60, 64, 67, 70, 73, 75, 78, 81, 84, 86, 85)
    private val shoulderArc = intArrayOf(60, 61, 62, 66, 69, 72, 75, 77, 79, 81, 83, 86, 88, 86)
    private val wristArc = intArrayOf(58, 59, 61, 65, 68, 71, 74, 76, 68, 74, 79, 83, 85, 84)

    private fun applyClinicalImprovementArc(
        day: Int,
        session: InterpretedRaLensSession
    ): InterpretedRaLensSession {
        val e = elbowArc[day]
        val k = kneeArc[day]
        val s = shoulderArc[day]
        val w = wristArc[day]
        val newJoints = session.joints.map { j ->
            val target = when {
                j.jointName.contains("elbow", ignoreCase = true) -> e
                j.jointName.contains("knee", ignoreCase = true) -> k
                j.jointName.contains("shoulder", ignoreCase = true) -> s
                j.jointName.contains("wrist", ignoreCase = true) -> w
                else -> j.romScore
            }
            j.copy(
                romScore = target,
                severity = RomUniversalInterpreter.severityFromScore(target),
                summary = "${j.jointName.replace('_', ' ')}: demo trajectory $target/100."
            )
        }
        val overall = newJoints.map { it.romScore.toDouble() }.average().roundToInt().coerceIn(0, 100)
        val weakest = newJoints.minByOrNull { it.romScore }!!
        val strongest = newJoints.maxByOrNull { it.romScore }!!
        return session.copy(
            joints = newJoints,
            overallRomScore = overall,
            weakestJoint = weakest.jointName,
            strongestJoint = strongest.jointName,
            summary = "Combined ROM index $overall/100 across ${newJoints.size} sides (synthetic improvement arc)."
        )
    }

    fun generateTimeline(): Pair<List<InterpretedRaLensSession>, List<DummyRaLensSessionBundle>> {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val startMs = nowMs - ((DAYS - 1) * 86_400_000L)
        val sessions = mutableListOf<InterpretedRaLensSession>()
        val bundles = mutableListOf<DummyRaLensSessionBundle>()
        for (day in 0 until DAYS) {
            val epochMs = startMs + day * 86_400_000L
            fun mn(salt: Int) = micro(day, salt)

            val leBase = Seed.elbow.leftElbow!!
            val reBase = Seed.elbow.rightElbow!!
            var leftElbowFlex = leBase.maxFlexion
            leftElbowFlex += minOf(day, 3) * 0.38
            leftElbowFlex += mn(12) * 0.55
            if (day in 4..6) leftElbowFlex -= (minOf(day, 6) - 3) * 0.85
            if (day in 7..9) leftElbowFlex -= flareIntensity(day) * 3.2
            if (day >= 10) leftElbowFlex += recoverEase(day, 1.05) * 2.8

            var rightElbowFlex = reBase.maxFlexion
            rightElbowFlex += minOf(day, 3) * 0.62
            if (day >= 4) rightElbowFlex -= (minOf(day, 6) - 3).coerceAtLeast(0) * 2.45
            rightElbowFlex -= kneeFlareFlexLoss(day) * 0.78
            if (day in 7..9) rightElbowFlex -= flareIntensity(day) * 14.5
            if (day >= 10) rightElbowFlex += recoverEase(day, 0.88) * 13.4
            rightElbowFlex += mn(19) * 0.85

            var leftElbowExt = leBase.minExtension
            leftElbowExt += mn(22) * 0.38
            if (day in 4..6) leftElbowExt += (minOf(day, 6) - 3) * 0.25
            if (day in 7..9) leftElbowExt += flareIntensity(day) * 1.1

            var rightElbowExt = reBase.minExtension
            rightElbowExt += mn(27) * 0.42
            if (day in 4..6) rightElbowExt += (minOf(day, 6) - 3) * 0.35
            if (day in 7..9) rightElbowExt += flareIntensity(day) * 1.35

            val elbow = Seed.elbow.copy(
                leftElbow = leBase.copy(maxFlexion = leftElbowFlex, minExtension = leftElbowExt),
                rightElbow = reBase.copy(maxFlexion = rightElbowFlex, minExtension = rightElbowExt)
            ).recomputed()

            val lk0 = Seed.knee.leftKnee!!
            val rk0 = Seed.knee.rightKnee!!
            var lkFlex = lk0.maxFlexion
            lkFlex += minOf(day, 3) * 0.28
            if (day >= 4) lkFlex -= (minOf(day, 6) - 3).coerceAtLeast(0) * 2.05
            lkFlex -= kneeFlareFlexLoss(day) * 0.94
            if (day >= 10) lkFlex += recoverEase(day, 1.52) * 11.2
            lkFlex += mn(33) * 0.62

            var rkFlex = rk0.maxFlexion
            rkFlex += minOf(day, 3) * 0.22
            if (day >= 4) rkFlex -= (minOf(day, 6) - 3).coerceAtLeast(0) * 2.35
            rkFlex -= kneeFlareFlexLoss(day) * 1.02
            if (day >= 10) rkFlex += recoverEase(day, 1.58) * 11.8
            rkFlex += mn(37) * 0.58

            var lkExt = lk0.minExtension
            if (day in 4..6) lkExt += (minOf(day, 6) - 3) * 0.52
            if (day in 7..9) lkExt += flareIntensity(day) * 2.35
            lkExt += mn(41) * 0.35

            var rkExt = rk0.minExtension
            if (day in 4..6) rkExt += (minOf(day, 6) - 3) * 0.48
            if (day in 7..9) rkExt += flareIntensity(day) * 2.15
            rkExt += mn(43) * 0.32

            val knee = Seed.knee.copy(
                leftKnee = lk0.copy(maxFlexion = lkFlex, minExtension = lkExt),
                rightKnee = rk0.copy(maxFlexion = rkFlex, minExtension = rkExt)
            ).recomputed()

            val ls0 = Seed.shoulder.leftShoulder!!
            val rs0 = Seed.shoulder.rightShoulder!!
            var lsFlex = ls0.maxFlexion
            lsFlex += minOf(day, 3) * 0.42
            if (day >= 4) lsFlex -= (minOf(day, 6) - 3).coerceAtLeast(0) * 1.35
            if (day in 7..9) lsFlex -= flareIntensity(day) * 10.8
            lsFlex -= kneeLinkagePenalty(day) * 0.55
            if (day >= 10) lsFlex += recoverEase(day, 1.12) * 9.6
            lsFlex += mn(51) * 0.62

            var rsFlex = rs0.maxFlexion
            rsFlex += minOf(day, 3) * 0.18
            if (day in 7..9) rsFlex -= flareIntensity(day) * 2.1
            rsFlex -= kneeLinkagePenalty(day) * 0.35
            if (day >= 10) rsFlex += recoverEase(day, 0.72) * 3.4
            rsFlex += mn(53) * 0.48

            var lsAbd = ls0.maxAbduction
            lsAbd -= kneeLinkagePenalty(day) * 0.42
            lsAbd += mn(57) * 0.55
            if (day >= 10) lsAbd += recoverEase(day, 1.18) * 2.2

            val shoulder = Seed.shoulder.copy(
                leftShoulder = ls0.copy(
                    maxFlexion = lsFlex,
                    maxExtension = ls0.maxExtension +
                        mn(61) * 0.45 +
                        if (day in 7..9) -flareIntensity(day) * 1.2 else 0.0,
                    maxAbduction = lsAbd
                ),
                rightShoulder = rs0.copy(
                    maxFlexion = rsFlex,
                    maxExtension = rs0.maxExtension + mn(63) * 0.38,
                    maxAbduction = rs0.maxAbduction +
                        mn(65) * 0.42 +
                        if (day in 7..9) -kneeLinkagePenalty(day) * 0.25 else 0.0
                )
            ).recomputed()

            val lw0 = Seed.wrist.leftWrist!!
            val rw0 = Seed.wrist.rightWrist!!
            var lwFlex = lw0.maxFlexion
            lwFlex += day * 0.58
            lwFlex -= kneeLinkagePenalty(day) * 1.05
            if (day in 7..9) lwFlex -= flareIntensity(day) * 6.3
            if (day >= 10) lwFlex += recoverEase(day, 0.52) * 14.6
            lwFlex += mn(71) * 0.78

            var lwExt = lw0.maxExtension
            if (day in 4..6) lwExt -= (minOf(day, 6) - 3) * 0.55
            if (day in 7..9) lwExt -= flareIntensity(day) * 13.2
            if (day >= 10) lwExt += recoverEase(day, 0.48) * 11.5
            lwExt += mn(73) * 0.62

            var rwFlex = rw0.maxFlexion
            rwFlex += day * 0.42
            rwFlex -= kneeLinkagePenalty(day) * 0.95
            if (day in 7..9) rwFlex -= flareIntensity(day) * 7.8
            if (day >= 10) rwFlex += recoverEase(day, 0.46) * 13.2
            rwFlex += mn(77) * 0.82

            var rwExt = rw0.maxExtension
            rwExt += minOf(day, 3) * 0.25
            if (day in 7..9) rwExt -= flareIntensity(day) * 10.5
            if (day >= 10) rwExt += recoverEase(day, 0.50) * 9.8
            rwExt += mn(79) * 0.58

            val wrist = Seed.wrist.copy(
                leftWrist = lw0.copy(
                    maxFlexion = lwFlex,
                    maxExtension = lwExt,
                    maxRadial = lw0.maxRadial + mn(81) * 0.45 + sin(day * 0.29 + PI / 7) * 0.35,
                    maxUlnar = lw0.maxUlnar + mn(83) * 0.38
                ),
                rightWrist = rw0.copy(
                    maxFlexion = rwFlex,
                    maxExtension = rwExt,
                    maxRadial = rw0.maxRadial + mn(85) * 0.42,
                    maxUlnar = rw0.maxUlnar + mn(87) * 0.36
                )
            ).recomputed()

            val rawBundle = RawReportsForDay(elbow = elbow, knee = knee, shoulder = shoulder, wrist = wrist)
            val ts = Instant.fromEpochMilliseconds(epochMs).toString()
            val sessionId = "ralens-demo-d${day}-arthocare-seq"
            val interpretedRaw = RomUniversalInterpreter.aggregateSession(
                sessionId = sessionId,
                timestampIso = ts,
                elbow = elbow,
                knee = knee,
                shoulder = shoulder,
                wrist = wrist
            )
            val interpreted = applyClinicalImprovementArc(day, interpretedRaw)
            sessions.add(interpreted)
            bundles.add(
                DummyRaLensSessionBundle(
                    sessionDayIndex = day,
                    epochMs = epochMs,
                    rawReports = rawBundle,
                    interpretedSnapshot = interpreted
                )
            )
        }
        return sessions to bundles
    }
}
