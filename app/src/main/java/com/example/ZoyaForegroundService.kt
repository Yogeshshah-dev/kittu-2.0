package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

class ZoyaForegroundService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Engine Components
    private var wakeWordDetector: ZoyaWakeWordDetector? = null
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient()
    private var toolEngine: ZoyaToolExecutionEngine? = null
    private var overlayController: ZoyaOverlayController? = null

    // Audio recording for active session
    private var isRecordingSession = false
    private var sessionAudioRecord: AudioRecord? = null
    private var sessionRecordJob: Job? = null

    // Audio playback for Gemini's voice
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private var isPlaying = false

    inner class LocalBinder : Binder() {
        fun getService(): ZoyaForegroundService = this@ZoyaForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        toolEngine = ZoyaToolExecutionEngine(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Zoya is watching in the background..."))
        ZoyaSessionManager.updateServiceRunning(true)

        overlayController = ZoyaOverlayController(this).apply {
            startListeningToState()
        }

        // Initialize local wake word detector
        initializeWakeWordDetector()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START -> startWakeWordListening()
                ACTION_STOP -> stopServiceSelf()
                ACTION_AWAKE -> {
                    Log.d("ZoyaService", "Manual awake trigger received.")
                    awakenZoyaSession()
                }
                "com.example.action.STOP_ACTIVE_ONLY" -> {
                    Log.d("ZoyaService", "Request to stop active session but maintain background word detection.")
                    webSocket?.close(1000, "User clicked stop bubble.")
                    webSocket = null
                    stopActiveAudioSession()
                    ZoyaSessionManager.updateState(ZoyaState.IDLE)
                    startWakeWordListening()
                }
            }
        }
        return START_STICKY
    }

    private fun initializeWakeWordDetector() {
        wakeWordDetector = ZoyaWakeWordDetector {
            // Wake word "Zoya" detected! Let's immediately transition into active voice session
            awakenZoyaSession()
        }
        startWakeWordListening()
    }

    private fun startWakeWordListening() {
        try {
            wakeWordDetector?.startListening()
            ZoyaSessionManager.updateWakeWordListening(true)
            ZoyaSessionManager.updateStatusLabel("Say 'Zoya' or tap orb to start...")
        } catch (e: Exception) {
            Log.e("ZoyaService", "Failed to start wake word detector: ${e.message}")
        }
    }

    private fun stopWakeWordListening() {
        wakeWordDetector?.stopListening()
        ZoyaSessionManager.updateWakeWordListening(false)
    }

    /**
     * Instantly awakens the Gemini Live WebSocket session, changing Zoya's state from IDLE to LISTENING.
     */
    @SuppressLint("MissingPermission")
    fun awakenZoyaSession() {
        if (ZoyaSessionManager.currentState.value != ZoyaState.IDLE) {
            Log.d("ZoyaService", "Zoya is already active.")
            return
        }

        // Pause local wake-word detector to avoid resource contention over the microphone
        stopWakeWordListening()

        ZoyaSessionManager.updateState(ZoyaState.LISTENING)
        ZoyaSessionManager.updateStatusLabel("Waking up Zoya...")
        updateNotification("Waking up Zoya...")

        // Connect WebSocket to Gemini Multimodal Live API
        val apiKey = ZoyaSettings.getApiKey(this)
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
            ZoyaSessionManager.updateStatusLabel("API Key missing! Please set in Secrets or Settings.")
            ZoyaSessionManager.updateState(ZoyaState.IDLE)
            startWakeWordListening()
            return
        }

        // Multimodal Live API Bidirectional Endpoint
        val webSocketUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(webSocketUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ZoyaService", "Gemini Live WebSocket successfully opened!")
                sendSetupConfig()
                
                // Initialize audio recording and playback
                startActiveAudioSession()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ZoyaService", "WebSocket error: ${t.message}")
                stopActiveAudioSession()
                ZoyaSessionManager.updateStatusLabel("Connection failed. Returning to standby...")
                ZoyaSessionManager.updateState(ZoyaState.IDLE)
                startWakeWordListening()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ZoyaService", "WS Connection closed.")
                stopActiveAudioSession()
                ZoyaSessionManager.updateState(ZoyaState.IDLE)
                startWakeWordListening()
            }
        })
    }

    private fun sendSetupConfig() {
        try {
            val modelName = ZoyaSettings.getGeminiModel(this)
            val voiceName = ZoyaSettings.getGeminiVoice(this)
            val systemInstrText = ZoyaSettings.compileSystemInstruction(this)

            Log.d("ZoyaService", "Setting model: $modelName, voice: $voiceName")

            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", systemInstrText)
                        }))
                    })
                    // Enable Real-time Transcript Outputs from the server
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())

                    // Declare Tools
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "openApp")
                                put("description", "Launch a specified app on the phone by its packagename.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("packageName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Lower case package name like 'com.google.android.youtube' or 'com.android.calculator2'")
                                        })
                                    })
                                    put("required", JSONArray().put("packageName"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "searchAndCallContact")
                                put("description", "Search for a contact's phone number by name and immediately auto-dial.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contactName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Name of the person from address book to search and call")
                                        })
                                    })
                                    put("required", JSONArray().put("contactName"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "sendWhatsAppMessage")
                                put("description", "Locate a contact and launch WhatsApp with a prefilled text message.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contactName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Contact person's name")
                                        })
                                        put("message", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The pre-filled text message content")
                                        })
                                    })
                                    put("required", JSONArray().put("contactName").put("message"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "sendGmail")
                                put("description", "Open Gmail app targeting recipient, subject and body prefilled.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("recipientEmail", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Recipient email address")
                                        })
                                        put("subject", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Subject line")
                                        })
                                        put("body", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Email body text content")
                                        })
                                    })
                                    put("required", JSONArray().put("recipientEmail").put("subject").put("body"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "executeSystemAutomation")
                                put("description", "Execute standard system gestures and automation commands. Used to go home, go back, scroll the screen or click somewhere.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("actionType", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Available action choices: 'HOME', 'BACK', 'NOTIFICATIONS', 'QUICK_SETTINGS', 'RECENTS', 'POWER_DIALOG', 'SCROLL', 'CLICK'")
                                        })
                                        put("scrollDirection", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Direction to scroll. Required only if actionType is 'SCROLL'. Options: 'UP', 'DOWN', 'LEFT', 'RIGHT'")
                                        })
                                        put("clickPercentX", JSONObject().apply {
                                            put("type", "NUMBER")
                                            put("description", "Horizontal coordinate as a percentage from 0.0 (left) to 1.0 (right). Required only if actionType is 'CLICK'")
                                        })
                                        put("clickPercentY", JSONObject().apply {
                                            put("type", "NUMBER")
                                            put("description", "Vertical coordinate as a percentage from 0.0 (top) to 1.0 (bottom). Required only if actionType is 'CLICK'")
                                        })
                                    })
                                    put("required", JSONArray().put("actionType"))
                                })
                            })
                        })
                    }))
                })
            }

            webSocket?.send(setupJson.toString())
            Log.d("ZoyaService", "WS Sent setup payload successfully.")
        } catch (e: Exception) {
            Log.e("ZoyaService", "Failed to compile/send Setup: ${e.message}")
        }
    }

    private fun handleWebSocketMessage(rawMsg: String) {
        try {
            val root = JSONObject(rawMsg)
            
            // Check for transcription of user input and assistant output
            val inputTransText = root.optJSONObject("inputTranscription")?.optString("text")
                ?: root.optJSONObject("serverContent")?.optJSONObject("inputTranscription")?.optString("text")
            if (!inputTransText.isNullOrEmpty()) {
                ZoyaSessionManager.addChatMessage(inputTransText, isUser = true)
                checkForSpeechEmergency(inputTransText)
            }

            val outputTransText = root.optJSONObject("outputTranscription")?.optString("text")
                ?: root.optJSONObject("serverContent")?.optJSONObject("outputTranscription")?.optString("text")
            if (!outputTransText.isNullOrEmpty()) {
                ZoyaSessionManager.addChatMessage(outputTransText, isUser = false)
            }

            // Check if setup is fully completed from server, then we send the Vocal greeting!
            if (root.has("setupComplete")) {
                Log.d("ZoyaService", "Session setup confirmed by Gemini server! Triggering vocal greeting.")
                val greeting = ZoyaSettings.compileGreeting(this)
                sendClientText(greeting)
                ZoyaSessionManager.addChatMessage(greeting, isUser = false)
            }

            // 1. Analyze serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                
                // Track if conversation was interrupted
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d("ZoyaService", "Zoya's playback was user-interrupted! Purging playback.")
                    interruptPlayback()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            
                            // Check for textual updates / transcripts
                            if (part.has("text")) {
                                val text = part.getString("text")
                                ZoyaSessionManager.updateStatusLabel("Zoya: \"$text\"")
                                updateNotification("Zoya: $text")
                            }

                            // Check and render synthetic audio output chunks
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataStr = inlineData.getString("data")
                                val audioBytes = Base64.decode(dataStr, Base64.NO_WRAP)
                                
                                // Push base64 decoded raw PCM audio bytes to the playback thread
                                ZoyaSessionManager.updateState(ZoyaState.SPEAKING)
                                playbackQueue.put(audioBytes)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d("ZoyaService", "Model Turn complete.")
                    // If speaking is done, state goes back to LISTENING for next inputs
                    scope.launch {
                        delay(500)
                        if (playbackQueue.isEmpty() && !isPlaying) {
                            ZoyaSessionManager.updateState(ZoyaState.LISTENING)
                            ZoyaSessionManager.updateStatusLabel("Listening to you...")
                        }
                    }
                }
            }

            // 2. Analyze toolCall requests
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    ZoyaSessionManager.updateState(ZoyaState.THINKING)
                    ZoyaSessionManager.updateStatusLabel("Zoya is doing device operations...")

                    for (i in 0 until functionCalls.length()) {
                        val fn = functionCalls.getJSONObject(i)
                        val name = fn.getString("name")
                        val id = fn.getString("id")
                        val args = fn.optJSONObject("args") ?: JSONObject()

                        Log.d("ZoyaService", "Gemini requested tool execution: $name id: $id")
                        
                        // Execute tool call natively
                        val toolResult = toolEngine?.executeTool(name, args) ?: "{\"success\":false}"
                        
                        // Send callback back to websocket
                        sendToolResponse(id, name, toolResult)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("ZoyaService", "WS JSON Parse Error: ${e.message}")
        }
    }

    private fun sendClientText(text: String) {
        try {
            val payload = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", text)
                        }))
                    }))
                    put("turnComplete", true)
                })
            }
            webSocket?.send(payload.toString())
            Log.d("ZoyaService", "Sent client text successfully: $text")
        } catch (e: Exception) {
            Log.e("ZoyaService", "Error sending client text: ${e.message}")
        }
    }

    private fun sendToolResponse(callId: String, name: String, resultStringJson: String) {
        try {
            val responseJson = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().put(JSONObject().apply {
                        put("id", callId)
                        put("name", name)
                        put("response", JSONObject().apply {
                            put("output", JSONObject(resultStringJson))
                        })
                    }))
                })
            }
            webSocket?.send(responseJson.toString())
            Log.d("ZoyaService", "Sent functionResponse back successfully.")
        } catch (e: Exception) {
            Log.e("ZoyaService", "Error sending function response: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startActiveAudioSession() {
        isRecordingSession = true
        isPlaying = true

        // 1. Setup Playback Thread (AudioTrack - 24000Hz, Mono, 16-bit)
        val minPlayBufferSize = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minPlayBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isPlaying) {
                try {
                    val pcmBytes = playbackQueue.take()
                    
                    // Render RMS values of output voice to animate Zoya waves
                    var peakSum = 0.0
                    val shortCount = pcmBytes.size / 2
                    for (k in 0 until shortCount) {
                        val sample = ((pcmBytes[k * 2].toInt() and 0xFF) or (pcmBytes[k * 2 + 1].toInt() shl 8)).toShort()
                        peakSum += sample * sample
                    }
                    val outputRms = sqrt(peakSum / shortCount.coerceAtLeast(1))
                    ZoyaSessionManager.updateZoyaAudioLevel((outputRms / 8000f).toFloat().coerceIn(0f, 1f))

                    audioTrack?.write(pcmBytes, 0, pcmBytes.size)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Setup Recording Thread (AudioRecord - 16000Hz, Mono, 16-bit PCM)
        val minRecordBufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recordBufferSize = (minRecordBufferSize * 2).coerceAtLeast(2048)
        
        sessionAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferSize
        )

        if (sessionAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("ZoyaService", "Session record init failed.")
            return
        }

        sessionAudioRecord?.startRecording()
        ZoyaSessionManager.updateStatusLabel("Listening to you...")

        sessionRecordJob = scope.launch(Dispatchers.IO) {
            val recordBuffer = ByteArray(1024)
            while (isRecordingSession) {
                val readSize = sessionAudioRecord?.read(recordBuffer, 0, recordBuffer.size) ?: 0
                if (readSize > 0) {
                    
                    // Audio amplitude modeling of speaking user in session
                    var recordSum = 0.0
                    val shorts = readSize / 2
                    for (i in 0 until shorts) {
                        val samp = ((recordBuffer[i * 2].toInt() and 0xFF) or (recordBuffer[i * 2 + 1].toInt() shl 8)).toShort()
                        recordSum += samp * samp
                    }
                    val inputRms = sqrt(recordSum / shorts.coerceAtLeast(1))
                    ZoyaSessionManager.updateUserAudioLevel((inputRms / 8000f).toFloat().coerceIn(0f, 1f))

                    // IF user started speaking while speaking, trigger Local Interruption
                    if (inputRms > 2000.0 && ZoyaSessionManager.currentState.value == ZoyaState.SPEAKING) {
                        Log.d("ZoyaService", "Local interruption triggered by user voicing!")
                        interruptPlayback()
                    }

                    // Convert raw mic bytes to Base64 data chunks
                    val base64Data = Base64.encodeToString(recordBuffer, 0, readSize, Base64.NO_WRAP)
                    
                    val inputPayload = JSONObject().apply {
                        put("realtimeInput", JSONObject().apply {
                            put("mediaChunks", JSONArray().put(JSONObject().apply {
                                put("mimeType", "audio/pcm")
                                put("data", base64Data)
                            }))
                        })
                    }

                    try {
                        webSocket?.send(inputPayload.toString())
                    } catch (e: Exception) {
                        Log.e("ZoyaService", "Error streaming audio chunk: ${e.message}")
                    }
                }
                delay(20)
            }
        }
    }

    private fun interruptPlayback() {
        playbackQueue.clear()
        ZoyaSessionManager.updateZoyaAudioLevel(0f)
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.pause()
                    it.flush()
                    it.play()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ZoyaSessionManager.updateState(ZoyaState.LISTENING)
    }

    private fun stopActiveAudioSession() {
        isRecordingSession = false
        isPlaying = false

        sessionRecordJob?.cancel()
        sessionRecordJob = null
        playbackJob?.cancel()
        playbackJob = null

        playbackQueue.clear()

        try {
            sessionAudioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sessionAudioRecord = null

        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }

    /**
     * Terminates the active session and gracefully resets Zoya in background.
     */
    fun endActiveSession() {
        webSocket?.close(1000, "Done Conversing")
        webSocket = null
        stopActiveAudioSession()
        ZoyaSessionManager.updateState(ZoyaState.IDLE)
        startWakeWordListening()
    }

    private fun stopServiceSelf() {
        stopWakeWordListening()
        stopActiveAudioSession()
        webSocket?.close(1000, "Shutting down service.")
        webSocket = null
        ZoyaSessionManager.updateServiceRunning(false)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController?.stop()
        overlayController = null
        stopWakeWordListening()
        stopActiveAudioSession()
        scope.cancel()
        ZoyaSessionManager.updateServiceRunning(false)
    }

    // --- Emergency Speech triggers Parser logic ---
    private fun checkForSpeechEmergency(text: String) {
        val lower = text.lowercase()
        val emergencies = listOf("emergency", "madad", "help", "bachao", "help me", "save me", "koi madad karo", "mujhe bachao", "emergency message")
        if (emergencies.any { lower.contains(it) }) {
            Log.d("ZoyaService", "Speech emergency trigger detected in transcript: $text")
            triggerEmergencyProtocol()
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerEmergencyProtocol() {
        Log.d("ZoyaService", "Emergency trigger dispatched!")
        val contacts = ZoyaSettings.getEmergencyContacts(this)
        if (contacts.isEmpty()) {
            ZoyaSessionManager.updateStatusLabel("SOS Active! But no emergency contacts set.")
            ZoyaSessionManager.addChatMessage("SOS Triggered! None emergency contacts defined.", isUser = false)
            return
        }

        ZoyaSessionManager.updateStatusLabel("SOS Active! Querying GPS location coordinates...")
        
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var locationSent = false

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!locationSent) {
                    locationSent = true
                    sendEmergencySmsToContacts(contacts, location.latitude, location.longitude)
                    locationManager.removeUpdates(this)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }
                
                // Fetch location updates instantly
                locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)
                
                // Instant Check Last Known Cache
                val lastKnown = locationManager.getLastKnownLocation(provider) ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (lastKnown != null && !locationSent) {
                    locationSent = true
                    sendEmergencySmsToContacts(contacts, lastKnown.latitude, lastKnown.longitude)
                    locationManager.removeUpdates(locationListener)
                }
                
                // Safety deadline timer: If satellite lock takes > 6 seconds, blast details immediately without further lag!
                scope.launch {
                    delay(6000)
                    if (!locationSent) {
                        locationSent = true
                        locationManager.removeUpdates(locationListener)
                        sendEmergencySmsToContacts(contacts, null, null)
                    }
                }
            } else {
                // Instantly send alert without coordinate parameters
                sendEmergencySmsToContacts(contacts, null, null)
            }
        } catch (e: Exception) {
            Log.e("ZoyaService", "Error resolving locations: ${e.message}")
            sendEmergencySmsToContacts(contacts, null, null)
        }
    }

    private fun sendEmergencySmsToContacts(contacts: List<EmergencyContact>, lat: Double?, lon: Double?) {
        val locationString = if (lat != null && lon != null) {
            "https://maps.google.com/?q=$lat,$lon"
        } else {
            "Location Unavailable"
        }
        
        val message = "EMERGENCY ALERT SOS! Please help me. My current live GPS coordinates location is: $locationString"

        var sentSuccess = 0
        for (recipient in contacts) {
            try {
                val cleanPhoneNum = recipient.number.replace(" ", "").trim()
                if (cleanPhoneNum.isNotEmpty()) {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(cleanPhoneNum, null, message, null, null)
                    sentSuccess++
                }
            } catch (e: Exception) {
                Log.e("ZoyaService", "Failed to send SMS text: ${e.message}")
            }
        }

        val confirmStatusText = if (sentSuccess > 0) {
            "SOS Emergency dispatches successfully blasted to $sentSuccess contacts!"
        } else {
            "Emergency SOS activated! (Failed to deliver via SMS text)"
        }
        ZoyaSessionManager.updateStatusLabel(confirmStatusText)
        ZoyaSessionManager.addChatMessage(confirmStatusText, isUser = false)
    }

    // --- Notification Management ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zoya Background Active State",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for running real-time sassy voice assistant Zoya"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ZoyaForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val awakeIntent = Intent(this, ZoyaForegroundService::class.java).apply { action = ACTION_AWAKE }
        val awakePending = PendingIntent.getService(this, 2, awakeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zoya Sassy Assistant")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(mainPending)
            .addAction(android.R.drawable.ic_media_play, "Wake Zoya", awakePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kill Zoya", stopPending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "zoya_assistant_channel"
        const val NOTIFICATION_ID = 4843

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_AWAKE = "com.example.action.AWAKE"
    }
}
