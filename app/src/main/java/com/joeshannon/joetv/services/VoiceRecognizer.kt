package com.joeshannon.joetv.services

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

// -----------------------------------------------------------------------------
// JoeTV Voice Recognizer
//
// Wraps Vosk's offline speech recognizer for JoeTV's voice search.
//
// This exists because LineageOS TV without GApps has no system speech
// recognizer at all -- no Google app, no "Speech Services by Google" -- so
// android.speech.RecognizerIntent has nothing to resolve to on this device.
// Vosk is bundled directly into JoeTV instead, so voice search works with
// zero Google dependency and zero network connection.
//
// The small English model (~40MB, bundled at assets/model-en-us) is unpacked
// to internal storage once per install and reused for the life of the app.
// -----------------------------------------------------------------------------

/**
 * Current state of the bundled voice model.
 */
sealed class VoiceRecognizerState {

    /** Model is being unpacked from assets to internal storage. */
    object Loading : VoiceRecognizerState()

    /** Model is loaded and ready to start listening. */
    object Ready : VoiceRecognizerState()

    /** Model failed to unpack or load. */
    data class Failed(val message: String) : VoiceRecognizerState()
}


/**
 * Owns the Vosk model and the microphone recognition session used by JoeTV's
 * mic button and remote voice-search hotkey.
 *
 * @param context Any valid Android context. The application context is
 * stored internally to avoid accidentally retaining an Activity.
 */
class VoiceRecognizer(
    context: Context
) {
    private val appContext = context.applicationContext

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var hasStartedLoading = false

    private val _state = mutableStateOf<VoiceRecognizerState>(VoiceRecognizerState.Loading)

    /**
     * Read-only Compose state observed by the JoeTV home screen, mainly so a
     * mic tap before the model finishes loading can show a clear message
     * instead of silently doing nothing.
     */
    val state: State<VoiceRecognizerState> = _state


    /**
     * Starts unpacking/loading the bundled model if that hasn't already
     * begun. Safe to call every time HomeScreen enters composition -- later
     * calls after the first are no-ops.
     */
    fun prepare() {
        if (hasStartedLoading) return
        hasStartedLoading = true

        Log.d(TAG, "Starting model unpack from assets/model-en-us")

        StorageService.unpack(
            appContext,
            "model-en-us",
            "model",
            { unpackedModel ->
                Log.d(TAG, "Model unpack succeeded, ready to listen")
                model = unpackedModel
                _state.value = VoiceRecognizerState.Ready
            },
            { exception ->
                Log.e(TAG, "Model unpack failed", exception)
                _state.value = VoiceRecognizerState.Failed(
                    exception.message ?: "Failed to load the voice model"
                )
            }
        )
    }


    /**
     * Starts listening on the microphone for a single spoken phrase.
     *
     * [onResult] fires once with the best transcript Vosk heard; [onError]
     * fires if the model isn't ready yet, nothing was understood, or the
     * recognizer itself failed. Exactly one of the two always fires.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val loadedModel = model

        if (loadedModel == null) {
            // This used to always say "still loading" regardless of what
            // actually happened -- if unpacking failed outright, that real
            // reason was silently swallowed and this generic message shown
            // instead, forever. Surface whatever state.value actually is.
            val message = when (val current = _state.value) {
                is VoiceRecognizerState.Loading ->
                    "Voice model is still loading -- try again in a moment"
                is VoiceRecognizerState.Failed ->
                    "Voice model failed to load: ${current.message}"
                is VoiceRecognizerState.Ready ->
                    // Should be unreachable (model would be non-null), but
                    // fall back to something informative rather than lying.
                    "Voice model reported ready but has no model loaded"
            }

            Log.e(TAG, "startListening() called with no model: $message")
            onError(message)
            return
        }

        // A stray previous session (e.g. the mic was tapped again before the
        // last one finished) should never run two recognizers at once.
        stopListening()

        val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
        val service = SpeechService(recognizer, SAMPLE_RATE)
        speechService = service

        var resultDelivered = false

        Log.d(TAG, "startListening: mic session starting (timeout ${LISTEN_TIMEOUT_MS}ms)")

        // Without an explicit timeout this listens forever -- if the Pi has
        // no working microphone input at all, RecognitionListener never
        // fires anything and the "Listening..." overlay would be stuck
        // permanently with no feedback. The timeout guarantees onTimeout()
        // fires and the user gets a message either way.
        service.startListening(object : RecognitionListener {

            override fun onPartialResult(hypothesis: String) {
                // Not surfaced to the UI -- JoeTV only acts on the final
                // transcript for one phrase per mic tap, not live partials.
                // Logged anyway: partials firing at all is the clearest
                // signal the mic is actually picking up audio.
                Log.d(TAG, "onPartialResult: $hypothesis")
            }

            override fun onResult(hypothesis: String) {
                Log.d(TAG, "onResult: $hypothesis")

                // SpeechService calls this once per detected pause in
                // speech and keeps listening afterward. JoeTV only wants a
                // single phrase per mic tap, so the first result ends the
                // session rather than waiting for onFinalResult (which only
                // fires once stop() is called from outside).
                if (resultDelivered) return
                resultDelivered = true

                finishListening(hypothesis, onResult, onError)
            }

            override fun onFinalResult(hypothesis: String) {
                Log.d(TAG, "onFinalResult: $hypothesis")

                if (resultDelivered) return
                resultDelivered = true

                finishListening(hypothesis, onResult, onError)
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "onError during listening", exception)
                resultDelivered = true
                stopListening()
                onError(exception.message ?: "Voice recognition failed")
            }

            override fun onTimeout() {
                Log.d(TAG, "onTimeout fired -- ${LISTEN_TIMEOUT_MS}ms passed with no usable result")

                if (resultDelivered) return
                resultDelivered = true

                stopListening()
                onError("Didn't catch that -- try again")
            }
        }, LISTEN_TIMEOUT_MS)
    }


    private fun finishListening(
        hypothesis: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val text = extractText(hypothesis)
        stopListening()

        if (text.isNullOrBlank()) {
            onError("Didn't catch that -- try again")
        } else {
            onResult(text)
        }
    }


    /**
     * Pulls the "text" field out of Vosk's JSON result, e.g.
     * `{"text" : "youtube"}`.
     */
    private fun extractText(hypothesis: String): String? {
        if (hypothesis.isBlank()) return null

        return runCatching {
            JSONObject(hypothesis).optString("text", "")
        }.getOrNull()?.trim()
    }


    /**
     * Stops and releases the current listening session, if any. Safe to
     * call even when nothing is listening.
     */
    fun stopListening() {
        speechService?.let { service ->
            service.stop()
            service.shutdown()
        }
        speechService = null
    }


    /**
     * Releases everything, including the loaded model. Call when JoeTV's
     * home screen leaves composition for good.
     */
    fun release() {
        stopListening()
        model = null
    }


    companion object {
        private const val TAG = "JoeTvVoiceRecognizer"
        private const val SAMPLE_RATE = 16000.0f
        private const val LISTEN_TIMEOUT_MS = 8000
    }
}
