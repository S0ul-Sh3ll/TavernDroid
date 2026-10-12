package io.github.taverndroid

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Whole-install backups. A backup zip contains:
 *   data/…            the SillyTavern data root (all users, characters, chats, settings, secrets)
 *   config.yaml       server configuration
 *   extensions/…      global third-party extensions (app/public/scripts/extensions/third-party)
 *   plugins/…         server plugins
 * Restore also accepts a plain zip of a desktop/Termux SillyTavern "data" folder
 * (entries starting with "data/" or directly with "<user>/…" such as "default-user/").
 */
class BackupManager(private val context: Context) {
    private val paths = StPaths(context)
    private val extensionsDir get() = File(paths.app, "public/scripts/extensions/third-party")
    private val pluginsDir get() = File(paths.app, "plugins")

    fun backup(target: Uri, progress: (String) -> Unit) {
        context.contentResolver.openOutputStream(target)!!.use { raw ->
            ZipOutputStream(raw.buffered(1 shl 16)).use { zip ->
                addTree(zip, paths.data, "data", progress) { rel ->
                    // Compiled frontend cache is rebuilt automatically.
                    !rel.startsWith("_webpack")
                }
                if (paths.config.exists()) addFile(zip, paths.config, "config.yaml")
                addTree(zip, extensionsDir, "extensions", progress)
                addTree(zip, pluginsDir, "plugins", progress)
            }
        }
    }

    /** Must be called while the server is stopped. */
    fun restore(source: Uri, progress: (String) -> Unit): Int {
        val layout = detectLayout(source)
        var count = 0
        context.contentResolver.openInputStream(source)!!.use { raw ->
            ZipInputStream(raw.buffered(1 shl 16)).use { zip ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = mapEntry(normalize(entry.name), layout) ?: continue
                    val canonicalRoots = listOf(paths.root, paths.app).map { it.canonicalPath + File.separator }
                    require(canonicalRoots.any { target.canonicalPath.startsWith(it) }) { "Bad zip entry ${entry.name}" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                    count++
                    if (count % 50 == 0) progress("Restored $count files…")
                }
            }
        }
        return count
    }

    private enum class Layout { APP_BACKUP, DATA_FOLDER, USER_FOLDERS }

    private fun normalize(name: String) = name.replace('\\', '/').trimStart('/')

    /** Strips one wrapping folder (e.g. "SillyTavern/data/…") by looking at what the zip contains. */
    private fun detectLayout(source: Uri): Pair<Layout, String> {
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(source)!!.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (names.size < 5000) {
                    val e = zip.nextEntry ?: break
                    names += normalize(e.name)
                }
            }
        }
        fun prefixOf(marker: String): String? =
            names.firstNotNullOfOrNull { n ->
                val i = n.indexOf(marker)
                if (i >= 0 && n.substring(0, i).count { it == '/' } <= 1) n.substring(0, i) else null
            }
        prefixOf("config.yaml")?.let { p -> if (names.any { it.startsWith(p + "data/") }) return Layout.APP_BACKUP to p }
        prefixOf("data/default-user/")?.let { return Layout.DATA_FOLDER to it }
        prefixOf("default-user/")?.let { return Layout.USER_FOLDERS to it }
        prefixOf("data/")?.let { return Layout.DATA_FOLDER to it }
        throw IllegalArgumentException("This zip does not look like a SillyTavern backup or data folder.")
    }

    private fun mapEntry(name: String, layout: Pair<Layout, String>): File? {
        val (kind, prefix) = layout
        if (!name.startsWith(prefix)) return null
        val rel = name.removePrefix(prefix)
        if (rel.isEmpty()) return null
        return when (kind) {
            Layout.APP_BACKUP -> when {
                rel == "config.yaml" -> paths.config
                rel.startsWith("data/") -> File(paths.data, rel.removePrefix("data/"))
                rel.startsWith("extensions/") -> File(extensionsDir, rel.removePrefix("extensions/"))
                rel.startsWith("plugins/") -> File(pluginsDir, rel.removePrefix("plugins/"))
                else -> null
            }
            Layout.DATA_FOLDER -> if (rel.startsWith("data/")) File(paths.data, rel.removePrefix("data/")) else null
            Layout.USER_FOLDERS -> File(paths.data, rel)
        }
    }

    private fun addTree(
        zip: ZipOutputStream, dir: File, prefix: String, progress: (String) -> Unit,
        include: (String) -> Boolean = { true },
    ) {
        if (!dir.exists()) return
        var count = 0
        dir.walkTopDown().forEach { file ->
            val rel = file.relativeTo(dir).invariantSeparatorsPath
            if (rel.isEmpty() || !include(rel)) return@forEach
            if (file.isDirectory) {
                zip.putNextEntry(ZipEntry("$prefix/$rel/"))
                zip.closeEntry()
            } else {
                addFile(zip, file, "$prefix/$rel")
                if (++count % 50 == 0) progress("Backed up $count files from $prefix…")
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
