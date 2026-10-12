package io.github.taverndroid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that keeps the SillyTavern server alive while the UI is in the background. */
class ServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ServerController.stopAsync()
                Generation.release()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> ServerController.restart(this)
        }
        goForeground(ServerController.state.value)
        ServerController.start(this)
        if (watcher == null) {
            watcher = scope.launch {
                ServerController.state.collect { state ->
                    goForeground(state)
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Prefs(this).keepRunning) {
            ServerController.stopAsync()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        Generation.release()
        super.onDestroy()
    }

    private fun goForeground(state: ServerController.State) {
        val notification = buildNotification(this, state)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val ACTION_STOP = "io.github.taverndroid.STOP"
        const val ACTION_RESTART = "io.github.taverndroid.RESTART"
        const val CHANNEL_SERVER = "server"
        // Channel settings are fixed once created, so a changed default needs a new id. "replies" (1.0.0-1.0.2)
        // was IMPORTANCE_DEFAULT without vibration: a quiet sound, no vibration, no pop-up.
        const val CHANNEL_REPLIES = "replies_alert"
        private val OLD_CHANNELS = listOf("replies")
        const val CHANNEL_FILES = "files"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ServerService::class.java))
        }

        fun restart(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ServerService::class.java).setAction(ACTION_RESTART)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ServerService::class.java).setAction(ACTION_STOP))
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVER, "SillyTavern server", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the local SillyTavern server is running"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REPLIES, "Replies", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A character replied while the app was in the background"
                    enableVibration(true)
                }
            )
            OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_FILES, "Saved files", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Exports and downloads saved to the Downloads folder"
                }
            )
        }

        private fun buildNotification(context: Context, state: ServerController.State): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stop = PendingIntent.getService(
                context, 1, Intent(context, ServerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val prefs = Prefs(context)
            val text = when (state) {
                is ServerController.State.Running ->
                    if (prefs.lanAccess) "Running · LAN ${Network.lanUrls(prefs.port).firstOrNull() ?: state.url}"
                    else "Running on ${state.url}"
                is ServerController.State.Preparing -> state.message
                ServerController.State.Starting -> "Starting…"
                is ServerController.State.Failed -> "Stopped: ${state.message}"
                ServerController.State.Stopped -> "Stopped"
            }
            return NotificationCompat.Builder(context, CHANNEL_SERVER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("SillyTavern")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, "Stop server", stop)
                .build()
        }
    }
}

/** Keeps the CPU awake while a reply is being generated (screen-off generations would otherwise stall). */
object Generation {
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    @SuppressLint("WakelockTimeout")
    fun started(context: Context) {
        if (!Prefs(context).wakeDuringGeneration) return
        val lock = wakeLock ?: (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TavernDroid:generation")
            .also { it.setReferenceCounted(false); wakeLock = it }
        // Safety timeout in case the page never reports the end of the generation.
        lock.acquire(20 * 60 * 1000L)
    }

    @Synchronized
    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
