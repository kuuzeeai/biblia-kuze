package com.kuzeai.biblia

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

object LectorVoz {
    private var tts: TextToSpeech? = null

    fun inicializar(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale("es", "ES")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }

    fun hablar(texto: String, callback: (() -> Unit)? = null) {
        tts?.stop()
        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_${System.currentTimeMillis()}")
        }
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "utterance_${System.currentTimeMillis()}")
        callback?.invoke()
    }

    fun pausar() {
        tts?.stop()
    }

    fun estaReproduciendo(): Boolean {
        return tts?.isSpeaking == true
    }

    fun limpiar(context: Context) {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
