package com.joeshannon.joetv.services

import android.content.Context
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

        StorageService.unpack(
            appContext,
            "model-en-us",
            "model",
            { unpackedModel ->
                model = unpackedModel
                _state.value = VoiceRecognizerState.Ready
            },
            { exception ->
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
            onError("Voice model is still loading -- try again in a moment")
            return
        }

        // A stray previous session (e.g. the mic was tapped again before the
        // last one finished) should never run two recognizers at once.
        stopListening()

        val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
        val service = SpeechService(recognizer, SAMPLE_RATE)
        speechService = service

        var resultDelivered = false

        service.startListening(object : RecognitionListener {

            override fun onPartialResult(hypothesis: String) {
                // Not surfaced -- JoeTV only acts on the final transcript for
                // one phrase per mic tap, not live partials.
            }

            override fun onResult(hypothesis: String) {
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
                if (resultDelivered) return
                resultDelivered = true

                finishListening(hypothesis, onResult, onError)
            }

            override fun onError(exception: Exception) {
                resultDelivered = true
                stopListening()
                onError(exception.message ?: "Voice recognition failed")
            }

            override fun onTimeout() {
                if (resultDelivered) return
                resultDelivered = true

                stopListening()
                onError("Didn't catch that -- try again")
            }
        })
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
        private const val SAMPLE_RATE = 16000.0f
    }
}
