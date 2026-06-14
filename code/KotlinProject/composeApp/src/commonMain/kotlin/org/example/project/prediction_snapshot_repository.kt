package org.example.project

import io.ktor.client.request.parameter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Persistence for [prediction_snapshots]. No UI.
 */
object PredictionSnapshotRepository {

    private val json get() = SupabaseHealthRest.timelineJson

    suspend fun insertFromOverall(
        userId: String,
        overall: OverallApiResponse,
        modelVersion: String = "overall_v1"
    ): Result<Unit> {
        val raw = json.encodeToJsonElement(OverallApiResponse.serializer(), overall)
        val dto = PredictionSnapshotInsertDto(
            userId = userId,
            overallRisk = overall.overallProbability,
            flareProbability = overall.overallProbability,
            weatherContribution = overall.weatherFlare.probability,
            symptomContribution = overall.symptomDerived.probability,
            modelVersion = modelVersion,
            rawResponse = raw
        )
        return SupabaseHealthRest.post("prediction_snapshots", dto)
    }

    suspend fun fetchLatest(userId: String): Result<List<PredictionSnapshotRowDto>> {
        return SupabaseHealthRest.get("prediction_snapshots") {
            parameter("user_id", "eq.$userId")
            parameter("order", "created_at.desc")
            parameter("limit", "1")
        }
    }

    suspend fun fetchSinceDays(userId: String, days: Int, limit: Int = 60): Result<List<PredictionSnapshotRowDto>> {
        val cutMillis = Clock.System.now().toEpochMilliseconds() -
            days * 24L * 60L * 60L * 1000L
        val cutIso = Instant.fromEpochMilliseconds(cutMillis).toString()
        return SupabaseHealthRest.get("prediction_snapshots") {
            parameter("user_id", "eq.$userId")
            parameter("created_at", "gte.$cutIso")
            parameter("order", "created_at.desc")
            parameter("limit", "$limit")
        }
    }

    fun decodeOverall(row: PredictionSnapshotRowDto): OverallApiResponse? {
        val raw = row.rawResponse ?: return null
        return runCatching {
            json.decodeFromJsonElement<OverallApiResponse>(raw)
        }.getOrNull()
    }
}
