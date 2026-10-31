package hu.szileemester.adamazon.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens in Hungarian from [start] until [stop], across pauses: the recognizer ends an utterance
 * after a short silence, and is restarted at once, so each pause separates two utterances.
 *
 * Google's recognizer is asked for by name – the phone's default may be a weaker one (Samsung's).
 * The start beeps that every restart would make are muted while listening (notification and system
 * sounds; music and navigation stay on). The muted streams are remembered, so they are restored at
 * the next app start even if the app dies mid-listening ([restoreMutedStreams]). Main thread only.
 */
class VoiceRecognizer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val heard = mutableListOf<String>()
    private var stopping = false
    private var onDone: ((Result) -> Unit)? = null

    /** What has been heard so far while listening; null when not listening. */
    data class Listening(val heard: List<String>, val partial: String)

    sealed interface Result {
        data class Heard(val utterances: List<String>) : Result
        data class Failed(val message: String) : Result
    }

    private val _state = MutableStateFlow<Listening?>(null)
    val state: StateFlow<Listening?> = _state.asStateFlow()

    val isAvailable: Boolean
        get() = googleService() != null || SpeechRecognizer.isRecognitionAvailable(app)

    fun start(onDone: (Result) -> Unit) {
        if (_state.value != null) return
        val created = SpeechRecognizer.createSpeechRecognizer(app, googleService()) ?: run {
            onDone(Result.Failed("A beszédfelismerés nem érhető el ezen a telefonon."))
            return
        }
        this.onDone = onDone
        heard.clear()
        stopping = false
        recognizer = created.apply { setRecognitionListener(listener) }
        muteStreams()
        _state.value = Listening(emptyList(), "")
        listen()
        handler.postDelayed(::stop, MAX_LISTENING_MS)
    }

    /** Stops listening; what was said until now is still delivered. */
    fun stop() {
        if (_state.value == null || stopping) return
        stopping = true
        recognizer?.stopListening()
        // Some recognizers never answer a stop without speech; don't hang waiting for them.
        handler.postDelayed({ finish(null) }, STOP_GRACE_MS)
    }

    /** Stops without delivering anything, e.g. when the screen goes away. */
    fun cancel() {
        onDone = null
        finish(null)
    }

    private fun listen() {
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName),
        )
    }

    private fun finish(error: String?) {
        if (_state.value == null) return
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        restoreStreams()
        _state.value = null
        val callback = onDone ?: return
        onDone = null
        callback(if (error != null && heard.isEmpty()) Result.Failed(error) else Result.Heard(heard.toList()))
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { heard += it.trim() }
            _state.value = Listening(heard.toList(), "")
            if (stopping) finish(null) else listen()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _state.value = Listening(heard.toList(), partial)
        }

        override fun onError(error: Int) {
            when {
                stopping -> finish(null)
                // Silence or nothing understood: keep listening.
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT -> {
                    recognizer?.cancel()
                    handler.postDelayed(::listen, RETRY_DELAY_MS)
                }
                else -> finish(errorMessage(error))
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognizer.ERROR_AUDIO -> "Nem érem el a mikrofont."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "A beszédfelismeréshez internet kell, vagy a magyar offline nyelvi csomag."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "A magyar beszédfelismerés nem érhető el ezen a telefonon."
        else -> "A beszédfelismerés hibát jelzett (kód: $error)."
    }

    private fun googleService(): ComponentName? {
        val services = app.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        return GOOGLE_PACKAGES.firstNotNullOfOrNull { pkg ->
            services.firstOrNull { it.serviceInfo.packageName == pkg }?.serviceInfo
        }?.let { ComponentName(it.packageName, it.name) }
    }

    private fun muteStreams() {
        val muted = BEEP_STREAMS.filter { stream ->
            !audio.isStreamMute(stream) &&
                runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0) }.isSuccess
        }
        prefs.edit { putString(MUTED_KEY, muted.joinToString(",")) }
    }

    private fun restoreStreams() = restoreMutedStreams(app)

    companion object {
        /** Unmutes what an earlier listening left muted; called on every app start. */
        fun restoreMutedStreams(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val muted = prefs.getString(MUTED_KEY, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
            if (muted.isEmpty()) return
            val audio = context.getSystemService(AudioManager::class.java)
            muted.forEach { runCatching { audio.adjustStreamVolume(it, AudioManager.ADJUST_UNMUTE, 0) } }
            prefs.edit { remove(MUTED_KEY) }
        }

        private const val PREFS = "voice"
        private const val LANGUAGE = "hu-HU"
        private const val MAX_LISTENING_MS = 2 * 60 * 1000L
        private const val STOP_GRACE_MS = 2_000L
        private const val RETRY_DELAY_MS = 300L
        private const val MUTED_KEY = "mutedStreams"
        private val GOOGLE_PACKAGES = listOf("com.google.android.googlequicksearchbox", "com.google.android.tts", "com.google.android.as")
        private val BEEP_STREAMS = listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    }
}
