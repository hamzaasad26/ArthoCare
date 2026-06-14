package org.example.project

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Patient-facing formatters shared by Dashboard, Insights, RA Lens results,
 * RA Predictions and any future surface that renders user-visible joint names
 * or assessment timestamps.
 *
 * Important: These helpers are for *display only*. Do not use them on strings
 * passed to the ML backend, Supabase rows, debug logs, or analytics keys —
 * the upstream code paths must keep their canonical raw forms (e.g. "left_knee").
 */

/**
 * Converts a snake_case / lower-case joint identifier (e.g. "left_knee") into a
 * patient-friendly title-cased label (e.g. "Left Knee").
 */
fun String.toDisplayJointName(): String =
    this.replace("_", " ").split(" ")
        .joinToString(" ") { token ->
            token.replaceFirstChar { c -> c.uppercase() }
        }

/**
 * Humanises an ISO-8601 timestamp into a relative ("Today", "Yesterday",
 * "3 days ago") or short month-day label ("Apr 12") suitable for cards.
 *
 * Falls back to the raw string when parsing fails so we never crash a card
 * over a malformed value coming back from the network or local cache.
 */
fun formatAssessmentTime(isoTimestamp: String): String {
    return runCatching {
        val instant = Instant.parse(isoTimestamp)
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val thenMillis = instant.toEpochMilliseconds()
        val diffHours = (nowMillis - thenMillis) / (1000L * 60L * 60L)
        when {
            diffHours < 24 -> "Today"
            diffHours < 48 -> "Yesterday"
            diffHours < 168 -> "${(diffHours / 24).toInt()} days ago"
            else -> {
                val date = instant.toString().substring(0, 10) // YYYY-MM-DD
                val parts = date.split("-")
                "${monthName(parts[1].toInt())} ${parts[2].toInt()}"
            }
        }
    }.getOrElse { isoTimestamp }
}

/**
 * Converts a 1-based month index (1=Jan … 12=Dec) into its three-letter
 * abbreviation. Returns an empty string for out-of-range input rather than
 * throwing, since this is only used by [formatAssessmentTime].
 */
fun monthName(m: Int): String {
    val names = listOf(
        "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return names.getOrNull(m).orEmpty()
}
