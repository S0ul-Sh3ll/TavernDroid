package io.github.taverndroid

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.view.animation.CycleInterpolator
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PIN pad for the app lock. Modes:
 *  - create: no PIN yet (first launch); enter + confirm, cannot be skipped.
 *  - unlock: enter the PIN; Back leaves the app instead of revealing it.
 *  - change: current PIN, new PIN, confirm (opened from settings; Back cancels).
 */
class LockActivity : ComponentActivity() {
    private enum class Step { CURRENT, NEW, CONFIRM, UNLOCK }

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var error: TextView
    private lateinit var dotsRow: LinearLayout
    private val dots = mutableListOf<View>()
    private val keys = mutableListOf<View>()

    private var changing = false
    private var step = Step.UNLOCK
    private var entered = StringBuilder()
    private var firstEntry = ""
    private var busy = false
    private var countdown: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        changing = intent.getBooleanExtra(EXTRA_CHANGE, false)
        buildViews()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Never reveal the app behind the lock; changing the PIN can be cancelled.
                if (changing) finish() else moveTaskToBack(true)
            }
        })
        reset()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Unlocked elsewhere (e.g. this screen was left in the back stack): nothing to do here.
        if (!changing && !AppLock.isLocked(this)) finish()
        showBlockedCountdown()
    }

    private fun reset() {
        step = when {
            changing -> Step.CURRENT
            !AppLock.hasPin(this) -> Step.NEW
            else -> Step.UNLOCK
        }
        firstEntry = ""
        clearEntry()
        render()
    }

    private fun render() {
        val creating = !changing && step != Step.UNLOCK
        title.text = when (step) {
            Step.UNLOCK -> "Enter your PIN"
            Step.CURRENT -> "Enter your current PIN"
            Step.NEW -> if (changing) "Choose a new PIN" else "Create a PIN"
            Step.CONFIRM -> "Confirm your PIN"
        }
        subtitle.text = when {
            creating && step == Step.NEW ->
                "TavernDroid is protected by a 6-digit PIN. You need it every time the app locks.\n" +
                    "There is no way to recover a forgotten PIN without erasing the app's data."
            creating -> "Enter the same 6 digits again."
            step == Step.UNLOCK -> "Forgot it? Android Settings → Apps → TavernDroid → Clear storage (erases all data)."
            else -> ""
        }
    }

    // --------------------------------------------------------------- input

    private fun press(digit: Char) {
        if (busy || entered.length >= AppLock.PIN_LENGTH || AppLock.blockedFor(this) > 0 && step.needsVerify()) return
        error.text = ""
        entered.append(digit)
        updateDots()
        if (entered.length == AppLock.PIN_LENGTH) submit(entered.toString())
    }

    private fun backspace() {
        if (busy || entered.isEmpty()) return
        entered.deleteCharAt(entered.length - 1)
        updateDots()
    }

    private fun Step.needsVerify() = this == Step.UNLOCK || this == Step.CURRENT

    private fun submit(pin: String) {
        when (step) {
            Step.UNLOCK, Step.CURRENT -> verify(pin)
            Step.NEW -> {
                firstEntry = pin
                step = Step.CONFIRM
                clearEntry()
                render()
            }
            Step.CONFIRM -> {
                if (pin != firstEntry) {
                    fail("The PINs did not match. Try again.")
                    step = Step.NEW
                    firstEntry = ""
                    render()
                    return
                }
                busy = true
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) { AppLock.setPin(this@LockActivity, pin) }
                    busy = false
                    if (changing) toast("PIN changed")
                    finishUnlocked()
                }
            }
        }
    }

    private fun verify(pin: String) {
        busy = true
        lifecycleScope.launch {
            // Hashing takes a moment on older phones; keep it off the UI thread.
            val result = withContext(Dispatchers.Default) {
                AppLock.verify(this@LockActivity, pin, unlock = step == Step.UNLOCK)
            }
            busy = false
            when (result) {
                AppLock.Result.Ok -> if (step == Step.CURRENT) {
                    step = Step.NEW
                    clearEntry()
                    render()
                } else {
                    finishUnlocked()
                }
                is AppLock.Result.Wrong -> fail(
                    if (result.attemptsLeft > 0) "Wrong PIN. ${result.attemptsLeft} attempt${if (result.attemptsLeft == 1) "" else "s"} left before a wait."
                    else "Wrong PIN."
                )
                is AppLock.Result.Blocked -> {
                    fail("")
                    showBlockedCountdown()
                }
            }
        }
    }

    private fun showBlockedCountdown() {
        countdown?.cancel()
        if (!step.needsVerify()) return
        countdown = lifecycleScope.launch {
            while (true) {
                val left = AppLock.blockedFor(this@LockActivity)
                if (left <= 0) {
                    error.text = ""
                    setKeysEnabled(true)
                    break
                }
                setKeysEnabled(false)
                val s = (left + 999) / 1000
                error.text = "Too many wrong PINs. Try again in ${if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"}."
                delay(1000)
            }
        }
    }

    private fun finishUnlocked() {
        AppLock.touch()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun fail(message: String) {
        error.text = message
        dotsRow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dotsRow.startAnimation(TranslateAnimation(0f, dp(12).toFloat(), 0f, 0f).apply {
            duration = 400
            interpolator = CycleInterpolator(3f)
        })
        clearEntry()
    }

    private fun clearEntry() {
        entered = StringBuilder()
        updateDots()
    }

    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            (dot.background as GradientDrawable).setColor(if (i < entered.length) ACCENT else Color.TRANSPARENT)
        }
    }

    private fun setKeysEnabled(enabled: Boolean) {
        keys.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else 0.35f }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val c = event.unicodeChar.toChar()
        return when {
            c in '0'..'9' -> { press(c); true }
            keyCode == KeyEvent.KEYCODE_DEL -> { backspace(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // --------------------------------------------------------------- views

    @SuppressLint("SetTextI18n")
    private fun buildViews() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(BG)
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        val icon = TextView(this).apply {
            text = "🔒"
            textSize = 34f
            gravity = Gravity.CENTER
        }
        title = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        subtitle = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(0x9a, 0x9a, 0x9a))
            gravity = Gravity.CENTER
        }
        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(12))
        }
        repeat(AppLock.PIN_LENGTH) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2), ACCENT)
                }
            }
            dots += dot
            dotsRow.addView(dot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(dp(9), 0, dp(9), 0) })
        }
        error = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(0xff, 0x7a, 0x6e))
            gravity = Gravity.CENTER
            minHeight = dp(40)
        }

        val grid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
        }
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        for (label in labels) {
            val key = TextView(this).apply {
                text = label
                textSize = if (label == "⌫") 22f else 26f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                if (label.isNotEmpty()) {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(KEY_BG)
                    }
                    isClickable = true
                    isFocusable = true
                    contentDescription = if (label == "⌫") "Delete" else label
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (label == "⌫") backspace() else press(label[0])
                    }
                    if (label == "⌫") setOnLongClickListener { clearEntry(); true }
                    keys += this
                }
            }
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = dp(76)
                height = dp(76)
                setMargins(dp(12), dp(8), dp(12), dp(8))
            })
        }

        root.addView(icon)
        root.addView(title)
        root.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(dotsRow)
        root.addView(error, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
        if (changing) {
            root.addView(TextView(this).apply {
                text = "Cancel"
                textSize = 15f
                setTextColor(ACCENT)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { finish() }
            })
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left + dp(24), bars.top + dp(32), bars.right + dp(24), bars.bottom + dp(16))
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_CHANGE = "change"
        private val BG = Color.rgb(0x17, 0x17, 0x17)
        private val KEY_BG = Color.rgb(0x2a, 0x2a, 0x2a)
        private val ACCENT = Color.rgb(0xe1, 0x8a, 0x24)

        fun changePin(context: Context) =
            context.startActivity(Intent(context, LockActivity::class.java).putExtra(EXTRA_CHANGE, true))
    }
}
