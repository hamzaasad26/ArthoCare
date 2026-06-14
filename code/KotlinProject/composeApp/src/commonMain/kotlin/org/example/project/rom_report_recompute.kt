package org.example.project

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private fun Double.round1(): Double = round(this * 10.0) / 10.0

private fun RomReportElbowSide.withElbowDeficits(ref: RomReportElbowRef): RomReportElbowSide {
    val fd = max(0.0, ref.flexionNormalDeg - maxFlexion)
    val ed = max(0.0, minExtension - ref.extensionUpperLimitDeg)
    return copy(flexionDeficit = fd, extensionDeficit = ed)
}

fun RomReportElbow.recomputed(): RomReportElbow = copy(
    leftElbow = leftElbow?.withElbowDeficits(reference),
    rightElbow = rightElbow?.withElbowDeficits(reference)
)

private fun RomReportKneeSide.withKneeDerived(normalFlexion: Double): RomReportKneeSide {
    val fd = max(0.0, normalFlexion - maxFlexion)
    val ed = minExtension
    val arc = max(0.0, maxFlexion - minExtension)
    val impairment = max(0.0, (1.0 - min(arc / 135.0, 1.0)) * 7.0).round1()
    return copy(
        flexionDeficit = fd,
        extensionDeficit = ed,
        totalArc = arc,
        impairmentPct = impairment
    )
}

fun RomReportKnee.recomputed(): RomReportKnee = copy(
    leftKnee = leftKnee?.withKneeDerived(normalReference.flexionNormalDeg),
    rightKnee = rightKnee?.withKneeDerived(normalReference.flexionNormalDeg)
)

private fun RomReportShoulderSide.withShoulderDeficits(ref: RomReportShoulderRef): RomReportShoulderSide {
    val fd = max(0.0, ref.flexionNormalDeg - maxFlexion)
    val ed = max(0.0, ref.extensionNormalDeg - maxExtension)
    val ad = max(0.0, ref.abductionNormalDeg - maxAbduction)
    return copy(flexionDeficit = fd, extensionDeficit = ed, abductionDeficit = ad)
}

fun RomReportShoulder.recomputed(): RomReportShoulder = copy(
    leftShoulder = leftShoulder?.withShoulderDeficits(reference),
    rightShoulder = rightShoulder?.withShoulderDeficits(reference)
)

private fun RomReportWristSide.withWristDeficits(ref: RomReportWristRef): RomReportWristSide {
    val fd = max(0.0, ref.flexionNormalDeg - maxFlexion)
    val ed = max(0.0, ref.extensionNormalDeg - maxExtension)
    val rd = max(0.0, ref.radialNormalDeg - maxRadial)
    val ud = max(0.0, ref.ulnarNormalDeg - maxUlnar)
    return copy(flexionDeficit = fd, extensionDeficit = ed, radialDeficit = rd, ulnarDeficit = ud)
}

fun RomReportWrist.recomputed(): RomReportWrist = copy(
    leftWrist = leftWrist?.withWristDeficits(reference),
    rightWrist = rightWrist?.withWristDeficits(reference)
)
