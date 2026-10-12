package io.github.taverndroid

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Android TextToSpeech behind the page's Web Speech API polyfill (assets/taverndroid.js):
 * Android WebView has no speech synthesis, which SillyTavern's "System" TTS provider needs.
 * Events go back to the page through [emit] as window.__tdSpeech.onNative(id, type, detail).
 */
class NativeTts(context: Context, private val emit: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    /** null until the engine reports back; then true (usable) or false (no TTS engine). */
    @Volatile private var ready: Boolean? = null
    private val pending = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                val ok = status == TextToSpeech.SUCCESS
                ready = ok
                if (ok) tts?.setOnUtteranceProgressListener(listener)
                pending.forEach { (action, fail) -> if (ok) action() else fail() }
                pending.clear()
                emit("window.__tdSpeech && window.__tdSpeech.onVoicesChanged()")
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String) = event(id, "start")
        override fun onDone(id: String) = event(id, "end")
        override fun onStop(id: String, interrupted: Boolean) = event(id, "end")
        @Deprecated("Deprecated in Java")
        override fun onError(id: String) = event(id, "error", "synthesis-failed")
        override fun onError(id: String, errorCode: Int) = event(id, "error", "synthesis-failed")
    }

    private fun event(id: String, type: String, detail: String = "") {
        main.post {
            emit("window.__tdSpeech && window.__tdSpeech.onNative(${JSONObject.quote(id)}, '$type', ${JSONObject.quote(detail)})")
        }
    }

    private fun whenReady(action: () -> Unit, fail: () -> Unit = {}) = main.post {
        when (ready) {
            true -> action()
            false -> fail()
            null -> pending += action to fail
        }
    }

    /** JSON array of {voiceURI, name, lang, localService, default}; empty until the engine is ready. */
    fun voices(): String {
        val engine = tts ?: return "[]"
        if (ready != true) return "[]"
        val default = runCatching { engine.defaultVoice?.name }.getOrNull()
        val list = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
        val out = JSONArray()
        for (v in list) {
            out.put(JSONObject()
                .put("voiceURI", v.name)
                .put("name", "${v.locale.displayName} · ${v.name}")
                .put("lang", v.locale.toLanguageTag())
                .put("localService", !v.isNetworkConnectionRequired)
                .put("default", v.name == default))
        }
        return out.toString()
    }

    fun speak(id: String, text: String, voiceUri: String?, lang: String?, rate: Float, pitch: Float, volume: Float) =
        whenReady(action = {
            val engine = tts ?: return@whenReady
            val voice = voiceUri?.takeIf { it.isNotBlank() }?.let { uri -> engine.voices?.firstOrNull { it.name == uri } }
            when {
                voice != null -> engine.voice = voice
                !lang.isNullOrBlank() -> engine.language = Locale.forLanguageTag(lang)
                else -> engine.defaultVoice?.let { engine.voice = it }
            }
            engine.setSpeechRate(rate.coerceIn(0.1f, 4f))
            engine.setPitch(pitch.coerceIn(0.1f, 2f))
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
            if (engine.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) {
                event(id, "error", "synthesis-failed")
            }
        }, fail = { event(id, "error", "synthesis-unavailable") })

    fun stop() = whenReady(action = { tts?.stop() })

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
