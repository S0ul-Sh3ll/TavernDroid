package io.github.taverndroid

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives files exported by SillyTavern and stores them in Downloads/SillyTavern (MediaStore, API 29+).
 * On older Android, where public Downloads needs a storage permission, the user picks a location instead.
 */
class FileSaver(private val context: Context) {
    data class Pending(val file: File, val name: String, val mime: String)

    private val pending = ConcurrentHashMap<String, Pending>()

    fun begin(name: String, mime: String): String {
        val id = UUID.randomUUID().toString()
        val dir = StPaths(context).downloads.apply { mkdirs() }
        pending[id] = Pending(File(dir, id), sanitize(name), mime.ifBlank { guessMime(name) })
        return id
    }

    fun append(id: String, bytes: ByteArray) {
        val p = pending[id] ?: return
        p.file.appendBytes(bytes)
    }

    fun abort(id: String) {
        pending.remove(id)?.file?.delete()
    }

    /** Takes ownership of a finished temp file. */
    fun take(id: String): Pending? = pending.remove(id)

    fun adopt(file: File, name: String, mime: String) = Pending(file, sanitize(name), mime.ifBlank { guessMime(name) })

    /** Saves to public Downloads (API 29+). Returns a user-visible location, or null if not supported. */
    fun publish(p: Pending): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, p.name)
            put(MediaStore.Downloads.MIME_TYPE, p.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SillyTavern")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)!!.use { out -> p.file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        p.file.delete()
        return uri
    }

    fun writeTo(p: Pending, uri: Uri) {
        context.contentResolver.openOutputStream(uri)!!.use { out -> p.file.inputStream().use { it.copyTo(out) } }
        p.file.delete()
    }

    fun shareIntent(p: Pending): Intent {
        val shared = File(StPaths(context).downloads, "share").apply { mkdirs() }
        val copy = File(shared, p.name)
        p.file.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        return Intent(Intent.ACTION_SEND).setType(p.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifBlank { "download" }.take(200)

        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jsonl" -> "application/jsonl"
                "json" -> "application/json"
                "charx", "byaf" -> "application/zip"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
        }
    }
}
