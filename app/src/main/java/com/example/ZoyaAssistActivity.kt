package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

class ZoyaAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ZoyaAssistActivity", "Zoya Assist Intent triggered - Awaking assistant!")

        // Broadcast Wake trigger action to the Foreground service
        val serviceIntent = Intent(this, ZoyaForegroundService::class.java).apply {
            action = ZoyaForegroundService.ACTION_AWAKE
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("ZoyaAssistActivity", "Failed to awake Zoya Foreground Service: ${e.message}")
        }

        // Instantly close displayless assist container Activity
        finish()
    }
}
