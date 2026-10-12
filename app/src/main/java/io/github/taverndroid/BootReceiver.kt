package io.github.taverndroid

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !Prefs(context).startOnBoot) return
        try {
            ServerService.start(context)
        } catch (e: Exception) {
            // Some Android versions/OEMs refuse foreground-service starts from the background even at boot
            // (ForegroundServiceStartNotAllowedException). Offer a one-tap start instead of crashing.
            Log.w("TavernDroid", "start on boot refused: ${e.message}")
            offerStart(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun offerStart(context: Context) {
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, ServerService.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SillyTavern")
            .setContentText("Android did not allow starting at boot. Tap to start the server.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(4, n) }
    }
}
