package org.example.project

/**
 * Unified read surface for longitudinal health data (aggregation-ready).
 * No UI; callers supply [userId] (typically [AuthService.getCurrentUser]?.id).
 */
object HealthTimelineRepository {

    suspend fun getLast7DaysSymptoms(userId: String): Result<List<SymptomLogRowDto>> =
        SymptomLogRepository.fetchSinceDays(userId, days = 7)

    suspend fun getLast30DaysSymptoms(userId: String): Result<List<SymptomLogRowDto>> =
        SymptomLogRepository.fetchSinceDays(userId, days = 30)

    suspend fun getLatestRaLensSession(userId: String): Result<RaLensSessionRowDto?> =
        RaLensSessionRepository.fetchLatest(userId).map { it.firstOrNull() }

    suspend fun getLatestPrediction(userId: String): Result<PredictionSnapshotRowDto?> =
        PredictionSnapshotRepository.fetchLatest(userId).map { it.firstOrNull() }

    suspend fun getTrendData(userId: String): Result<HealthTrendBundle> {
        val s7r = SymptomLogRepository.fetchSinceDays(userId, 7)
        if (s7r.isFailure) return Result.failure(s7r.exceptionOrNull() ?: Exception("symptoms 7d"))
        val s7 = s7r.getOrNull()!!
        val s30r = SymptomLogRepository.fetchSinceDays(userId, 30)
        if (s30r.isFailure) return Result.failure(s30r.exceptionOrNull() ?: Exception("symptoms 30d"))
        val s30 = s30r.getOrNull()!!
        val predsR = PredictionSnapshotRepository.fetchSinceDays(userId, 30, limit = 30)
        if (predsR.isFailure) return Result.failure(predsR.exceptionOrNull() ?: Exception("predictions"))
        val preds = predsR.getOrNull()!!
        val romR = RaLensSessionRepository.fetchSinceDays(userId, 30, limit = 30)
        if (romR.isFailure) return Result.failure(romR.exceptionOrNull() ?: Exception("ra_lens"))
        val rom = romR.getOrNull()!!
        return Result.success(
            HealthTrendBundle(
                symptomsLast7 = s7,
                symptomsLast30 = s30,
                recentPredictions = preds,
                recentRaLens = rom
            )
        )
    }

    suspend fun getRecentHealthSummary(userId: String): Result<RecentHealthSummary> {
        val symptoms7 = getLast7DaysSymptoms(userId).getOrElse { emptyList() }
        val symptoms30 = getLast30DaysSymptoms(userId).getOrElse { emptyList() }
        val latestSymptom = SymptomLogRepository.fetchLatest(userId, limit = 1).getOrNull()?.firstOrNull()
        val latestPrediction = getLatestPrediction(userId).getOrNull()
        val latestRaLens = getLatestRaLensSession(userId).getOrNull()
        return Result.success(
            RecentHealthSummary(
                latestSymptom = latestSymptom,
                latestPrediction = latestPrediction,
                latestRaLens = latestRaLens,
                symptomCount7d = symptoms7.size,
                symptomCount30d = symptoms30.size
            )
        )
    }
}
