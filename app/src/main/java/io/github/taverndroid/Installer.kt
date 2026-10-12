package io.github.taverndroid

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Unpacks the bundled SillyTavern (assets/st-bundle.zip) into app storage when the bundled
 * version differs from the installed one, and refreshes the runtime files and executable links
 * on every start (nativeLibraryDir changes whenever the APK is updated).
 */
class Installer(private val context: Context, private val paths: StPaths) {

    fun interface Progress {
        fun update(message: String, fraction: Float?)
    }

    fun bundledVersion(): String =
        context.assets.open("st-bundle.version").use { it.readBytes().decodeToString().trim() }

    fun installedVersion(): String? =
        paths.appVersion.takeIf { it.exists() }?.readText()?.trim()

    fun ensureInstalled(progress: Progress) {
        listOf(paths.root, paths.data, paths.home, paths.logs, paths.tmp, paths.downloads).forEach { it.mkdirs() }
        val bundled = bundledVersion()
        if (installedVersion() != bundled || !File(paths.app, "server.js").exists()) {
            extractBundle(bundled, progress)
        }
        progress.update("Preparing runtime…", null)
        installRuntime()
        seedCache()
        linkExecutables()
    }

    /** Forces a fresh extraction on next start (used by "Repair app files"). */
    fun invalidate() {
        paths.appVersion.delete()
    }

    private fun extractBundle(version: String, progress: Progress) {
        val staging = File(paths.root, "app.new")
        staging.deleteRecursively()
        staging.mkdirs()

        val total = context.assets.openFd("st-bundle.zip").use { it.length }.coerceAtLeast(1)
        var lastReport = -1
        context.assets.open("st-bundle.zip").use { raw ->
            val counting = CountingInputStream(raw)
            ZipInputStream(counting.buffered(1 shl 16)).use { zip ->
                val buffer = ByteArray(1 shl 16)
                val stagingPath = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(staging, entry.name)
                    require(target.canonicalPath.startsWith(stagingPath)) { "Bad zip entry ${entry.name}" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    val percent = (counting.count * 100 / total).toInt()
                    if (percent != lastReport) {
                        lastReport = percent
                        progress.update("Unpacking SillyTavern $version… $percent%", percent / 100f)
                    }
                }
            }
        }

        // Keep things users installed into the app folder itself across upgrades:
        // global third-party extensions and server plugins.
        progress.update("Keeping installed extensions and plugins…", null)
        if (paths.app.exists()) {
            for (rel in listOf("public/scripts/extensions/third-party", "plugins", "backups")) {
                val old = File(paths.app, rel)
                val new = File(staging, rel)
                old.listFiles()?.forEach { child ->
                    val dest = File(new, child.name)
                    if (!dest.exists()) {
                        new.mkdirs()
                        if (!child.renameTo(dest)) child.copyRecursively(dest, overwrite = false)
                    }
                }
            }
        }

        val old = File(paths.root, "app.old")
        old.deleteRecursively()
        if (paths.app.exists() && !paths.app.renameTo(old)) paths.app.deleteRecursively()
        check(staging.renameTo(paths.app)) { "Could not move SillyTavern into place" }
        old.deleteRecursively()
        paths.appVersion.writeText(version)
    }

    private fun installRuntime() {
        paths.runtime.mkdirs()
        paths.gitTemplates.mkdirs()
        for (name in context.assets.list("runtime").orEmpty()) {
            context.assets.open("runtime/$name").use { input ->
                File(paths.runtime, name).outputStream().use { input.copyTo(it) }
            }
        }
    }

    /** Tokenizer files SillyTavern would download on first use (see tools/prepare.sh). */
    private fun seedCache() {
        val cache = File(paths.data, "_cache").apply { mkdirs() }
        for (name in context.assets.list("seed-cache").orEmpty()) {
            val target = File(cache, name)
            val unpacked = File(cache, name.removeSuffix(".gz"))
            if (target.exists() || unpacked.exists()) continue
            context.assets.open("seed-cache/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun linkExecutables() {
        paths.bin.deleteRecursively()
        paths.gitCore.mkdirs()
        val native = paths.nativeDir
        fun link(target: String, link: File) {
            Os.symlink(File(native, target).absolutePath, link.absolutePath)
        }
        link("libnode.so", File(paths.bin, "node"))
        link("libgit.so", File(paths.bin, "git"))
        link("libgit.so", File(paths.gitCore, "git"))
        for (name in listOf("git-remote-http", "git-remote-https", "git-remote-ftp", "git-remote-ftps")) {
            link("libgitremotehttp.so", File(paths.gitCore, name))
        }
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
