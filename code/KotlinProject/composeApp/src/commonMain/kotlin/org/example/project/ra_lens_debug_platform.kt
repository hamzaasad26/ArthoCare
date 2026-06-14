package org.example.project

internal const val RA_LENS_DEBUG_FILENAME = "ra_lens_debug_sessions.json"

internal expect suspend fun readRaLensDebugJsonBytes(): ByteArray?

internal expect suspend fun writeRaLensDebugJsonBytes(bytes: ByteArray)

internal expect fun newRaLensSessionId(): String

internal expect fun raLensDebugLog(tag: String, message: String)
