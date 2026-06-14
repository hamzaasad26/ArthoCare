package org.example.project

/**
 * Dashboard header fields from auth + `user_profiles` (BMI optional until collected).
 */
data class RaDashboardUserInfo(
    val fullName: String,
    /** ISO-8601 date YYYY-MM-DD */
    val dateOfBirth: String,
    val gender: String,
    val bmi: Double?,
    val physicalActivity: String,
    val location: String,
)

/**
 * Age in full years from YYYY-MM-DD (KMP-safe: no JVM Calendar).
 * [referenceYear]/[referenceMonth]/[referenceDay] default to a fixed dev date if needed.
 */
fun ageFromIsoDate(
    isoDate: String,
    referenceYear: Int = 2026,
    referenceMonth: Int = 3,
    referenceDay: Int = 25,
): Int {
    val parts = isoDate.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return 0
    val (y, m, d) = parts
    var age = referenceYear - y
    if (referenceMonth < m || (referenceMonth == m && referenceDay < d)) age--
    return age.coerceAtLeast(0)
}

fun RaDashboardUserInfo.withNameFromAuth(nameFromSession: String?): RaDashboardUserInfo {
    val n = nameFromSession?.takeIf { it.isNotBlank() } ?: fullName
    return copy(fullName = n)
}
