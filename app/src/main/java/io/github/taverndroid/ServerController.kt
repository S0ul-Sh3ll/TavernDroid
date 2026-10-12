package io.github.taverndroid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL

/** Owns the SillyTavern Node.js process. One instance per app process. */
object ServerController {
    sealed interface State {
        data object Stopped : State
        data class Preparing(val message: String, val progress: Float?) : State
        data object Starting : State
        /** [run] changes on every server start, so pages from an earlier run (stale CSRF token) get reloaded. */
        data class Running(val url: String, val run: Int) : State
        data class Failed(val message: String) : State
    }

    private const val TAG = "TavernDroid"
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> Log.e(TAG, "background task failed", t) }
    )
    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    val log = LogBuffer(4000)

    @Volatile private var process: Process? = null
    @Volatile private var stopRequested = false
    private var runCounter = 0
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    fun localUrl(context: Context) = "http://127.0.0.1:${Prefs(context).port}/"

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        stopRequested = false
        job = scope.launch { runServer(app) }
    }

    /** Sends SIGTERM (SillyTavern shuts down cleanly), then kills it if it lingers. */
    suspend fun stop() {
        stopRequested = true
        val p = process
        if (p != null) {
            withContext(Dispatchers.IO) {
                p.destroy()
                // Process.waitFor(timeout) needs API 26; poll instead.
                val deadline = System.currentTimeMillis() + 5000
                while (p.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(100)
                if (p.isRunning()) p.destroyForcibly()
            }
        }
        job?.join()
        _state.value = State.Stopped
    }

    fun stopAsync() {
        scope.launch { stop() }
    }

    fun restart(context: Context) {
        val app = context.applicationContext
        scope.launch {
            stop()
            start(app)
        }
    }

    private suspend fun runServer(context: Context) {
        val paths = StPaths(context)
        val prefs = Prefs(context)
        try {
            _state.value = State.Preparing("Checking SillyTavern files…", null)
            Installer(context, paths).ensureInstalled { message, fraction ->
                _state.value = State.Preparing(message, fraction)
            }

            val port = prefs.port
            val lan = prefs.lanAccess
            if (!portAvailable(port, lan)) {
                _state.value = State.Failed(
                    "Port $port is already in use by another app (for example SillyTavern in Termux). " +
                        "Stop it or choose another port in the app settings."
                )
                return
            }

            val crashes = ArrayDeque<Long>()
            var autoRestart = false
            while (true) {
                _state.value = State.Starting
                rotateLog(paths)
                if (!autoRestart) log.clear()   // keep the crash output visible after an automatic restart
                val startedAt = System.currentTimeMillis()
                val p = buildProcess(paths, prefs, port, lan).start()
                process = p
                val pumping = scope.launch { pumpOutput(p, paths.serverLog) }

                val url = "http://127.0.0.1:$port/"
                while (p.isRunning()) {
                    if (probe(url)) {
                        _state.value = State.Running(url, ++runCounter)
                        break
                    }
                    delay(400)
                }
                val code = withContext(Dispatchers.IO) { p.waitFor() }
                pumping.join()
                process = null
                if (stopRequested) {
                    _state.value = State.Stopped
                    return
                }

                // Unexpected exit. SIGKILL (137) is almost always Android's out-of-memory killer, e.g. when
                // large local models (captioning, embeddings) don't fit next to everything else. Restart a
                // few times so the user is not left on an error screen; give up if it keeps crashing.
                val now = System.currentTimeMillis()
                crashes.addLast(now)
                while (crashes.isNotEmpty() && now - crashes.first() > 10 * 60_000) crashes.removeFirst()
                val reason = if (code == 137) "Android stopped it, most likely because the phone ran out of memory"
                else "exit code $code"
                if (now - startedAt >= 20_000 && crashes.size <= 3) {
                    log.add("[TavernDroid] SillyTavern stopped ($reason). Restarting…")
                    _state.value = State.Preparing("SillyTavern stopped ($reason). Restarting…", null)
                    delay(1500)
                    if (stopRequested) {
                        _state.value = State.Stopped
                        return
                    }
                    autoRestart = true
                    continue
                }
                _state.value = State.Failed("SillyTavern stopped unexpectedly ($reason). See the server log for details.")
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "server failed", t)
            log.add("[TavernDroid] ${t.javaClass.simpleName}: ${t.message}")
            process?.destroy()
            process = null
            _state.value = State.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun buildProcess(paths: StPaths, prefs: Prefs, port: Int, lan: Boolean): ProcessBuilder {
        val command = mutableListOf(paths.node.absolutePath)
        if (prefs.nodeHeapMb > 0) command += "--max-old-space-size=${prefs.nodeHeapMb}"
        command += listOf(
            "--require", paths.preload.absolutePath,
            "server.js",
            "--configPath", paths.config.absolutePath,
            "--dataRoot", paths.data.absolutePath,
            "--port", port.toString(),
            "--listen", lan.toString(),
            "--browserLaunchEnabled", "false",
            "--heartbeatInterval", "0",
        )
        if (lan) {
            // Other devices must authenticate; the in-app WebView answers the challenge itself.
            command += listOf("--whitelist", "false", "--basicAuthMode", "true")
        }

        val pb = ProcessBuilder(command).directory(paths.app).redirectErrorStream(true)
        val env = pb.environment()
        env["HOME"] = paths.home.absolutePath
        env["TMPDIR"] = paths.tmp.absolutePath
        env["LD_LIBRARY_PATH"] = paths.nativeDir.absolutePath
        env["PATH"] = "${paths.bin.absolutePath}:/system/bin:/system/xbin"
        env["OPENSSL_CONF"] = paths.opensslConf.absolutePath
        env["SSL_CERT_FILE"] = paths.certs.absolutePath
        env["CURL_CA_BUNDLE"] = paths.certs.absolutePath
        env["GIT_SSL_CAINFO"] = paths.certs.absolutePath
        env["GIT_EXEC_PATH"] = paths.gitCore.absolutePath
        env["GIT_TEMPLATE_DIR"] = paths.gitTemplates.absolutePath
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["NODE_ENV"] = "production"
        env["FORCE_COLOR"] = "0"
        env["TAVERNDROID"] = "1"
        if (lan) {
            env["SILLYTAVERN_BASICAUTHUSER_USERNAME"] = prefs.lanUser
            env["SILLYTAVERN_BASICAUTHUSER_PASSWORD"] = prefs.lanPassword
        }
        return pb
    }

    private fun pumpOutput(p: Process, logFile: File) {
        logFile.parentFile?.mkdirs()
        logFile.bufferedWriter().use { writer ->
            try {
                p.inputStream.bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val line = stripAnsi(raw)
                        log.add(line)
                        writer.write(line)
                        writer.newLine()
                        writer.flush()
                    }
                }
            } catch (_: IOException) {
                // Android closes the pipe when the process is destroyed; that just ends the log.
            }
        }
    }

    private fun rotateLog(paths: StPaths) {
        if (paths.serverLog.exists()) {
            paths.previousServerLog.delete()
            paths.serverLog.renameTo(paths.previousServerLog)
        }
    }

    private fun portAvailable(port: Int, lan: Boolean): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            val address = if (lan) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
            socket.bind(InetSocketAddress(address, port))
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Any HTTP response (including 401 in LAN mode) means the server is up. */
    private fun probe(url: String): Boolean = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1000
        connection.readTimeout = 2000
        connection.requestMethod = "HEAD"
        connection.useCaches = false
        connection.setRequestProperty("Connection", "close")
        val code = connection.responseCode
        connection.disconnect()
        code > 0
    } catch (_: Exception) {
        false
    }

    private fun Process.isRunning(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    // CSI sequences (colors), OSC sequences such as the terminal title (ended by BEL or ESC \), other escapes.
    private val ansi = Regex("\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-Z\\\\-_])")
    private fun stripAnsi(s: String) = ansi.replace(s, "")
}

/** Thread-safe ring buffer of recent server output, observable through [version]. */
class LogBuffer(private val capacity: Int) {
    private val lines = ArrayDeque<String>()
    val version = MutableStateFlow(0L)

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
        version.value++
    }

    @Synchronized
    fun clear() {
        lines.clear()
        version.value++
    }

    @Synchronized
    fun tail(n: Int): List<String> = lines.toList().takeLast(n)

    @Synchronized
    fun all(): List<String> = lines.toList()
}
