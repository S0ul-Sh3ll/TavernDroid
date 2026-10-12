package io.github.taverndroid

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 6-digit PIN app lock.
 *
 * - No PIN yet → the lock screen asks to create one and nothing else is reachable.
 * - The app is unlocked only in memory, so a new process always starts locked.
 * - It locks again after [Prefs.autoLockSeconds] without user interaction (touches, keys, typing in the page;
 *   time spent in the background counts as inactivity). 0 = lock as soon as the app leaves the screen.
 * - The PIN is stored as a salted PBKDF2 hash; wrong attempts are rate-limited, persistently.
 */
object AppLock {
    const val PIN_LENGTH = 6
    private const val ITERATIONS = 120_000
    private const val FREE_ATTEMPTS = 5

    /** Auto-lock choices offered in settings (seconds). */
    val TIMEOUT_CHOICES = listOf(0, 30, 60, 300, 900, 1800, 3600)

    @Volatile private var unlocked = false
    @Volatile private var lastInteraction = SystemClock.elapsedRealtime()
    @Volatile private var externalUntil = 0L
    private var startedActivities = 0

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                // Back from a picker/save dialog we launched: the exemption is used up.
                if (++startedActivities == 1) externalUntil = 0
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0 && Prefs(activity).autoLockSeconds == 0 &&
                    SystemClock.elapsedRealtime() > externalUntil
                ) {
                    lock()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Keep chats out of the recent-apps thumbnail (without blocking screenshots).
                if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun touch() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    /** Call before launching a picker/save dialog so returning from it does not count as leaving the app. */
    fun allowExternalActivity() {
        externalUntil = SystemClock.elapsedRealtime() + 10 * 60 * 1000L
    }

    fun lock() {
        unlocked = false
    }

    fun hasPin(context: Context) = store(context).contains(KEY_HASH)

    /** True when the user must see the lock screen now (also applies the inactivity timeout). */
    fun isLocked(context: Context): Boolean {
        if (!hasPin(context)) return true
        if (!unlocked) return true
        val timeout = Prefs(context).autoLockSeconds
        if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction > timeout * 1000L) {
            unlocked = false
        }
        return !unlocked
    }

    /** Starts the lock screen if needed. Returns true when it did. */
    fun guard(activity: Activity): Boolean {
        if (!isLocked(activity)) return false
        activity.startActivity(
            Intent(activity, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        return true
    }

    // --------------------------------------------------------------- PIN store

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeft: Int) : Result
        data class Blocked(val millisLeft: Long) : Result
    }

    fun setPin(context: Context, pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = algorithm()
        store(context).edit {
            putString(KEY_SALT, b64(salt))
            putString(KEY_ALGO, algorithm)
            putString(KEY_HASH, b64(hash(pin, salt, algorithm)))
            putInt(KEY_FAILS, 0)
            putLong(KEY_BLOCKED_UNTIL, 0)
        }
        unlocked = true
        touch()
    }

    /** Remaining lockout after too many wrong PINs, in ms (0 = may try). */
    fun blockedFor(context: Context): Long =
        (store(context).getLong(KEY_BLOCKED_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    /** Checks a PIN; on success unlocks the app (when [unlock]) and resets the failure counter. */
    fun verify(context: Context, pin: String, unlock: Boolean = true): Result {
        val sp = store(context)
        blockedFor(context).takeIf { it > 0 }?.let { return Result.Blocked(it) }
        val salt = unb64(sp.getString(KEY_SALT, null) ?: return Result.Wrong(0))
        val expected = unb64(sp.getString(KEY_HASH, null) ?: return Result.Wrong(0))
        val algorithm = sp.getString(KEY_ALGO, null) ?: algorithm()
        if (MessageDigest.isEqual(expected, hash(pin, salt, algorithm))) {
            sp.edit { putInt(KEY_FAILS, 0); putLong(KEY_BLOCKED_UNTIL, 0) }
            if (unlock) {
                unlocked = true
                touch()
            }
            return Result.Ok
        }
        val fails = sp.getInt(KEY_FAILS, 0) + 1
        // 5 free attempts, then 30 s, 1 min, 2 min, … up to 30 min between attempts.
        val delay = if (fails < FREE_ATTEMPTS) 0L
        else (30_000L shl (fails - FREE_ATTEMPTS).coerceAtMost(6)).coerceAtMost(30 * 60_000L)
        sp.edit {
            putInt(KEY_FAILS, fails)
            putLong(KEY_BLOCKED_UNTIL, if (delay > 0) System.currentTimeMillis() + delay else 0)
        }
        return if (delay > 0) Result.Blocked(delay) else Result.Wrong(FREE_ATTEMPTS - fails)
    }

    private fun algorithm() = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    private fun hash(pin: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun store(context: Context) =
        context.applicationContext.getSharedPreferences("applock", Context.MODE_PRIVATE)

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_ALGO = "algo"
    private const val KEY_FAILS = "fails"
    private const val KEY_BLOCKED_UNTIL = "blockedUntil"
}
