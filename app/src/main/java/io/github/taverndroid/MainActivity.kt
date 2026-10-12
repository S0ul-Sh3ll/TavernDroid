package io.github.taverndroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Full-screen WebView hosting the SillyTavern UI served by the bundled Node.js server. */
class MainActivity : LockedActivity() {
    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var overlayTitle: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var overlayProgress: ProgressBar
    private lateinit var overlayLog: TextView
    private lateinit var overlayButtons: LinearLayout
    private lateinit var fileSaver: FileSaver

    private var loadedUrl: String? = null
    private var loadedRun = -1
    private var pageShown = false
    private var resumed = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var pendingLegacySave: FileSaver.Pending? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var nativeTts: NativeTts? = null
    private val bridgeScript by lazy { assets.open("taverndroid.js").use { it.readBytes().decodeToString() } }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        val data = result.data
        val uris = when {
            result.resultCode != Activity.RESULT_OK || data == null -> null
            data.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val request = pendingPermission ?: return@registerForActivityResult
        pendingPermission = null
        val allowed = request.resources.filter { res ->
            when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> granted[Manifest.permission.RECORD_AUDIO] == true ||
                    hasPermission(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> granted[Manifest.permission.CAMERA] == true ||
                    hasPermission(Manifest.permission.CAMERA)
                else -> false
            }
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val legacySave = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingLegacySave ?: return@registerForActivityResult
        pendingLegacySave = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                fileSaver.writeTo(pending, uri)
                withContext(Dispatchers.Main) { toast("Saved ${pending.name}") }
            }
        } else {
            pending.file.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        fileSaver = FileSaver(this)
        buildViews()
        configureWebView()
        onBackPressedDispatcher.addCallback(this, backCallback)

        ServerService.start(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ServerController.state.collect { render(it) } }
                launch {
                    ServerController.log.version.collect {
                        if (overlay.visibility == View.VISIBLE) {
                            overlayLog.text = ServerController.log.tail(14).joinToString("\n")
                        }
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Launcher shortcuts (res/xml/shortcuts.xml) come through here so the other screens stay unexported.
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            "settings" -> openSettings()
            "log" -> startActivity(Intent(this, LogActivity::class.java))
        }
        intent?.removeExtra(EXTRA_OPEN)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        applyWindowFlags()
        // Some vendor freezers (vivo/iQOO, OPPO) thaw the app's main process on return but leave the WebView
        // renderer frozen until it gets input, so a reply that paused in the background would only continue
        // after a tap. Talking to the renderer right away (resume + a no-op script) thaws it.
        webView.onResume()
        webView.evaluateJavascript("void 0", null)
        webView.postDelayed({ if (!isDestroyed) webView.evaluateJavascript("void 0", null) }, 500)
        WebView.setWebContentsDebuggingEnabled(prefs.webDebugging || BuildConfig.DEBUG)
    }

    override fun onUnlockedResume() {
        NotificationManagerCompat.from(this).cancel(REPLY_NOTIFICATION_ID)
        maybeAskNotificationPermission()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    @Synchronized
    private fun speech(): NativeTts = nativeTts ?: NativeTts(this) { js ->
        runOnUiThread { if (!isDestroyed) webView.evaluateJavascript(js, null) }
    }.also { nativeTts = it }

    override fun onDestroy() {
        nativeTts?.shutdown()
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ views

    private fun buildViews() {
        val background = Color.rgb(0x17, 0x17, 0x17)
        root = FrameLayout(this).apply { setBackgroundColor(background) }
        webView = WebView(this).apply {
            setBackgroundColor(background)
            visibility = View.INVISIBLE
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val pad = dp(24)
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, dp(72), pad, pad)
            setBackgroundColor(background)
        }
        overlayTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        overlayStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(0xcc, 0xcc, 0xcc))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(12))
        }
        overlayProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            isIndeterminate = true
        }
        overlayButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        overlayLog = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(0x88, 0x88, 0x88))
            setTextIsSelectable(true)
        }
        val logScroll = ScrollView(this).apply { addView(overlayLog) }
        overlay.addView(overlayTitle)
        overlay.addView(overlayStatus)
        overlay.addView(overlayProgress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)))
        overlay.addView(overlayButtons)
        overlay.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (customView == null) {
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setButtons(vararg buttons: Pair<String, () -> Unit>) {
        overlayButtons.removeAllViews()
        for ((label, action) in buttons) {
            overlayButtons.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
    }

    private fun render(state: ServerController.State) {
        when (state) {
            is ServerController.State.Running -> {
                if (loadedUrl != state.url || loadedRun != state.run) {
                    loadedUrl = state.url
                    loadedRun = state.run
                    pageShown = false
                    webView.loadUrl(state.url)
                }
                overlayStatus.text = "Loading interface…"
                setButtons()
            }
            is ServerController.State.Preparing -> showOverlay(state.message, state.progress)
            ServerController.State.Starting -> showOverlay("Starting SillyTavern…", null)
            is ServerController.State.Failed -> {
                showOverlay(state.message, null, done = true)
                setButtons(
                    "Retry" to { ServerService.restart(this) },
                    "Logs" to { startActivity(Intent(this, LogActivity::class.java)) },
                    "Settings" to { openSettings() },
                )
            }
            ServerController.State.Stopped -> {
                showOverlay("The server is stopped.", null, done = true)
                setButtons(
                    "Start" to { ServerService.start(this) },
                    "Settings" to { openSettings() },
                )
            }
        }
    }

    private fun showOverlay(status: String, progress: Float?, done: Boolean = false) {
        pageShown = false
        loadedUrl = null
        overlay.visibility = View.VISIBLE
        webView.visibility = View.INVISIBLE
        overlayStatus.text = status
        overlayProgress.visibility = if (done) View.GONE else View.VISIBLE
        overlayProgress.isIndeterminate = progress == null
        if (progress != null) overlayProgress.progress = (progress * 1000).toInt()
        if (!done) setButtons()
        overlayLog.text = ServerController.log.tail(14).joinToString("\n")
    }

    /**
     * By default WebView lowers its renderer process's priority when the app is not visible, which lets
     * freezers pause the page mid-reply. While a reply is generating, keep it bound as important.
     */
    private fun keepRendererImportant(important: Boolean) {
        if (Build.VERSION.SDK_INT < 26 || isDestroyed) return
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, !important)
    }

    private fun revealPage() {
        if (pageShown) return
        pageShown = true
        webView.visibility = View.VISIBLE
        overlay.visibility = View.GONE
    }

    private fun applyWindowFlags() {
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------------------------------------------------------------- webview

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(prefs.webDebugging || BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = false
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString TavernDroid/${BuildConfig.VERSION_NAME}"
        }
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.addJavascriptInterface(Bridge(), "TavernDroidNative")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeScript, setOf("*"))
        }
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                webView.evaluateJavascript("window.__taverndroidSave && window.__taverndroidSave(${jsString(url)})", null)
            } else {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                downloadHttp(url, userAgent, name, mimeType)
            }
        }
    }

    private fun isLocal(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return (host == "127.0.0.1" || host == "localhost") && uri.port == prefs.port
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            toast("No app can open $uri")
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when {
                isLocal(uri) -> false
                uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto" || uri.scheme == "intent" -> {
                    openExternal(uri); true
                }
                uri.scheme == "about" || uri.scheme == "blob" || uri.scheme == "data" || uri.scheme == "javascript" -> false
                else -> { openExternal(uri); true }
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(bridgeScript, null)
            }
            if (isLocal(Uri.parse(url))) revealPage()
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            if (prefs.lanAccess) handler.proceed(prefs.lanUser, prefs.lanPassword) else handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            // The renderer crashed or was killed for memory; rebuild the activity instead of dying with it.
            Log.w(TAG, "WebView renderer gone (crash=${detail.didCrash()})")
            recreate()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val mimes = params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
                .map { type ->
                    if (type.startsWith(".")) {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1).lowercase())
                    } else {
                        type
                    }
                }
            val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            // Only narrow the picker when every accepted type is a known MIME type
            // (SillyTavern also accepts .jsonl, .charx, .byaf, ... which Android cannot map).
            if (mimes.isNotEmpty() && mimes.all { it != null }) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes.filterNotNull().distinct().toTypedArray())
            }
            if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            return try {
                AppLock.allowExternalActivity()
                pickFiles.launch(Intent.createChooser(intent, null))
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                val needed = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        else -> null
                    }
                }
                if (needed.isEmpty()) {
                    request.deny()
                } else {
                    pendingPermission = request
                    askPermissions.launch(needed.toTypedArray())
                }
            }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // window.open(): catch the first navigation of a throwaway WebView and hand it to the browser.
            val popup = WebView(this@MainActivity)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (isLocal(uri)) webView.loadUrl(uri.toString()) else openExternal(uri)
                    v.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView?.let { onHideCustomView() }
            customView = view
            customViewCallback = callback
            root.setPadding(0, 0, 0, 0)
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        override fun onHideCustomView() {
            customView?.let { root.removeView(it) }
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            ViewCompat.requestApplyInsets(root)
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                Log.w(TAG, "console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            }
            return true
        }
    }

    // ------------------------------------------------------------------ back

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (customView != null) {
                (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
                return
            }
            if (!pageShown) {
                moveTaskToBack(true)
                return
            }
            webView.evaluateJavascript("window.__taverndroidBack ? window.__taverndroidBack() : false") { result ->
                if (result == "true") {
                    // SillyTavern closes the topmost panel/popup on Escape.
                    webView.requestFocus()
                    webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
                    webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    // ------------------------------------------------------------- downloads

    private fun downloadHttp(url: String, userAgent: String, name: String, mime: String?) {
        toast("Downloading $name…")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                if (prefs.lanAccess && isLocal(Uri.parse(url))) {
                    val token = Base64.encodeToString("${prefs.lanUser}:${prefs.lanPassword}".toByteArray(), Base64.NO_WRAP)
                    connection.setRequestProperty("Authorization", "Basic $token")
                }
                val dir = StPaths(this@MainActivity).downloads.apply { mkdirs() }
                val temp = File.createTempFile("dl", null, dir)
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val type = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                    ?: connection.contentType?.substringBefore(';') ?: ""
                val pending = fileSaver.adopt(temp, name, type)
                withContext(Dispatchers.Main) { finishSave(pending) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Download failed: ${e.message}") }
            }
        }
    }

    private fun finishSave(pending: FileSaver.Pending) {
        if (Build.VERSION.SDK_INT >= 29) {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching { fileSaver.publish(pending) }
                withContext(Dispatchers.Main) {
                    val uri = result.getOrNull()
                    if (uri != null) {
                        toast("Saved to Downloads/SillyTavern/${pending.name}")
                        notifySaved(pending, uri)
                    } else {
                        toast("Could not save ${pending.name}: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        } else {
            pendingLegacySave = pending
            AppLock.allowExternalActivity()
            legacySave.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(pending.mime).putExtra(Intent.EXTRA_TITLE, pending.name)
            )
        }
    }

    private fun notifySaved(pending: FileSaver.Pending, uri: Uri) {
        if (!canNotify()) return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, pending.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getActivity(
            this, pending.name.hashCode(), Intent.createChooser(view, pending.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, ServerService.CHANNEL_FILES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(pending.name)
            .setContentText("Saved to Downloads/SillyTavern")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        @SuppressLint("MissingPermission")
        fun post() = NotificationManagerCompat.from(this).notify(pending.name.hashCode(), n)
        post()
    }

    // ---------------------------------------------------------------- bridge

    /** Methods callable from the page as window.TavernDroidNative.* (see assets/taverndroid.js). */
    private inner class Bridge {
        @JavascriptInterface
        fun saveBegin(name: String, mime: String, @Suppress("UNUSED_PARAMETER") size: Double): String =
            fileSaver.begin(name, mime)

        @JavascriptInterface
        fun saveChunk(id: String, base64: String) = fileSaver.append(id, Base64.decode(base64, Base64.DEFAULT))

        @JavascriptInterface
        fun saveEnd(id: String) {
            val pending = fileSaver.take(id) ?: return
            runOnUiThread { finishSave(pending) }
        }

        @JavascriptInterface
        fun saveAbort(id: String) = fileSaver.abort(id)

        @JavascriptInterface
        fun toast(message: String) = runOnUiThread { this@MainActivity.toast(message) }

        @JavascriptInterface
        fun openSettings() = runOnUiThread { this@MainActivity.openSettings() }

        @JavascriptInterface
        fun generationStarted(@Suppress("UNUSED_PARAMETER") type: String) {
            Generation.started(applicationContext)
            runOnUiThread {
                keepRendererImportant(true)
                Background.maybeAskOnce(this@MainActivity)
            }
        }

        @JavascriptInterface
        fun generationEnded() {
            Generation.release()
            runOnUiThread { keepRendererImportant(false) }
        }

        @JavascriptInterface
        fun replyReceived(name: String, text: String) = runOnUiThread { notifyReply(name, text) }

        @JavascriptInterface
        fun pageReady() = runOnUiThread { revealPage() }

        @JavascriptInterface
        fun ttsVoices(): String = speech().voices()

        @JavascriptInterface
        fun ttsSpeak(id: String, text: String, voice: String, lang: String, rate: Float, pitch: Float, volume: Float) =
            speech().speak(id, text, voice, lang, rate, pitch, volume)

        @JavascriptInterface
        fun ttsStop() = speech().stop()

        /** Typing and scrolling inside the page (keyboard input never reaches onUserInteraction). */
        @JavascriptInterface
        fun userActive() = AppLock.touch()
    }

    private fun notifyReply(name: String, text: String) {
        if (resumed || !prefs.notifyReplies || !canNotify()) return
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, ServerService.CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Android 7 (no channels): sound, vibration, pop-up
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .apply {
                if (prefs.notifyReplyText) {
                    setContentText(TextUtils.ellipsize(text, android.text.TextPaint(), 2000f, TextUtils.TruncateAt.END))
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                } else {
                    setContentText("New reply")
                }
            }
            .build()
        // Remove the previous reply first: an identical update to a notification that is still shown ("New reply"
        // again) counts as non-interruptive, so Android would replace it without sound or vibration.
        val nm = NotificationManagerCompat.from(this)
        nm.cancel(REPLY_NOTIFICATION_ID)
        @SuppressLint("MissingPermission")
        fun post() = nm.notify(REPLY_NOTIFICATION_ID, n)
        post()
    }

    // ----------------------------------------------------------------- utils

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotificationPermission &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            prefs.askedNotificationPermission = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun jsString(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    companion object {
        private const val TAG = "TavernDroid"
        private const val REPLY_NOTIFICATION_ID = 2
        const val EXTRA_OPEN = "open"
    }
}
