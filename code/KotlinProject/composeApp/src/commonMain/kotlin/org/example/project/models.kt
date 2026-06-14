package org.example.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    @SerialName("password_hash") val passwordHash: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("race_ethnicity") val raceEthnicity: String? = null,
    @SerialName("physical_activity") val physicalActivity: String? = null,
    val smoking: String? = null,
    val drinking: String? = null,
    @SerialName("calories_per_day") val caloriesPerDay: Int? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("caffeine_g") val caffeineG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    val hypertension: String? = null,
    val diabetes: String? = null,
    val hyperlipidemia: String? = null
)

/** Minimal request for creating a user (auth only) */
@Serializable
data class SignUpRequest(
    val username: String,
    @SerialName("password_hash") val passwordHash: String,
    @SerialName("full_name") val fullName: String
)

/** Request body for inserting user profile (after user is created) */
@Serializable
data class UserProfileInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("race_ethnicity") val raceEthnicity: String? = null,
    @SerialName("physical_activity") val physicalActivity: String? = null,
    val smoking: String? = null,
    val drinking: String? = null,
    @SerialName("calories_per_day") val caloriesPerDay: Int? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("caffeine_g") val caffeineG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    val hypertension: String? = null,
    val diabetes: String? = null,
    val hyperlipidemia: String? = null,
    @SerialName("pain_level") val painLevel: Int? = null,
    @SerialName("stiffness_level") val stiffnessLevel: Int? = null,
    @SerialName("fatigue_level") val fatigueLevel: Int? = null,
    @SerialName("physical_difficulty_level") val physicalDifficultyLevel: Int? = null,
    @SerialName("vigorous_days") val vigorousDays: Int? = null,
    @SerialName("vigorous_hours") val vigorousHours: Double? = null,
    @SerialName("moderate_days") val moderateDays: Int? = null,
    @SerialName("moderate_hours") val moderateHours: Double? = null,
    @SerialName("walking_days") val walkingDays: Int? = null,
    @SerialName("walking_hours") val walkingHours: Double? = null,
    @SerialName("sitting_hours_per_weekday") val sittingHoursPerWeekday: Double? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/** In-memory form state for the multi-step sign-up flow (not serialized to API) */
data class SignUpFormData(
    // Step 0: Account
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    // Step 1: Personal info
    val fullName: String = "",
    val birthDay: String = "",
    val birthMonth: String = "",
    val birthYear: String = "",
    val gender: String = "",
    val raceEthnicity: String = "",
    // Step 2: Lifestyle & diet
    val physicalActivity: String = "",
    val smoking: String = "",
    val drinking: String = "",
    val caloriesPerDay: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val caffeineG: String = "",
    val fiberG: String = "",
    val hypertension: String = "",
    val diabetes: String = "",
    val hyperlipidemia: String = ""
)
