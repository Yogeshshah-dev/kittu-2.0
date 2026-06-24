package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ZoyaState {
    IDLE,        // Slow breathing glow
    LISTENING,   // Active listening waveform responding to mic input frequency
    THINKING,    // Pulsing neon ring
    SPEAKING     // Dynamic audio wave matching Zoya's output stream
}

data class ZoyaChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object ZoyaSessionManager {
    private val _currentState = MutableStateFlow(ZoyaState.IDLE)
    val currentState = _currentState.asStateFlow()

    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel = _userAudioLevel.asStateFlow()

    private val _zoyaAudioLevel = MutableStateFlow(0f)
    val zoyaAudioLevel = _zoyaAudioLevel.asStateFlow()

    private val _statusLabel = MutableStateFlow("Zoya is standard-by...")
    val statusLabel = _statusLabel.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _isWakeWordListening = MutableStateFlow(false)
    val isWakeWordListening = _isWakeWordListening.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected = _isAccessibilityConnected.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ZoyaChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    fun updateState(state: ZoyaState) {
        _currentState.value = state
    }

    fun updateAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun updateUserAudioLevel(level: Float) {
        _userAudioLevel.value = level
    }

    fun updateZoyaAudioLevel(level: Float) {
        _zoyaAudioLevel.value = level
    }

    fun updateStatusLabel(label: String) {
        _statusLabel.value = label
    }

    fun updateServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateWakeWordListening(listening: Boolean) {
        _isWakeWordListening.value = listening
    }

    fun addChatMessage(text: String, isUser: Boolean) {
        val current = _chatMessages.value.toMutableList()
        // Simple deduplication for assistant messages
        if (!isUser && current.isNotEmpty() && !current.last().isUser && current.last().text == text) {
            return
        }
        current.add(ZoyaChatMessage(text, isUser))
        _chatMessages.value = current
    }

    fun clearChatMessages() {
        _chatMessages.value = emptyList()
    }

    // Helper sassy phrases that Zoya says depending on tool executions
    val sassPhrases = listOf(
        "Oh, you want me to do that? Fine, here you go.",
        "As you wish, master. Placing the call now.",
        "WhatsApp messenger opened. Don't say anything embarrassing.",
        "Launch complete. Try not to break anything.",
        "Gmail ready. Write something witty, okay?",
        "Look at me, doing everything for you. You're welcome!"
    )

    fun getRandomSass(): String {
        return sassPhrases.random()
    }
}
