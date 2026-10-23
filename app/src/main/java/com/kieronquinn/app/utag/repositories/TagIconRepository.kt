package com.kieronquinn.app.utag.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kieronquinn.app.utag.model.TagIconFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One PNG per tag, stored under the app's files directory. Map markers and
 * widgets fall back to Samsung's icon when this file is missing.
 */
class TagIconRepository(context: Context) {

    private val directory = File(context.filesDir, "tag-icons").apply { mkdirs() }
    private val resolver = context.contentResolver

    fun fileFor(deviceId: String): File = File(directory, TagIconFiles.fileName(deviceId))

    fun hasCustom(deviceId: String): Boolean = fileFor(deviceId).isFile

    fun lastModified(deviceId: String): Long {
        val file = fileFor(deviceId)
        return if (file.isFile) file.lastModified() else 0L
    }

    fun loadBitmap(deviceId: String, size: Int): Bitmap? {
        val file = fileFor(deviceId)
        if (!file.isFile) return null
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        if (decoded.width == size && decoded.height == size) return decoded
        return Bitmap.createScaledBitmap(decoded, size, size, true).also {
            if (it != decoded) decoded.recycle()
        }
    }

    suspend fun save(deviceId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext false
            val scaled = if (decoded.width == ICON_SIZE && decoded.height == ICON_SIZE) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, ICON_SIZE, ICON_SIZE, true).also {
                    if (it != decoded) decoded.recycle()
                }
            }
            fileFor(deviceId).outputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return@withContext false
                }
            }
            scaled.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clear(deviceId: String) {
        fileFor(deviceId).delete()
    }

    private companion object {
        private const val ICON_SIZE = 131
    }

}
