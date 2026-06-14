package org.example.project

/**
 * UI-facing snapshot combining the authenticated user with their (optional)
 * extended profile row from Supabase `user_profiles`.
 */
data class ProfileSnapshot(
    val user: User,
    val profile: UserProfile?
)

/**
 * Thin wrapper around [SupabaseClient.getUserProfileByUserId] so screens depend
 * on a focused, immutable contract instead of touching the Supabase client
 * directly. Keep this file intentionally small — fetch only, no UI state.
 */
object ProfileRepository {
    suspend fun loadSnapshot(user: User): Result<ProfileSnapshot> {
        val remote = SupabaseClient.getUserProfileByUserId(user.id)
        if (remote.isSuccess) {
            val profile = remote.getOrNull()
            if (profile != null) {
                try {
                    OfflineCache.saveProfile(profile)
                } catch (_: Exception) {
                }
                OfflineStateHolder.setOfflineMode(false)
                return Result.success(ProfileSnapshot(user = user, profile = profile))
            }
            val cached = OfflineCache.loadProfile()
            if (cached != null && cached.userId == user.id) {
                OfflineStateHolder.setOfflineMode(true)
                return Result.success(ProfileSnapshot(user = user, profile = cached))
            }
            OfflineStateHolder.setOfflineMode(false)
            return Result.success(ProfileSnapshot(user = user, profile = null))
        }
        val cached = OfflineCache.loadProfile()
        if (cached != null && cached.userId == user.id) {
            OfflineStateHolder.setOfflineMode(true)
            return Result.success(ProfileSnapshot(user = user, profile = cached))
        }
        return Result.failure(remote.exceptionOrNull() ?: Exception("Profile unavailable offline"))
    }
}
