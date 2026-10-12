package io.github.taverndroid

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

/** App-level (native) settings. SillyTavern's own settings stay in its data folder and config.yaml. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("taverndroid", Context.MODE_PRIVATE)

    var port: Int
        get() = sp.getInt("port", DEFAULT_PORT)
        set(value) = sp.edit { putInt("port", value) }

    /** Listen on all interfaces so other devices on the network can connect (with basic auth). */
    var lanAccess: Boolean
        get() = sp.getBoolean("lan", false)
        set(value) = sp.edit { putBoolean("lan", value) }

    var lanUser: String
        get() = sp.getString("lanUser", null) ?: "tavern"
        set(value) = sp.edit { putString("lanUser", value) }

    var lanPassword: String
        get() = sp.getString("lanPassword", null) ?: randomPassword().also { lanPassword = it }
        set(value) = sp.edit { putString("lanPassword", value) }

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keepScreenOn", false)
        set(value) = sp.edit { putBoolean("keepScreenOn", value) }

    var startOnBoot: Boolean
        get() = sp.getBoolean("startOnBoot", false)
        set(value) = sp.edit { putBoolean("startOnBoot", value) }

    /** Keep the server running after the app is swiped away from recents. */
    var keepRunning: Boolean
        get() = sp.getBoolean("keepRunning", true)
        set(value) = sp.edit { putBoolean("keepRunning", value) }

    var notifyReplies: Boolean
        get() = sp.getBoolean("notifyReplies", true)
        set(value) = sp.edit { putBoolean("notifyReplies", value) }

    /** Hold a CPU wake lock while a reply is generating so screen-off generations finish. */
    var wakeDuringGeneration: Boolean
        get() = sp.getBoolean("wakeDuringGeneration", true)
        set(value) = sp.edit { putBoolean("wakeDuringGeneration", value) }

    var webDebugging: Boolean
        get() = sp.getBoolean("webDebugging", false)
        set(value) = sp.edit { putBoolean("webDebugging", value) }

    /** Extra V8 heap in MB (0 = Node default). */
    var nodeHeapMb: Int
        get() = sp.getInt("nodeHeapMb", 0)
        set(value) = sp.edit { putInt("nodeHeapMb", value) }

    /** Lock after this many seconds without interaction; 0 = as soon as the app leaves the screen. */
    var autoLockSeconds: Int
        get() = sp.getInt("autoLockSeconds", 300)
        set(value) = sp.edit { putInt("autoLockSeconds", value) }

    /** Show the reply text in notifications (off: only "<name> replied", so chats stay behind the PIN). */
    var notifyReplyText: Boolean
        get() = sp.getBoolean("notifyReplyText", false)
        set(value) = sp.edit { putBoolean("notifyReplyText", value) }

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("askedNotif", false)
        set(value) = sp.edit { putBoolean("askedNotif", value) }

    companion object {
        const val DEFAULT_PORT = 8800

        fun randomPassword(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
            val random = SecureRandom()
            return (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}
