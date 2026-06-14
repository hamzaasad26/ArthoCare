package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Abstracts where interpreted sessions come from (Supabase first when logged in; else local JSON).
 */
interface InterpretedRomSessionSource {
    suspend fun loadInterpretedSessions(): List<InterpretedRaLensSession>?

    /** Invalidates in-repo cache when this value changes (e.g. login / user switch). */
    fun cachePartitionKey(): String
}

/** Default: [InterpretedRomLocalHistory] on-device JSON only. */
class LocalInterpretedRomSessionSource : InterpretedRomSessionSource {
    override fun cachePartitionKey(): String = "local_file"

    override suspend fun loadInterpretedSessions(): List<InterpretedRaLensSession>? =
        InterpretedRomLocalHistory.loadInterpretedSessions()
}

/**
 * Prefers Supabase interpreted longitudinal rows for [userIdProvider]; falls back to local file if empty or on failure.
 */
class CompositeInterpretedRomSessionSource(
    private val userIdProvider: () -> String?
) : InterpretedRomSessionSource {

    override fun cachePartitionKey(): String =
        userIdProvider()?.takeIf { it.isNotBlank() } ?: "anon_local"

    override suspend fun loadInterpretedSessions(): List<InterpretedRaLensSession>? {
        val uid = userIdProvider()?.takeIf { it.isNotBlank() }
        val remote = uid?.let {
            RaLensSessionRepository.fetchInterpretedLongitudinal(it).getOrNull()
        }
        val base = when {
            !remote.isNullOrEmpty() -> remote
            else -> InterpretedRomLocalHistory.loadInterpretedSessions()
        } ?: return null
        return RomLongitudinalAnalytics.indexSessionsByDay(base).map { it.third }
    }
}

/**
 * Longitudinal ROM dashboard data: load once per cache partition, derive many times.
 */
interface RomInsightsRepository {

    suspend fun getAllSessions(): List<InterpretedRaLensSession>

    suspend fun getLatestSession(): InterpretedRaLensSession?

    suspend fun getRomTrendPoints(): List<RomTrendPoint>

    suspend fun getWeakestJointHistory(): List<WeakestJointPoint>

    suspend fun getSeverityProgression(): List<SeverityDistribution>

    suspend fun detectFlareMoments(): List<FlareMoment>

    suspend fun loadDashboardState(): RomDashboardLongitudinalState

    suspend fun invalidateCache()
}

class DefaultRomInsightsRepository(
    private val sessionSource: InterpretedRomSessionSource = LocalInterpretedRomSessionSource()
) : RomInsightsRepository {

    private val mutex = Mutex()
    private var cachedSessions: List<InterpretedRaLensSession>? = null
    private var cachedKey: String? = null

    override suspend fun invalidateCache() {
        mutex.withLock {
            cachedSessions = null
            cachedKey = null
        }
    }

    private suspend fun sessionsSnapshot(): List<InterpretedRaLensSession> {
        val key = sessionSource.cachePartitionKey()
        mutex.withLock {
            if (cachedSessions != null && cachedKey == key) return cachedSessions!!
        }

        val decoded = withContext(Dispatchers.Default) {
            sessionSource.loadInterpretedSessions().orEmpty()
        }

        return mutex.withLock {
            when {
                cachedSessions != null && cachedKey == key -> cachedSessions!!
                else -> {
                    cachedKey = key
                    decoded.also { cachedSessions = it }
                }
            }
        }
    }

    private suspend fun indexedTimeline(): List<Triple<Int, Int, InterpretedRaLensSession>> =
        RomLongitudinalAnalytics.indexSessionsByDay(sessionsSnapshot())

    override suspend fun getAllSessions(): List<InterpretedRaLensSession> =
        withContext(Dispatchers.Default) {
            indexedTimeline().map { it.third }
        }

    override suspend fun getLatestSession(): InterpretedRaLensSession? =
        withContext(Dispatchers.Default) {
            indexedTimeline().lastOrNull()?.third
        }

    override suspend fun getRomTrendPoints(): List<RomTrendPoint> =
        withContext(Dispatchers.Default) {
            RomLongitudinalAnalytics.mapRomTrendPoints(indexedTimeline())
        }

    override suspend fun getWeakestJointHistory(): List<WeakestJointPoint> =
        withContext(Dispatchers.Default) {
            RomLongitudinalAnalytics.mapWeakestJointHistory(indexedTimeline())
        }

    override suspend fun getSeverityProgression(): List<SeverityDistribution> =
        withContext(Dispatchers.Default) {
            RomLongitudinalAnalytics.mapSeverityProgression(indexedTimeline())
        }

    override suspend fun detectFlareMoments(): List<FlareMoment> =
        withContext(Dispatchers.Default) {
            RomLongitudinalAnalytics.detectFlareMoments(indexedTimeline())
        }

    override suspend fun loadDashboardState(): RomDashboardLongitudinalState =
        withContext(Dispatchers.Default) {
            RomLongitudinalAnalytics.buildDashboardState(sessionsSnapshot())
        }
}

private val defaultRomInsightsRepositoryInstance by lazy {
    DefaultRomInsightsRepository(
        CompositeInterpretedRomSessionSource { AuthService.getCurrentUser()?.id }
    )
}

fun defaultRomInsightsRepository(): RomInsightsRepository = defaultRomInsightsRepositoryInstance
