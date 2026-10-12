package io.github.taverndroid

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Live server console. SillyTavern prints things only here, e.g. account recovery codes
 * and extension install errors, so it has to be reachable on a phone.
 */
class LogActivity : LockedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(0x10, 0x10, 0x10))
        }
        val logText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(0xd0, 0xd0, 0xd0))
            setTextIsSelectable(true)
            setPadding(16, 16, 16, 16)
        }
        val vertical = ScrollView(this)
        vertical.addView(HorizontalScrollView(this).apply { addView(logText) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Share log"
            isAllCaps = false
            setOnClickListener { share() }
        })
        buttons.addView(Button(this).apply {
            text = "Restart server"
            isAllCaps = false
            setOnClickListener { ServerService.restart(this@LogActivity) }
        })
        root.addView(buttons)
        root.addView(vertical, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerController.log.version.collect {
                    val atBottom = !vertical.canScrollVertically(1)
                    logText.text = ServerController.log.all().joinToString("\n")
                    if (atBottom) vertical.post { vertical.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun share() {
        AppLock.allowExternalActivity()
        val paths = StPaths(this)
        val file = paths.serverLog.takeIf { it.exists() } ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share server log"))
    }
}
