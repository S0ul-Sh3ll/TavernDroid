package io.github.taverndroid

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

/** Base for every screen that shows user content: shows the PIN screen whenever the app is locked. */
abstract class LockedActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val checker = object : Runnable {
        override fun run() {
            if (!AppLock.guard(this@LockedActivity)) handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AppLock.guard(this)) {
            handler.postDelayed(checker, CHECK_MS)
            onUnlockedResume()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(checker)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        AppLock.touch()
    }

    /** Called from onResume when the app is unlocked. */
    protected open fun onUnlockedResume() {}

    private companion object {
        const val CHECK_MS = 5_000L
    }
}
