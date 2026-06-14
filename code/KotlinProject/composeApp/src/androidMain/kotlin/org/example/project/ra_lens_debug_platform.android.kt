package org.example.project

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RaLensDebugAndroidContext {
    var appContext: Context? = null
}

internal actual suspend fun readRaLensDebugJsonBytes(): ByteArray? =
    withContext(Dispatchers.IO) {
        val ctx = RaLensDebugAndroidContext.appContext ?: return@withContext null
        val f = File(ctx.filesDir, RA_LENS_DEBUG_FILENAME)
        if (!f.exists()) return@withContext null
        f.readBytes()
    }

internal actual suspend fun writeRaLensDebugJsonBytes(bytes: ByteArray) =
    withContext(Dispatchers.IO) {
        val ctx = RaLensDebugAndroidContext.appContext
            ?: throw IllegalStateException("RaLens debug: Android context not set (MainActivity)")
        File(ctx.filesDir, RA_LENS_DEBUG_FILENAME).writeBytes(bytes)
    }

internal actual fun newRaLensSessionId(): String = UUID.randomUUID().toString()

internal actual fun raLensDebugLog(tag: String, message: String) {
    Log.i(tag, message)
}
