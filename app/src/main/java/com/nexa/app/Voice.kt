package com.nexa.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import java.util.Locale

/** Current emotional state of NEXA (drives voice tone and avatar look). */
object Mood { val now = mutableStateOf("calm") }

/** Text-to-speech with a low, calm, "hacker" tone that shifts with mood. */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentText = ""
    val speaking = mutableStateOf(false)
    val mouthOpen = mutableFloatStateOf(0f)

    fun init(c: Context) {
        if (tts != null) return
        tts = TextToSpeech(c.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speaking.value = true
                        mouthOpen.floatValue = 0.28f
                    }
                    override fun onDone(utteranceId: String?) {
                        speaking.value = false
                        mouthOpen.floatValue = 0f
                    }
                    override fun onError(utteranceId: String?) {
                        speaking.value = false
                        mouthOpen.floatValue = 0f
                    }
                    override fun onRangeStart(utteranceId: String?, start: Int, length: Int, frame: Int) {
                        val segment = currentText.substring(start.coerceAtMost(currentText.length),
                            (start + length).coerceAtMost(currentText.length)).lowercase(Locale.US)
                        val open = when {
                            segment.startsWith("m") || segment.startsWith("b") || segment.startsWith("p") -> 0.08f
                            segment.any { it in "ou" } -> 0.82f
                            segment.any { it in "aei" } -> 0.57f
                            else -> 0.22f
                        }
                        mouthOpen.floatValue = open
                    }
                })
                ready = true
            }
        }
    }

    fun say(t: String, mood: String = "calm") {
        if (!ready || t.isBlank()) return
        currentText = t
        val (pitch, rate) = when (mood) {
            "happy" -> 0.98f to 1.05f
            "playful" -> 0.98f to 1.08f
            "angry" -> 0.78f to 1.12f
            "sad" -> 0.80f to 0.85f
            "shy" -> 1.02f to 0.90f
            "caring" -> 0.90f to 0.88f
            else -> 0.85f to 0.95f
        }
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "n" + System.nanoTime())
    }

    fun stop() {
        tts?.stop()
        speaking.value = false
        mouthOpen.floatValue = 0f
    }
}

/** Speech-to-text wrapper. Must be created and used on the main thread. */
class Listener(
    private val c: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit = {}
) {
    private val sr: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(c)) SpeechRecognizer.createSpeechRecognizer(c) else null

    init {
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onState(false) }
            override fun onError(error: Int) { onState(false) }
            override fun onResults(results: Bundle?) {
                onState(false)
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) onText(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun start() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        sr?.startListening(i)
    }

    fun destroy() { sr?.destroy() }
}
