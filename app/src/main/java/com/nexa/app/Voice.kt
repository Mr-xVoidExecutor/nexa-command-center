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
import java.util.Locale

/** Current emotional state of NEXA (drives voice tone and avatar look). */
object Mood { val now = mutableStateOf("calm") }

/** Text-to-speech with a low, calm, "hacker" tone that shifts with mood. */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    val speaking = mutableStateOf(false)

    fun init(c: Context) {
        if (tts != null) return
        tts = TextToSpeech(c.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking.value = true }
                    override fun onDone(utteranceId: String?) { speaking.value = false }
                    override fun onError(utteranceId: String?) { speaking.value = false }
                })
                ready = true
            }
        }
    }

    fun say(t: String, mood: String = "calm") {
        if (!ready || t.isBlank()) return
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

    fun stop() { tts?.stop(); speaking.value = false }
}

/** Speech-to-text wrapper. Must be created and used on the main thread. */
class Listener(
    private val c: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean) -> Unit
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
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun start() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        sr?.startListening(i)
    }

    fun destroy() { sr?.destroy() }
}
