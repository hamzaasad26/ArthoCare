package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant
/**
 * Longitudinal symptom-log cache that mirrors the role
 * [defaultRomInsightsRepository] plays for ROM data.
 *
 * Why this exists:
 * [WeeklyLogStore] is a deliberate two-slot summary cache (only `latest` and
 * `previous`). It cannot grow beyond two snapshots, which is fine for the
 * Dashboard "Recommended Next Action" card and the ML pipeline that needs
 * "now vs prior" deltas, but it cannot back longitudinal analytics
 * (week-of-history charts, multi-row trend arrows, longitudinal averages).
 *
 * [SymptomLogStore] holds the full N-day window fetched from
 * `symptom_logs` so longitudinal surfaces can render real history without
 * touching [WeeklyLogStore]. It is hydrated from
 * [HealthTimelineHydrator.hydrateAfterLogin] alongside (not instead of) the
 * existing [WeeklyLogStore.hydrateFromRemote] call.
 *
 * No POSTs ever happen here — writes still go through [WeeklyLogStore.update]
 * → [SymptomLogRepository.insert] like before.
 */
object SymptomLogStore {

    /**
     * Incremented on every [hydrateFromRows]. Compose callers should read this
     * so UI recomposes after async Supabase hydration (singleton lists are not observable).
     */
    var hydrationEpoch: Int by mutableIntStateOf(0)
        private set

    /** Raw rows in the order returned by the repository (oldest → newest). */    var recentLogs: List<SymptomLogRowDto> = emptyList()
        private set

    /**
     * Decoded snapshots paired with their server timestamp (epoch millis).
     * Same ordering as [recentLogs] (oldest → newest), so `last()` is the
     * most recent log.
     */
    var recentSnapshots: List<Pair<Long, WeeklyLogSnapshot>> = emptyList()
        private set

    /** Replace the in-memory window with the rows just fetched from Supabase. */
    fun hydrateFromRows(rows: List<SymptomLogRowDto>) {
        recentLogs = rows
        recentSnapshots = rows.map { row ->
            val millis = SymptomLogRepository.parseCreatedAtMillis(row)
            val snap = SymptomLogRepository.rowToWeeklySnapshot(row)
            Pair(millis, snap)
        }
        hydrationEpoch++
    }

    fun isEmpty(): Boolean = recentLogs.isEmpty()

    /**
     * Compares the average pain over the last 3 logs against the 3 logs prior
     * to that window. Returns a single-character arrow callers can drop into
     * compact UI without further branching.
     */
    fun latestPainTrend(): String {
        if (recentSnapshots.size < 2) return "→"
        val recent = recentSnapshots.takeLast(3).map { it.second.pain }
        val avg = recent.average()
        val prev = recentSnapshots.dropLast(3).takeLast(3).map { it.second.pain }.average()
        return when {
            avg < prev - 0.5 -> "↓"
            avg > prev + 0.5 -> "↑"
            else -> "→"
        }
    }

    fun averagePain(): Double =
        if (recentSnapshots.isEmpty()) 0.0
        else recentSnapshots.map { it.second.pain }.average()

    fun averageStiffness(): Double =
        if (recentSnapshots.isEmpty()) 0.0
        else recentSnapshots.map { it.second.stiffness }.average()

    fun averageFatigue(): Double =
        if (recentSnapshots.isEmpty()) 0.0
        else recentSnapshots.map { it.second.fatigue }.average()

    /**
     * Time-series of pain severity (epoch-ms timestamp → pain 0..10).
     * Order matches [recentSnapshots] (oldest → newest), ready to drop into
     * a chart that derives its X-axis from the values' timestamps.
     */
    fun weeklyPainSeries(): List<Pair<Long, Double>> =
        recentSnapshots.map { Pair(it.first, it.second.pain) }

    fun hasMinimumData(minRows: Int = 3): Boolean = recentLogs.size >= minRows

    /**
     * Symptom-side flare detector that backs the Insights "Flare Detection"
     * card.
     *
     * Threshold scale: snap.pain/fatigue/stiffness are stored on a 0..10
     * Double scale (verified via [SymptomLogRepository.rowToWeeklySnapshot]
     * which builds them from row.painLevel.toDouble() where painLevel is the
     * 0..10 integer column). So `>= 6` is the raw "≥6/10" cutoff, not a
     * normalized 0..1 fraction.
     *
     * A snapshot counts as an elevated episode when *either* of:
     *   - pain ≥6 AND fatigue ≥6 on the same day (the canonical RA flare
     *     pairing — both axes elevated together), OR
     *   - stiffness ≥6 on its own (a high-stiffness day is itself
     *     clinically meaningful even when pain/fatigue stay moderate).
     *
     * Returns `(epoch_millis, formatted_label)` pairs in the same order as
     * [recentSnapshots] (oldest → newest), so callers can take `.last()` to
     * surface the most recent episode.
     */
    fun detectFlares(): List<Pair<Long, String>> {
        val threshold = 6.0
        return recentSnapshots
            .filter { (_, snap) ->
                (snap.pain >= threshold && snap.fatigue >= threshold) ||
                    snap.stiffness >= threshold
            }
            .map { (millis, snap) ->
                val date = formatAssessmentTime(Instant.fromEpochMilliseconds(millis).toString())
                Pair(
                    millis,
                    "Pain ${snap.pain.toInt()}/10 · Fatigue ${snap.fatigue.toInt()}/10 · Stiffness ${snap.stiffness.toInt()}/10 on $date"
                )
            }
    }
}
