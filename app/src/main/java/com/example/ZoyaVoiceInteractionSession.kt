package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class ZoyaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d("ZoyaVoiceInteractionSession", "Voice Assistant Session triggered via long-press/gesture!")

        val serviceIntent = Intent(context, ZoyaForegroundService::class.java).apply {
            action = ZoyaForegroundService.ACTION_AWAKE
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("ZoyaVoiceInteractionSession", "Failed to awake Zoya Foreground Service: ${e.message}")
        }

        // Auto finish session so our custom overlay can show independently
        finish()
    }
}
