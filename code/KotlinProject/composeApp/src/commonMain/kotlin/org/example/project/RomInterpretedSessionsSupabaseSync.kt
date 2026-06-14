package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Upload-only sync of on-device [InterpretedRomLocalHistory] into Supabase [ra_lens_sessions].
 * Idempotent via [RaLensSessionRepository.syncInterpretedSessionsMissing].
 */
object RomInterpretedSessionsSupabaseSync {

    suspend fun syncLocalInterpretedHistory(userId: String): Result<Int> =
        withContext(Dispatchers.Default) {
            val local = InterpretedRomLocalHistory.loadInterpretedSessions().orEmpty()
            RaLensSessionRepository.syncInterpretedSessionsMissing(userId, local)
        }
}
