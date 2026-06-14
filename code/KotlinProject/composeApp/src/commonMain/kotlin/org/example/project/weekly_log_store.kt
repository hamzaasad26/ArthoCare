package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class WeeklyLogSnapshot(
    val pain: Double,
    val stiffness: Double,
    val fatigue: Double,
    val physicalDifficulty: Double,
    val vigorousDays: Double,
    val vigorousHours: Double,
    val moderateDays: Double,
    val moderateHours: Double,
    val walkingDays: Double,
    val walkingHours: Double,
    val sittingHoursPerWeekday: Double
)

object WeeklyLogStore {
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var latest: WeeklyLogSnapshot? = null
        private set
    var previous: WeeklyLogSnapshot? = null
        private set
    var latestUpdatedAtMillis: Long? = null
        private set

    fun update(snapshot: WeeklyLogSnapshot) {
        previous = latest
        latest = snapshot
        latestUpdatedAtMillis = currentTimeMillis()
        val uid = AuthService.getCurrentUser()?.id ?: return
        persistScope.launch {
            runCatching { SymptomLogRepository.insert(uid, snapshot) }
        }
    }

    /** Hydrate memory from remote timeline rows; does not POST to Supabase. */
    fun hydrateFromRemote(latestSnapshot: WeeklyLogSnapshot?, previousSnapshot: WeeklyLogSnapshot?, latestAtMillis: Long?) {
        if (latestSnapshot == null) return
        latest = latestSnapshot
        previous = previousSnapshot
        latestUpdatedAtMillis = latestAtMillis
    }
}

expect fun currentTimeMillis(): Long
