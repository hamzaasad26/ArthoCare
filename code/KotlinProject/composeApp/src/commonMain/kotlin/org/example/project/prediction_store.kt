package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class CachedPredictionGauges(
    val populationPct: Int,
    val weatherFlarePct: Int,
    val overallPct: Int,
    /** Mirrors model symptom-derived channel when available; else may equal [overallPct] for fallback. */
    val symptomContributionPct: Int,
    val capturedAtMillis: Long,
    val isFallbackEstimate: Boolean = false
)

object PredictionStore {
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var latestUpdatedAtMillis: Long? = null
        private set

    var cachedGauges: CachedPredictionGauges? = null
        private set

    fun markUpdated() {
        latestUpdatedAtMillis = currentTimeMillis()
    }

    /**
     * Updates gauges and optionally persists a prediction + weather snapshot.
     * [persistRemote] false when hydrating from Supabase.
     */
    fun cachePredictionGaugeSnapshot(
        populationPct: Int,
        weatherFlarePct: Int,
        overallPct: Int,
        overallResponse: OverallApiResponse? = null,
        stage3Payload: Stage3ApiRequest? = null,
        persistRemote: Boolean = true
    ) {
        val capturedAt = currentTimeMillis()
        val symptomPct = overallResponse?.let { formatRiskPercent(it.symptomDerived.probability) } ?: overallPct
        cachedGauges = CachedPredictionGauges(
            populationPct = populationPct,
            weatherFlarePct = weatherFlarePct,
            overallPct = overallPct,
            symptomContributionPct = symptomPct,
            capturedAtMillis = capturedAt,
            isFallbackEstimate = false
        )
        latestUpdatedAtMillis = capturedAt

        if (!persistRemote) return
        val uid = AuthService.getCurrentUser()?.id ?: return
        persistScope.launch {
            runCatching {
                overallResponse?.let { PredictionSnapshotRepository.insertFromOverall(uid, it) }
                stage3Payload?.let { WeatherSnapshotRepository.insertFromStage3(uid, it, location = null) }
            }
        }
    }

    /** Restore gauges from a persisted row without writing back to Supabase. */
    fun hydrateFromRemoteSnapshot(
        populationPct: Int,
        weatherFlarePct: Int,
        overallPct: Int,
        capturedAtMillis: Long,
        symptomContributionPct: Int = overallPct
    ) {
        cachedGauges = CachedPredictionGauges(
            populationPct = populationPct,
            weatherFlarePct = weatherFlarePct,
            overallPct = overallPct,
            symptomContributionPct = symptomContributionPct,
            capturedAtMillis = capturedAtMillis,
            isFallbackEstimate = false
        )
        latestUpdatedAtMillis = capturedAtMillis
    }

    /**
     * When no prediction snapshot exists yet, derive a single overall gauge from
     * longitudinal symptom averages so the dashboard has a non-empty signal.
     */
    fun hydrateFromSymptomFallback(overallPct: Int, symptomContributionPct: Int) {
        if (cachedGauges != null) return
        cachedGauges = CachedPredictionGauges(
            populationPct = -1,
            weatherFlarePct = -1,
            overallPct = overallPct,
            symptomContributionPct = symptomContributionPct,
            capturedAtMillis = Clock.System.now().toEpochMilliseconds(),
            isFallbackEstimate = true
        )
        latestUpdatedAtMillis = cachedGauges!!.capturedAtMillis
    }
}
