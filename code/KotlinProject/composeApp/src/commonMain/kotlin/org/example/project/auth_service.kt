package org.example.project

import kotlinx.coroutines.runBlocking

object AuthService {
    private var currentUser: User? = null

    /** Optional Supabase Auth JWT; required for RLS-backed timeline REST calls. Defaults to anon key. */
    private var supabaseAccessToken: String? = null

    fun attachSupabaseAccessToken(token: String?) {
        supabaseAccessToken = token
    }

    internal fun bearerForSupabaseRest(): String =
        supabaseAccessToken ?: SupabaseConfig.SUPABASE_KEY

    /**
     * Sign up: create user (auth) + user_profile (extended data).
     * Uses the new multi-step form data.
     */
    suspend fun signUp(data: SignUpFormData): Result<User> {
        if (SupabaseClient.checkUsernameExists(data.username)) {
            return Result.failure(Exception("Username already exists"))
        }

        val passwordHash = PasswordHasher.hash(data.password)

        val signUpRequest = SignUpRequest(
            username = data.username,
            passwordHash = passwordHash,
            fullName = data.fullName
        )

        val userResult = SupabaseClient.insertUser(signUpRequest)
        if (userResult.isFailure) {
            return userResult
        }

        val user = userResult.getOrNull()!!
        val dateOfBirth = buildDateOfBirth(data.birthDay, data.birthMonth, data.birthYear)
        
        val profile = UserProfileInsert(
            userId = user.id,
            dateOfBirth = dateOfBirth,
            gender = data.gender.ifBlank { null },
            raceEthnicity = data.raceEthnicity.ifBlank { null },
            physicalActivity = data.physicalActivity.ifBlank { null },
            smoking = data.smoking.ifBlank { null },
            drinking = data.drinking.ifBlank { null },
            caloriesPerDay = data.caloriesPerDay.toIntOrNull(),
            proteinG = data.proteinG.toDoubleOrNull(),
            carbsG = data.carbsG.toDoubleOrNull(),
            fatG = data.fatG.toDoubleOrNull(),
            caffeineG = data.caffeineG.toDoubleOrNull(),
            fiberG = data.fiberG.toDoubleOrNull(),
            hypertension = data.hypertension.ifBlank { null },
            diabetes = data.diabetes.ifBlank { null },
            hyperlipidemia = data.hyperlipidemia.ifBlank { null }
        )

        val profileResult = SupabaseClient.insertUserProfile(profile)
        if (profileResult.isFailure) {
            // User was created but profile failed - could add cleanup logic
            return Result.failure(profileResult.exceptionOrNull()!!)
        }

        currentUser = user
        try {
            OfflineCache.saveUser(user)
        } catch (_: Exception) {
        }
        OfflineStateHolder.setOfflineMode(false)
        HealthTimelineHydrator.hydrateAfterLogin(user.id)
        return Result.success(user)
    }

    suspend fun login(username: String, password: String): Result<User> {
        val enteredPasswordHash = PasswordHasher.hash(password)
        return try {
            val result = SupabaseClient.getUserByUsername(username)
            if (result.isFailure) {
                return loginWithOfflineFallback(username, enteredPasswordHash)
            }
            val user = result.getOrNull()
            if (user == null) {
                return loginWithOfflineFallback(username, enteredPasswordHash)
            }
            if (user.passwordHash != enteredPasswordHash) {
                return Result.failure(Exception("Invalid username or password"))
            }
            currentUser = user
            try {
                OfflineCache.saveUser(user)
            } catch (_: Exception) {
            }
            OfflineStateHolder.setOfflineMode(false)
            HealthTimelineHydrator.hydrateAfterLogin(user.id)
            Result.success(user)
        } catch (e: Exception) {
            loginWithOfflineFallback(username, enteredPasswordHash)
        }
    }

    private suspend fun loginWithOfflineFallback(
        username: String,
        enteredPasswordHash: String,
    ): Result<User> {
        val cachedUser = OfflineCache.loadUser()
        if (cachedUser != null &&
            cachedUser.username == username &&
            cachedUser.passwordHash == enteredPasswordHash
        ) {
            currentUser = cachedUser
            OfflineStateHolder.setOfflineMode(true)
            HealthTimelineHydrator.hydrateAfterLogin(cachedUser.id)
            return Result.success(cachedUser)
        }
        return Result.failure(Exception("Login failed — check your connection"))
    }

    fun getCurrentUser(): User? = currentUser
    fun logout() {
        currentUser = null
        attachSupabaseAccessToken(null)
        OfflineStateHolder.setOfflineMode(false)
        runBlocking {
            try {
                OfflineCache.clearAll()
            } catch (_: Exception) {
                // best-effort wipe of private cache files
            }
        }
    }
    fun isLoggedIn(): Boolean = currentUser != null
}

/** Builds YYYY-MM-DD from day/month/year, or null if any is blank */
private fun buildDateOfBirth(day: String, month: String, year: String): String? {
    if (day.isBlank() || month.isBlank() || year.isBlank()) return null
    val d = day.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    if (d !in 1..31 || m !in 1..12 || y < 1900 || y > 2100) return null
    return "%04d-%02d-%02d".format(y, m, d)
}
