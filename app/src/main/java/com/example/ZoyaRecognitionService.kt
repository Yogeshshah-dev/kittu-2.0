package com.example

import android.content.Intent
import android.speech.RecognitionService

class ZoyaRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Handled internally by ZoyaForegroundService's audio engine
    }

    override fun onCancel(listener: Callback?) {
        // Handled internally by ZoyaForegroundService's audio engine
    }

    override fun onStopListening(listener: Callback?) {
        // Handled internally by ZoyaForegroundService's audio engine
    }
}
