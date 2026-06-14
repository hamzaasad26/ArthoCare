package org.example.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask

@Suppress("UNCHECKED_CAST")
private fun pathForPrivateFile(relativeName: String): String? {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    )
    val dir = paths.firstOrNull() as? String ?: return null
    return "$dir/$relativeName"
}

@Suppress("UNCHECKED_CAST")
internal actual suspend fun readPrivateAppFile(relativeName: String): ByteArray? =
    withContext(Dispatchers.Default) {
        val path = pathForPrivateFile(relativeName) ?: return@withContext null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext null
        val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: return@withContext null
        (content as String).encodeToByteArray()
    }

@Suppress("UNCHECKED_CAST")
internal actual suspend fun writePrivateAppFile(relativeName: String, bytes: ByteArray) =
    withContext(Dispatchers.Default) {
        val path = pathForPrivateFile(relativeName)
            ?: throw IllegalStateException("Could not resolve Documents path")
        val ok = (bytes.decodeToString() as NSString).writeToFile(
            path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
        if (!ok) throw IllegalStateException("Failed writing $relativeName")
    }
