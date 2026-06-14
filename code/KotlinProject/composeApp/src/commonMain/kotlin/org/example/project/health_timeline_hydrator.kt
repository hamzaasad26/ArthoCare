package org.example.project

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Loads remote timeline rows into existing in-memory stores (Phase 1 migration).
 * Called after login — does not touch UI.
 */
object HealthTimelineHydrator {

    suspend fun hydrateAfterLogin(userId: String) {
        println("[SymptomHydration] hydrateAfterLogin authUserId=$userId")
        // (1) Existing two-slot hydration — feeds [WeeklyLogStore.latest] /
        //     [WeeklyLogStore.previous] which the ML pipeline and the
        //     Dashboard "Recommended Next Action" card depend on. Untouched.
        SymptomLogRepository.fetchLatest(userId, limit = 2).getOrNull()?.let { logs ->
            val latestRow = logs.getOrNull(0) ?: return@let
            val prevRow = logs.getOrNull(1)
            val latestSnap = SymptomLogRepository.rowToWeeklySnapshot(latestRow)
            val prevSnap = prevRow?.let { SymptomLogRepository.rowToWeeklySnapshot(it) }
            val at = SymptomLogRepository.parseCreatedAtMillis(latestRow)
            WeeklyLogStore.hydrateFromRemote(
                latestSnapshot = latestSnap,
                previousSnapshot = prevSnap,
                latestAtMillis = at
            )
        }

        // (2) Longitudinal window for analytics — must match or exceed any UI
        // "recent N days" filter so rows are not dropped client-side after fetch.
        val fetchDays = SymptomLogRepository.LONGITUDINAL_SYMPTOM_FETCH_DAYS
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val cutMillis = nowMillis - fetchDays * 24L * 60L * 60L * 1000L
        val cutIso = Instant.fromEpochMilliseconds(cutMillis).toString()
        val symResult = SymptomLogRepository.fetchSinceDays(userId, days = fetchDays)
        symResult.exceptionOrNull()?.let { e ->
            println("[SymptomHydration] GET symptom_logs FAILED userId=$userId days=$fetchDays err=${e.message}")
        }
        if (symResult.isSuccess) {
            val rows = symResult.getOrNull() ?: emptyList()
            val newest = rows.lastOrNull()?.createdAt
            val oldest = rows.firstOrNull()?.createdAt
            println("[SymptomHydration] userId=$userId fetchedCount=${rows.size} cutoffIso=$cutIso newestRow=$newest oldestRow=$oldest")
            rows.take(3).forEachIndexed { i, r ->
                println(
                    "[SymptomHydration]   row[$i] id=${r.id} created_at=${r.createdAt} " +
                        "pain=${r.painLevel} fatigue=${r.fatigueLevel} stiffness=${r.stiffnessLevel} " +
                        "physical_difficulty_level=${r.physicalDifficultyLevel}"
                )
            }
            SymptomLogStore.hydrateFromRows(rows)
            try {
                OfflineCache.saveSymptomLogs(rows)
            } catch (_: Exception) {
            }
            OfflineStateHolder.setOfflineMode(false)
        } else {
            println("[SymptomHydration] userId=$userId symptom_logs result empty after failure")
            val cachedRows = OfflineCache.loadSymptomLogs()
                ?.filter { it.userId == userId }
                ?.takeIf { it.isNotEmpty() }
            if (cachedRows != null) {
                SymptomLogStore.hydrateFromRows(cachedRows)
                OfflineStateHolder.setOfflineMode(true)
            } else {
                println("[SymptomHydration] userId=$userId no cached symptom_logs for offline")
            }
        }

        RaLensSessionRepository.fetchLatest(userId).getOrNull()?.firstOrNull()?.let { romRow ->
            runCatching { RaLensSessionRepository.decodeAnalysis(romRow) }.getOrNull()
                ?.let { RaLensStore.replaceWithHydratedAnalysis(it) }
        }

        PredictionSnapshotRepository.fetchLatest(userId).getOrNull()?.firstOrNull()?.let { predRow ->
            val capturedAt = runCatching {
                Instant.parse(predRow.createdAt).toEpochMilliseconds()
            }.getOrElse { currentTimeMillis() }

            val overall = PredictionSnapshotRepository.decodeOverall(predRow)
            if (overall != null) {
                PredictionStore.hydrateFromRemoteSnapshot(
                    populationPct = formatRiskPercent(overall.population.probability),
                    weatherFlarePct = formatRiskPercent(overall.weatherFlare.probability),
                    overallPct = formatRiskPercent(overall.overallProbability),
                    capturedAtMillis = capturedAt,
                    symptomContributionPct = formatRiskPercent(overall.symptomDerived.probability)
                )
            } else {
                val symFromRow = ((predRow.symptomContribution ?: predRow.overallRisk) * 100).toInt()
                    .coerceIn(0, 100)
                PredictionStore.hydrateFromRemoteSnapshot(
                    populationPct = symFromRow,
                    weatherFlarePct = (predRow.weatherContribution * 100).toInt().coerceIn(0, 100),
                    overallPct = (predRow.overallRisk * 100).toInt().coerceIn(0, 100),
                    capturedAtMillis = capturedAt,
                    symptomContributionPct = symFromRow
                )
            }
        }

        if (PredictionStore.cachedGauges == null && !SymptomLogStore.isEmpty()) {
            val avgPain = SymptomLogStore.averagePain()
            val avgFatigue = SymptomLogStore.averageFatigue()
            val avgStiffness = SymptomLogStore.averageStiffness()
            val symptomBurden = ((avgPain + avgFatigue + avgStiffness) / 30.0 * 100)
                .toInt()
                .coerceIn(5, 95)
            PredictionStore.hydrateFromSymptomFallback(
                overallPct = symptomBurden,
                symptomContributionPct = symptomBurden
            )
        }
    }
}
