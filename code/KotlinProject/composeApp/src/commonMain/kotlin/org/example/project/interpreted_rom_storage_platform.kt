package org.example.project

internal expect suspend fun writePrivateAppFile(relativeName: String, bytes: ByteArray)

internal expect suspend fun readPrivateAppFile(relativeName: String): ByteArray?
