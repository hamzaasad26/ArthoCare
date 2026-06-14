package org.example.project

import io.ktor.client.request.parameter

/**
 * Persistence for [weather_snapshots]. No UI.
 */
object WeatherSnapshotRepository {

    suspend fun insertFromStage3(
        userId: String,
        stage3: Stage3ApiRequest,
        location: String? = null,
        riskModifier: Double? = null
    ): Result<Unit> {
        val dto = WeatherSnapshotInsertDto(
            userId = userId,
            temperature = stage3.tempC,
            humidity = stage3.humidity,
            pressure = stage3.pressureHpa,
            location = location,
            riskModifier = riskModifier
        )
        return SupabaseHealthRest.post("weather_snapshots", dto)
    }

    suspend fun fetchLatest(userId: String): Result<List<WeatherSnapshotRowDto>> {
        return SupabaseHealthRest.get("weather_snapshots") {
            parameter("user_id", "eq.$userId")
            parameter("order", "created_at.desc")
            parameter("limit", "1")
        }
    }
}
