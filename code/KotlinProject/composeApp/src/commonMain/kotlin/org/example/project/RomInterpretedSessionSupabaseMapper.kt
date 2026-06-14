package org.example.project

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.encodeToJsonElement

/** Matches [RaLensSessionInsertDto.capture_mode] for interpreted longitudinal rows. */
const val RA_LENS_CAPTURE_MODE_INTERPRETED_LONGITUDINAL = "interpreted_longitudinal"

/**
 * Maps [InterpretedRaLensSession] into the existing [RaLensSessionInsertDto] shape for [ra_lens_sessions].
 * Full interpreted snapshot is stored in [RaLensSessionInsertDto.analysisResult]; joint rows in [RaLensSessionInsertDto.jointScores].
 */
object RomInterpretedSessionSupabaseMapper {

    private val json get() = SupabaseHealthRest.timelineJson

    fun toInsertDto(session: InterpretedRaLensSession, userId: String): RaLensSessionInsertDto {
        val sideGuess = when {
            session.weakestJoint.contains("left", ignoreCase = true) -> "left"
            session.weakestJoint.contains("right", ignoreCase = true) -> "right"
            else -> "both"
        }
        return RaLensSessionInsertDto(
            userId = userId,
            joint = session.weakestJoint,
            side = sideGuess,
            romScore = session.overallRomScore.toDouble(),
            jointScores = json.encodeToJsonElement(
                ListSerializer(InterpretedJointRom.serializer()),
                session.joints
            ),
            analysisResult = json.encodeToJsonElement(
                InterpretedRaLensSession.serializer(),
                session
            ),
            captureMode = RA_LENS_CAPTURE_MODE_INTERPRETED_LONGITUDINAL,
            mediaUrl = null
        )
    }
}
