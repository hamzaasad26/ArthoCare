package org.example.project

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists critical Supabase-backed payloads beside ROM history
 * ([writePrivateAppFile] / [readPrivateAppFile]).
 */
object OfflineCache {

    private const val USER_CACHE_FILE = "cache_user.json"
    private const val PROFILE_CACHE_FILE = "cache_user_profile.json"
    private const val SYMPTOM_LOGS_CACHE_FILE = "cache_symptom_logs.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun saveUser(user: User) {
        val payload = json.encodeToString(User.serializer(), user).encodeToByteArray()
        writePrivateAppFile(USER_CACHE_FILE, payload)
    }

    suspend fun loadUser(): User? = withContext(Dispatchers.Default) {
        runCatching {
            val raw = readPrivateAppFile(USER_CACHE_FILE)?.decodeToString()?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            json.decodeFromString(User.serializer(), raw)
        }.getOrNull()
    }

    suspend fun saveProfile(profile: UserProfile) {
        val payload = json.encodeToString(UserProfile.serializer(), profile).encodeToByteArray()
        writePrivateAppFile(PROFILE_CACHE_FILE, payload)
    }

    suspend fun loadProfile(): UserProfile? = withContext(Dispatchers.Default) {
        runCatching {
            val raw = readPrivateAppFile(PROFILE_CACHE_FILE)?.decodeToString()?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            json.decodeFromString(UserProfile.serializer(), raw)
        }.getOrNull()
    }

    suspend fun saveSymptomLogs(rows: List<SymptomLogRowDto>) {
        val ser = ListSerializer(SymptomLogRowDto.serializer())
        val payload = json.encodeToString(ser, rows).encodeToByteArray()
        writePrivateAppFile(SYMPTOM_LOGS_CACHE_FILE, payload)
    }

    suspend fun loadSymptomLogs(): List<SymptomLogRowDto>? = withContext(Dispatchers.Default) {
        runCatching {
            val raw = readPrivateAppFile(SYMPTOM_LOGS_CACHE_FILE)?.decodeToString()?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            json.decodeFromString(ListSerializer(SymptomLogRowDto.serializer()), raw)
        }.getOrNull()
    }

    suspend fun clearAll() {
        listOf(USER_CACHE_FILE, PROFILE_CACHE_FILE, SYMPTOM_LOGS_CACHE_FILE).forEach { name ->
            writePrivateAppFile(name, ByteArray(0))
        }
    }
}

/** Compose-visible flag for “cached data” banner; toggled from auth / hydrator / profile only. */
object OfflineStateHolder {
    private val offlineModeState = mutableStateOf(false)

    /** Read during composition so the dashboard banner recomposes when this changes. */
    val isOfflineMode: Boolean get() = offlineModeState.value

    fun setOfflineMode(value: Boolean) {
        offlineModeState.value = value
    }
}
