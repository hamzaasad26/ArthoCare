package org.example.project

import io.ktor.client.request.parameter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Persistence for [ra_lens_sessions]. No UI.
 */
object RaLensSessionRepository {

    private val json get() = SupabaseHealthRest.timelineJson

    suspend fun insertSession(
        userId: String,
        joint: String,
        side: String,
        analysis: RaLensAnalyzeResponseApi,
        captureMode: String,
        mediaUrl: String? = null
    ): Result<Unit> {
        val jointScoresEl = json.encodeToJsonElement(
            ListSerializer(JointRomScoreApi.serializer()),
            analysis.jointScores
        )
        val analysisEl = json.encodeToJsonElement(RaLensAnalyzeResponseApi.serializer(), analysis)
        val dto = RaLensSessionInsertDto(
            userId = userId,
            joint = joint,
            side = side,
            romScore = analysis.overallRomBurden,
            jointScores = jointScoresEl,
            analysisResult = analysisEl,
            captureMode = captureMode,
            mediaUrl = mediaUrl
        )
        return SupabaseHealthRest.post("ra_lens_sessions", dto)
    }

    suspend fun fetchLatest(userId: String): Result<List<RaLensSessionRowDto>> {
        return SupabaseHealthRest.get("ra_lens_sessions") {
            parameter("user_id", "eq.$userId")
            parameter("order", "created_at.desc")
            parameter("limit", "1")
        }
    }

    suspend fun fetchSinceDays(userId: String, days: Int, limit: Int = 50): Result<List<RaLensSessionRowDto>> {
        val cutMillis = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() -
            days * 24L * 60L * 60L * 1000L
        val cutIso = kotlinx.datetime.Instant.fromEpochMilliseconds(cutMillis).toString()
        return SupabaseHealthRest.get("ra_lens_sessions") {
            parameter("user_id", "eq.$userId")
            parameter("created_at", "gte.$cutIso")
            parameter("order", "created_at.desc")
            parameter("limit", "$limit")
        }
    }

    fun decodeAnalysis(row: RaLensSessionRowDto): RaLensAnalyzeResponseApi =
        json.decodeFromJsonElement<RaLensAnalyzeResponseApi>(row.analysisResult)

    /**
     * Interpreted longitudinal timeline rows only ([RA_LENS_CAPTURE_MODE_INTERPRETED_LONGITUDINAL]).
     * Decodes [InterpretedRaLensSession] from [RaLensSessionRowDto.analysisResult].
     */
    suspend fun fetchInterpretedLongitudinal(userId: String, limit: Int = 500): Result<List<InterpretedRaLensSession>> {
        return SupabaseHealthRest.get<RaLensSessionRowDto>("ra_lens_sessions") {
            parameter("user_id", "eq.$userId")
            parameter("capture_mode", "eq.$RA_LENS_CAPTURE_MODE_INTERPRETED_LONGITUDINAL")
            parameter("select", "analysis_result,created_at")
            parameter("order", "created_at.asc")
            parameter("limit", "$limit")
        }.map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    json.decodeFromJsonElement<InterpretedRaLensSession>(row.analysisResult)
                }.getOrNull()
            }.let { decoded ->
                RomLongitudinalAnalytics.indexSessionsByDay(decoded).map { it.third }
            }
        }
    }

    /**
     * Idempotent upload: skips rows whose [InterpretedRaLensSession.sessionId] already exists remotely for this user.
     */
    suspend fun syncInterpretedSessionsMissing(userId: String, sessions: List<InterpretedRaLensSession>): Result<Int> {
        if (sessions.isEmpty()) return Result.success(0)
        val existingIds = fetchInterpretedLongitudinal(userId).getOrNull()?.map { it.sessionId }?.toSet() ?: emptySet()
        val pending = sessions.filter { it.sessionId !in existingIds }
        if (pending.isEmpty()) return Result.success(0)
        var inserted = 0
        for (chunk in pending.chunked(40)) {
            val dtos = chunk.map { RomInterpretedSessionSupabaseMapper.toInsertDto(it, userId) }
            SupabaseHealthRest.post("ra_lens_sessions", dtos).getOrElse { return Result.failure(it) }
            inserted += chunk.size
        }
        return Result.success(inserted)
    }
}
