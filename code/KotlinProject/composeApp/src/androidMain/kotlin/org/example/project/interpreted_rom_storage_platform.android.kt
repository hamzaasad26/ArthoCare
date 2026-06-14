package org.example.project

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun writePrivateAppFile(relativeName: String, bytes: ByteArray) =
    withContext(Dispatchers.IO) {
        val ctx = RaLensDebugAndroidContext.appContext
            ?: throw IllegalStateException("App context not set (MainActivity)")
        File(ctx.filesDir, relativeName).writeBytes(bytes)
    }

internal actual suspend fun readPrivateAppFile(relativeName: String): ByteArray? =
    withContext(Dispatchers.IO) {
        val ctx = RaLensDebugAndroidContext.appContext ?: return@withContext null
        val f = File(ctx.filesDir, relativeName)
        if (!f.exists()) return@withContext null
        f.readBytes()
    }
