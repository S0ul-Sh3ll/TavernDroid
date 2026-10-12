package io.github.taverndroid

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native settings for the Android shell (everything SillyTavern itself configures stays in its UI). */
@Suppress("UseSwitchCompatOrMaterialCode")
class SettingsActivity : LockedActivity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var busy: AlertDialog? = null

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runBackup(uri)
    }
    private val pickRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        scroll.addView(list)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        build()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerController.state.collect { updateStatus(it) }
            }
        }
    }

    private fun build() {
        title("App settings")

        section("Server")
        status = text("", 14f, Color.rgb(0xdd, 0xdd, 0xdd)).apply { setTextIsSelectable(true) }
        row(
            button("Restart") { ServerService.restart(this) },
            button("Stop") { ServerService.stop(this) },
            button("Start") { ServerService.start(this) },
        )
        button("Open in another browser") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ServerController.localUrl(this))))
        }.also { list.addView(it) }
        numberSetting("Port", "Default ${Prefs.DEFAULT_PORT}. Pick another if Termux SillyTavern uses the same one.",
            { prefs.port }, { value ->
                if (value !in 1024..65535) { toast("Use a port between 1024 and 65535"); false }
                else { prefs.port = value; ServerService.restart(this); true }
            })
        switch("Allow other devices (LAN)",
            "Listen on your Wi-Fi network. Other devices log in with the username and password below.",
            { prefs.lanAccess }) { prefs.lanAccess = it; ServerService.restart(this) }
        val creds = text("", 13f, Color.rgb(0xaa, 0xaa, 0xaa)).apply { setTextIsSelectable(true) }
        fun showCreds() {
            creds.text = "Username: ${prefs.lanUser}\nPassword: ${prefs.lanPassword}"
        }
        showCreds()
        row(
            button("Copy password") { copy(prefs.lanPassword) },
            button("New password") {
                prefs.lanPassword = Prefs.randomPassword()
                showCreds()
                if (prefs.lanAccess) ServerService.restart(this)
            },
        )

        section("App lock")
        val lockLabel = text("", 14f, Color.rgb(0xdd, 0xdd, 0xdd))
        fun showLock() {
            lockLabel.text = "Auto-lock: ${timeoutLabel(prefs.autoLockSeconds)}"
        }
        showLock()
        row(
            button("Change PIN") { LockActivity.changePin(this) },
            button("Auto-lock…") {
                val choices = AppLock.TIMEOUT_CHOICES
                AlertDialog.Builder(this)
                    .setTitle("Lock the app after")
                    .setSingleChoiceItems(
                        choices.map { timeoutLabel(it) }.toTypedArray(),
                        choices.indexOf(prefs.autoLockSeconds).coerceAtLeast(0),
                    ) { dialog, which ->
                        prefs.autoLockSeconds = choices[which]
                        showLock()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null).show()
            },
            button("Lock now") {
                AppLock.lock()
                AppLock.guard(this)
            },
        )
        hint("Time without touching or typing in the app (time in the background counts too).")
        switch("Show reply text in notifications", "Off: notifications only say who replied, so chats stay behind the PIN.",
            { prefs.notifyReplyText }) { prefs.notifyReplyText = it }

        section("Background activity")
        val bgStatus = text("", 14f, Color.rgb(0xdd, 0xdd, 0xdd))
        fun showBg() {
            bgStatus.text = if (Background.isUnrestricted(this)) "Battery: unrestricted ✓ (replies keep generating in the background)"
            else "Battery: optimized. Some phones (vivo/iQOO, OPPO, Xiaomi…) pause replies while the app is in the background."
        }
        showBg()
        bgRefresh = ::showBg
        row(
            button("Allow unrestricted") { Background.requestUnrestricted(this) },
            button("App system settings") { Background.openAppSettings(this) },
        )
        hint("On vivo/iQOO also check App system settings → Battery → \"Background power consumption\" → Allow.")

        section("Behaviour")
        switch("Keep running in background", "Keep the server alive after the app is swiped away from recents.",
            { prefs.keepRunning }) { prefs.keepRunning = it }
        switch("Start on boot", "Start the server when the phone starts.", { prefs.startOnBoot }) { prefs.startOnBoot = it }
        switch("Keep screen on", "While the chat is open.", { prefs.keepScreenOn }) { prefs.keepScreenOn = it }
        switch("Reply notifications", "Notify when a character replies while the app is in the background.",
            { prefs.notifyReplies }) { prefs.notifyReplies = it }
        switch("Keep CPU awake while generating", "Lets replies finish with the screen off.",
            { prefs.wakeDuringGeneration }) { prefs.wakeDuringGeneration = it }
        numberSetting("Node.js memory limit (MB)", "0 = automatic. Raise if large chats or vector storage crash the server.",
            { prefs.nodeHeapMb }, { value ->
                if (value != 0 && value !in 256..16384) { toast("Use 0 or 256–16384"); false }
                else { prefs.nodeHeapMb = value; ServerService.restart(this); true }
            })
        switch("WebView debugging", "Inspect the page from desktop Chrome at chrome://inspect.",
            { prefs.webDebugging }) { prefs.webDebugging = it }

        section("Data")
        list.addView(button("Back up everything (zip)") {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            AppLock.allowExternalActivity()
            createBackup.launch("SillyTavern-backup-$stamp.zip")
        })
        list.addView(button("Restore / import from zip") {
            AppLock.allowExternalActivity()
            pickRestore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        })
        hint("Restore accepts this app's backups, or a zip of a SillyTavern \"data\" folder from a PC or Termux.")
        list.addView(button("Edit config.yaml") { startActivity(Intent(this, ConfigEditorActivity::class.java)) })
        list.addView(button("Server log") { startActivity(Intent(this, LogActivity::class.java)) })
        list.addView(button("Clear compiled frontend cache") {
            lifecycleScope.launch {
                ServerController.stop()
                withContext(Dispatchers.IO) { File(StPaths(this@SettingsActivity).data, "_webpack").deleteRecursively() }
                ServerService.start(this@SettingsActivity)
            }
        })
        list.addView(button("Repair app files") {
            confirm("Repair app files?", "SillyTavern's program files are unpacked again. Your data is kept.") {
                lifecycleScope.launch {
                    ServerController.stop()
                    Installer(this@SettingsActivity, StPaths(this@SettingsActivity)).invalidate()
                    ServerService.start(this@SettingsActivity)
                    }
            }
        })

        section("About")
        val paths = StPaths(this)
        text(
            "TavernDroid ${BuildConfig.VERSION_NAME}\n" +
                "SillyTavern ${BuildConfig.ST_BUNDLE}\n" +
                "Node.js 24 LTS and git (Termux builds)\n" +
                "Data folder: ${paths.data.absolutePath}",
            13f, Color.rgb(0xaa, 0xaa, 0xaa)
        ).setTextIsSelectable(true)
        hint(getString(R.string.license_notice))
        list.addView(button("Open-source licenses") { startActivity(Intent(this, LicensesActivity::class.java)) })
        list.addView(button("Source code") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_url))))
        })
    }

    private fun timeoutLabel(seconds: Int) = when {
        seconds == 0 -> "Immediately when leaving the app"
        seconds < 60 -> "$seconds seconds of inactivity"
        seconds < 3600 -> "${seconds / 60} minute${if (seconds >= 120) "s" else ""} of inactivity"
        else -> "${seconds / 3600} hour${if (seconds >= 7200) "s" else ""} of inactivity"
    }

    private var bgRefresh: (() -> Unit)? = null

    override fun onUnlockedResume() {
        bgRefresh?.invoke()
    }

    private fun updateStatus(state: ServerController.State) {
        val base = when (state) {
            is ServerController.State.Running -> "Running at ${state.url}"
            is ServerController.State.Preparing -> state.message
            ServerController.State.Starting -> "Starting…"
            is ServerController.State.Failed -> "Stopped with an error: ${state.message}"
            ServerController.State.Stopped -> "Stopped"
        }
        val lan = if (prefs.lanAccess && state is ServerController.State.Running) {
            val urls = Network.lanUrls(prefs.port)
            if (urls.isEmpty()) "\nLAN: no Wi-Fi address found" else "\nLAN: " + urls.joinToString("\n     ")
        } else ""
        status.text = base + lan
    }

    // ------------------------------------------------------------- backups

    private fun runBackup(uri: Uri) {
        showBusy("Backing up…")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BackupManager(this@SettingsActivity).backup(uri) { msg -> runOnUiThread { busy?.setMessage(msg) } }
                }
            }
            hideBusy()
            toast(result.exceptionOrNull()?.let { "Backup failed: ${it.message}" } ?: "Backup saved")
        }
    }

    private fun confirmRestore(uri: Uri) {
        confirm("Restore from this zip?", "The server stops, files from the zip replace the matching files on this phone, then the server starts again.") {
            showBusy("Restoring…")
            lifecycleScope.launch {
                ServerController.stop()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        BackupManager(this@SettingsActivity).restore(uri) { msg -> runOnUiThread { busy?.setMessage(msg) } }
                    }
                }
                hideBusy()
                ServerService.start(this@SettingsActivity)
                result.onSuccess { toast("Restored $it files") }
                    .onFailure { toast("Restore failed: ${it.message}") }
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun showBusy(message: String) {
        busy = AlertDialog.Builder(this).setMessage(message).setCancelable(false).show()
    }

    private fun hideBusy() {
        busy?.dismiss()
        busy = null
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Continue") { _, _ -> action() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("password", text))
        toast("Copied")
    }

    private fun title(t: String) = text(t, 24f, Color.WHITE).apply { setPadding(0, dp(8), 0, dp(8)) }

    private fun section(t: String) = text(t.uppercase(), 13f, ACCENT).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(24), 0, dp(6))
    }

    private fun hint(t: String) = text(t, 12f, Color.rgb(0x99, 0x99, 0x99)).apply { setPadding(0, 0, 0, dp(8)) }

    private fun text(t: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        list.addView(this)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        views.forEach { row.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        list.addView(row)
    }

    private fun switch(label: String, description: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = get()
            setPadding(0, dp(10), 0, 0)
            setOnCheckedChangeListener { _, checked -> set(checked) }
        }
        list.addView(sw)
        hint(description)
    }

    private fun numberSetting(label: String, description: String, get: () -> Int, set: (Int) -> Boolean) {
        text(label, 16f, Color.WHITE).setPadding(0, dp(10), 0, 0)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(get().toString())
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        val apply = button("Apply") {
            val value = input.text.toString().toIntOrNull()
            if (value == null || !set(value)) input.setText(get().toString())
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(apply)
        list.addView(row)
        hint(description)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.rgb(0x17, 0x17, 0x17)
        private val ACCENT = Color.rgb(0xe1, 0x8a, 0x24)
    }
}
