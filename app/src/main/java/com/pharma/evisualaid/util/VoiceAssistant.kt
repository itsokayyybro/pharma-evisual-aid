package com.pharma.evisualaid.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class VoiceAssistant(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceAssistant", "Language not supported")
                Toast.makeText(context, "Voice language not supported on this device", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("VoiceAssistant", "TTS Ready")
                isInitialized = true
                Toast.makeText(context, "AI Voice Assistant Ready", Toast.LENGTH_SHORT).show()
                pendingText?.let { speak(it) }
                pendingText = null
            }
        } else {
            Log.e("VoiceAssistant", "TTS Init Failed: $status")
            Toast.makeText(context, "Voice Assistant failed to start", Toast.LENGTH_SHORT).show()
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            Log.d("VoiceAssistant", "Speaking...")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AiDescription")
        } else {
            Log.w("VoiceAssistant", "Not ready, queuing text")
            pendingText = text
        }
    }

    fun stop() {
        tts?.stop()
        pendingText = null
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
