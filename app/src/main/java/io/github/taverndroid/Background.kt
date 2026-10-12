package io.github.taverndroid

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Keeping generations alive while the app is in the background.
 *
 * Stock Android leaves a foreground-service app alone, but vendor power managers (vivo/iQOO, OPPO/Realme,
 * Xiaomi, Huawei, …) freeze or throttle apps that are not exempt from battery optimisation, pausing the
 * page and the server until the user comes back. The exemption ("Unrestricted" battery use) is what
 * those systems honour, so the app asks for it once and offers it again in settings.
 */
object Background {
    fun isUnrestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** System dialog asking to exempt the app from battery optimisation. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(activity: Activity) {
        if (Build.VERSION.SDK_INT < 23 || isUnrestricted(activity)) return
        AppLock.allowExternalActivity()
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
        try {
            activity.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            openAppSettings(activity)
        }
    }

    /** The app's system page, where vendors put their own background switches (vivo: "Background power consumption"). */
    fun openAppSettings(activity: Activity) {
        AppLock.allowExternalActivity()
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
        )
    }

    /** Asked once, the first time a reply is generated, if the app is still battery-optimised. */
    fun maybeAskOnce(activity: Activity) {
        val prefs = Prefs(activity)
        if (prefs.askedBackground || isUnrestricted(activity) || activity.isFinishing) return
        prefs.askedBackground = true
        AlertDialog.Builder(activity)
            .setTitle("Keep replies generating in the background?")
            .setMessage(
                "Some phones (vivo/iQOO, OPPO, Xiaomi and others) pause apps in the background, which pauses " +
                    "a reply until you come back. Allowing unrestricted battery use for TavernDroid prevents that. " +
                    "TavernDroid only keeps the CPU awake while a reply is being generated.\n\n" +
                    "You can change this later in App settings → Background activity."
            )
            .setPositiveButton("Allow") { _, _ -> requestUnrestricted(activity) }
            .setNegativeButton("Not now", null)
            .show()
    }
}
