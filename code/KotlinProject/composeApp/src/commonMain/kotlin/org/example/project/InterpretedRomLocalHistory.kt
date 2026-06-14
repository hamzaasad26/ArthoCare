package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Phase 5: persists interpreted RA Lens timelines locally (no Supabase).
 * Files live beside other RA Lens debug artifacts ([RaLensDebugAndroidContext] / iOS Documents).
 */
object InterpretedRomLocalHistory {

    const val INTERPRETED_RA_LENS_HISTORY_FILE = "interpreted_ra_lens_history.json"
    const val DUMMY_RA_LENS_SESSIONS_FILE = "dummy_ra_lens_sessions.json"

    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Writes canonical interpreted sessions + raw-shaped bundles unless history already exists.
     * Set [force] to regenerate (e.g. after tweaking interpretation math during development).
     */
    suspend fun bootstrapIfNeeded(force: Boolean = false) {
        mutex.withLock {
            // To regenerate: uninstall app or clear app data to delete the cached JSON
            val exists = readPrivateAppFile(INTERPRETED_RA_LENS_HISTORY_FILE)?.isNotEmpty() == true
            if (!force && exists) return
            val (sessions, bundles) = RomDummyLongitudinalGenerator.generateTimeline()
            writePrivateAppFile(
                INTERPRETED_RA_LENS_HISTORY_FILE,
                json.encodeToString(
                    ListSerializer(InterpretedRaLensSession.serializer()),
                    sessions
                ).encodeToByteArray()
            )
            writePrivateAppFile(
                DUMMY_RA_LENS_SESSIONS_FILE,
                json.encodeToString(
                    ListSerializer(DummyRaLensSessionBundle.serializer()),
                    bundles
                ).encodeToByteArray()
            )
        }
    }

    suspend fun loadInterpretedSessions(): List<InterpretedRaLensSession>? {
        val bytes = readPrivateAppFile(INTERPRETED_RA_LENS_HISTORY_FILE) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                json.decodeFromString(
                    ListSerializer(InterpretedRaLensSession.serializer()),
                    bytes.decodeToString()
                )
            }.getOrNull()
        }
    }

    suspend fun loadDummyBundles(): List<DummyRaLensSessionBundle>? {
        val bytes = readPrivateAppFile(DUMMY_RA_LENS_SESSIONS_FILE) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                json.decodeFromString(
                    ListSerializer(DummyRaLensSessionBundle.serializer()),
                    bytes.decodeToString()
                )
            }.getOrNull()
        }
    }
}
