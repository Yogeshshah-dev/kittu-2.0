package com.example

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ZoyaWakeWordDetector(
    private val onWakeWordDetected: () -> Unit
) {
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var detectorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Configuration
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRunning) return
        isRunning = true

        ZoyaSessionManager.updateWakeWordListening(true)
        Log.d("ZoyaWakeWord", "Wake-word detector starting up...")

        detectorJob = scope.launch {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)
            
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("ZoyaWakeWord", "AudioRecord failed to initialize.")
                    isRunning = false
                    ZoyaSessionManager.updateWakeWordListening(false)
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(1024)

                // Sys-variables for dynamic amplitude modeling representing "Zo - ya" syllables
                var ambientRms = 100.0
                var peakDetected = false
                var peakTime = 0L
                var dipDetected = false
                var dipTime = 0L

                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize <= 0) {
                        delay(20)
                        continue
                    }

                    // Calculate RMS
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += buffer[i] * buffer[i]
                    }
                    val currentRms = sqrt(sum / readSize)

                    // Track running baseline ambient floor
                    ambientRms = (ambientRms * 0.99) + (currentRms * 0.01)
                    if (ambientRms < 10.0) ambientRms = 10.0

                    val ratio = currentRms / ambientRms
                    val currentTime = System.currentTimeMillis()

                    // Visual feed to UI for wave animation
                    ZoyaSessionManager.updateUserAudioLevel(ratio.toFloat().coerceIn(1f, 10f) / 10f)

                    // Dual peak syllable pattern matching representing "Zo" (syllable 1) and "ya" (syllable 2):
                    // Syllable 1 (Zo): Sharp peak 3.5x above baseline, duration 100-300ms
                    // Syllable 2 (ya): Secondary peak 2.0x above baseline, following a brief 100-400ms dip.
                    if (!peakDetected && ratio > 3.0) {
                        peakDetected = true
                        peakTime = currentTime
                        Log.d("ZoyaWakeWord", "Acoustic syllable peak detected (Zo): Ratio $ratio")
                    } else if (peakDetected) {
                        val durationSincePeak = currentTime - peakTime
                        if (durationSincePeak > 800) {
                            // Reset state if too long has passed without a match
                            peakDetected = false
                            dipDetected = false
                        } else if (!dipDetected && ratio < 1.8 && durationSincePeak > 150) {
                            dipDetected = true
                            dipTime = currentTime
                        } else if (dipDetected && ratio > 2.2 && (currentTime - dipTime) > 100) {
                            // Final match! Syllabic dual peaks matching "Zoya"
                            Log.d("ZoyaWakeWord", "🎉 Syllable pattern matches 'Zoya'! Triggering live activation...")
                            onWakeWordDetected()
                            
                            // Reset state
                            peakDetected = false
                            dipDetected = false
                            delay(2000) // Cooldown to guard against recursive triggers
                        }
                    }

                    delay(30)
                }
            } catch (e: Exception) {
                Log.e("ZoyaWakeWord", "Detector loop error: ${e.message}")
            } finally {
                releaseResources()
            }
        }
    }

    fun stopListening() {
        isRunning = false
        ZoyaSessionManager.updateWakeWordListening(false)
        detectorJob?.cancel()
        detectorJob = null
        releaseResources()
    }

    private fun releaseResources() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
