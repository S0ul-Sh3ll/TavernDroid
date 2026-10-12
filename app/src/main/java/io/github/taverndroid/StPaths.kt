package io.github.taverndroid

import android.content.Context
import java.io.File

/** On-device layout. Everything lives in app-private storage; [data] is the SillyTavern data root. */
class StPaths(context: Context) {
    val root = File(context.filesDir, "st")
    val app = File(root, "app")
    val appVersion = File(root, "app.version")
    val data = File(root, "data")
    val config = File(root, "config.yaml")
    val home = File(root, "home")
    val bin = File(root, "bin")
    val gitCore = File(bin, "git-core")
    val runtime = File(root, "runtime")
    val gitTemplates = File(runtime, "git-templates")
    val logs = File(root, "logs")
    val serverLog = File(logs, "server.log")
    val previousServerLog = File(logs, "server.previous.log")
    val tmp = File(context.cacheDir, "tmp")
    val downloads = File(context.cacheDir, "downloads")
    val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    val node = File(nativeDir, "libnode.so")
    val preload = File(runtime, "android-preload.cjs")
    val certs = File(runtime, "cert.pem")
    val opensslConf = File(runtime, "openssl.cnf")
}
