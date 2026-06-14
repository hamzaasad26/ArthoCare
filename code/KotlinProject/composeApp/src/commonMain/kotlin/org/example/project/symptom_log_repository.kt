package org.example.project

import io.ktor.client.request.parameter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/**
 * Persistence for [symptom_logs]. No UI.
 *
 * PostgREST table name must match Supabase (`symptom_logs` per migrations).
 * If your project uses a different table name, align it here and in RLS.
 */
object SymptomLogRepository {

    /** Wide enough window so older real-world logs still hydrate the store. */
    const val LONGITUDINAL_SYMPTOM_FETCH_DAYS: Int = 400

    private val json get() = SupabaseHealthRest.timelineJson

    fun weeklySnapshotToInsert(userId: String, snapshot: WeeklyLogSnapshot): SymptomLogInsertDto {
        val embedded = WeeklyLogEmbeddedNotes(
            physicalDifficulty = snapshot.physicalDifficulty,
            vigorousDays = snapshot.vigorousDays,
            vigorousHours = snapshot.vigorousHours,
            moderateDays = snapshot.moderateDays,
            moderateHours = snapshot.moderateHours,
            walkingDays = snapshot.walkingDays,
            walkingHours = snapshot.walkingHours,
            sittingHoursPerWeekday = snapshot.sittingHoursPerWeekday
        )
        return SymptomLogInsertDto(
            userId = userId,
            painLevel = snapshot.pain.roundToInt().coerceIn(0, 10),
            fatigueLevel = snapshot.fatigue.roundToInt().coerceIn(0, 10),
            stiffnessLevel = snapshot.stiffness.roundToInt().coerceIn(0, 10),
            activityLevel = "weekly_summary",
            notes = json.encodeToString(WeeklyLogEmbeddedNotes.serializer(), embedded),
            source = "manual"
        )
    }

    suspend fun insert(userId: String, snapshot: WeeklyLogSnapshot): Result<Unit> {
        return SupabaseHealthRest.post("symptom_logs", weeklySnapshotToInsert(userId, snapshot))
    }

    suspend fun fetchLatest(userId: String, limit: Int): Result<List<SymptomLogRowDto>> {
        return SupabaseHealthRest.get("symptom_logs") {
            parameter("user_id", "eq.$userId")
            parameter("order", "created_at.desc")
            parameter("limit", "$limit")
        }
    }

    suspend fun fetchSinceDays(userId: String, days: Int): Result<List<SymptomLogRowDto>> {
        val cutMillis = Clock.System.now().toEpochMilliseconds() - days * 24L * 60L * 60L * 1000L
        val cutIso = Instant.fromEpochMilliseconds(cutMillis).toString()
        return SupabaseHealthRest.get("symptom_logs") {
            parameter("user_id", "eq.$userId")
            parameter("created_at", "gte.$cutIso")
            parameter("order", "created_at.asc")
        }
    }

    fun rowToWeeklySnapshot(row: SymptomLogRowDto): WeeklyLogSnapshot {
        val extras = row.notes?.let {
            runCatching {
                json.decodeFromString<WeeklyLogEmbeddedNotes>(it)
            }.getOrNull()
        }
        val difficultyFromNotes = extras?.physicalDifficulty
        val difficultyFromColumn = row.physicalDifficultyLevel?.toDouble()
        return WeeklyLogSnapshot(
            pain = row.painLevel.toDouble(),
            stiffness = row.stiffnessLevel.toDouble(),
            fatigue = row.fatigueLevel.toDouble(),
            physicalDifficulty = difficultyFromNotes ?: difficultyFromColumn ?: 0.0,
            vigorousDays = extras?.vigorousDays ?: 0.0,
            vigorousHours = extras?.vigorousHours ?: 0.0,
            moderateDays = extras?.moderateDays ?: 0.0,
            moderateHours = extras?.moderateHours ?: 0.0,
            walkingDays = extras?.walkingDays ?: 0.0,
            walkingHours = extras?.walkingHours ?: 0.0,
            sittingHoursPerWeekday = extras?.sittingHoursPerWeekday ?: 0.0
        )
    }

    fun parseCreatedAtMillis(row: SymptomLogRowDto): Long =
        runCatching { Instant.parse(row.createdAt).toEpochMilliseconds() }.getOrElse { currentTimeMillis() }
}
