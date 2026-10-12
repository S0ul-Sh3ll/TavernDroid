package io.github.taverndroid

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/**
 * Raw editor for SillyTavern's config.yaml (every server option: user accounts, SSO, proxies,
 * backups, extensions, thumbnails, ...). Port, listen, data root and browser launch are always
 * set by the app on the command line and override the file.
 */
class ConfigEditorActivity : LockedActivity() {
    private lateinit var editor: EditText
    private lateinit var config: File

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val paths = StPaths(this)
        config = paths.config
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(0x10, 0x10, 0x10))
        }
        val note = TextView(this).apply {
            text = "port, listen, dataRoot, whitelist (LAN mode) and browserLaunch are controlled by the app settings."
            textSize = 12f
            setTextColor(Color.rgb(0x99, 0x99, 0x99))
            setPadding(24, 12, 24, 12)
        }
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Color.rgb(0xe0, 0xe0, 0xe0))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(true)
            setText(if (config.exists()) config.readText() else "# config.yaml is created on the first server start\n")
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Save and restart"
            isAllCaps = false
            setOnClickListener { save() }
        })
        buttons.addView(Button(this).apply {
            text = "Reset to default"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@ConfigEditorActivity)
                    .setMessage("Replace config.yaml with SillyTavern's default configuration?")
                    .setPositiveButton("Reset") { _, _ ->
                        val default = File(paths.app, "default/config.yaml")
                        if (default.exists()) editor.setText(default.readText())
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        })
        root.addView(buttons)
        root.addView(note)
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun save() {
        val backup = File(config.parentFile, "config.yaml.bak")
        if (config.exists()) config.copyTo(backup, overwrite = true)
        config.writeText(editor.text.toString())
        Toast.makeText(this, "Saved (previous version kept as config.yaml.bak)", Toast.LENGTH_LONG).show()
        ServerService.restart(this)
        finish()
    }
}
