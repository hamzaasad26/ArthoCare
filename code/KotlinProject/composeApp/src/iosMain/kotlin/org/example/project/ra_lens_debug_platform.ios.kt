package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask

private fun debugDocumentsPath(): String? {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    )
    val dir = paths.firstOrNull() as? String ?: return null
    return "$dir/$RA_LENS_DEBUG_FILENAME"
}

@Suppress("UNCHECKED_CAST")
internal actual suspend fun readRaLensDebugJsonBytes(): ByteArray? =
    withContext(Dispatchers.Default) {
        val path = debugDocumentsPath() ?: return@withContext null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext null
        val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: return@withContext null
        (content as String).encodeToByteArray()
    }

@Suppress("UNCHECKED_CAST")
internal actual suspend fun writeRaLensDebugJsonBytes(bytes: ByteArray) =
    withContext(Dispatchers.Default) {
        val path = debugDocumentsPath()
            ?: throw IllegalStateException("RaLens debug: could not resolve Documents path")
        val ok = (bytes.decodeToString() as NSString).writeToFile(
            path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
        if (!ok) throw IllegalStateException("RaLens debug: write failed for $path")
    }

internal actual fun newRaLensSessionId(): String =
    NSUUID().UUIDString

internal actual fun raLensDebugLog(tag: String, message: String) {
    println("[$tag] $message")
}
