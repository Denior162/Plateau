package com.zghonnyk.plateau.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
class PhotoStorage(private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun copyFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
        input.use { src -> File(dir, name).outputStream().use { src.copyTo(it) } }
        "photos/$name"
    }

    fun file(relativePath: String) = File(context.filesDir, relativePath)

    fun delete(relativePath: String) { file(relativePath).delete() }
}

suspend fun copyPhotoToInternal(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
        input.use { src -> File(dir, name).outputStream().use { src.copyTo(it) } }
        "photos/$name"
    }