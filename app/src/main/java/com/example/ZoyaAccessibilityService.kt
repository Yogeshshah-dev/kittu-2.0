package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class ZoyaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ZoyaAccessibility", "Zoya Accessibility Service Connected!")
        instance = this
        ZoyaSessionManager.updateAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No action needed for events. We only use this service to execute programmatic commands
    }

    override fun onInterrupt() {
        Log.d("ZoyaAccessibility", "Service Interrupted")
        ZoyaSessionManager.updateAccessibilityConnected(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        ZoyaSessionManager.updateAccessibilityConnected(false)
    }

    fun performGlobalActionWrapper(action: Int): Boolean {
        Log.d("ZoyaAccessibility", "Performing global action: $action")
        return performGlobalAction(action)
    }

    fun performClickAt(x: Float, y: Float): Boolean {
        Log.d("ZoyaAccessibility", "Performing click at ($x, $y)")
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 50L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun performScroll(direction: String): Boolean {
        Log.d("ZoyaAccessibility", "Performing scroll: $direction")
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2
        val startY = height / 2
        var endX = width / 2
        var endY = height / 2

        when (direction.lowercase()) {
            "down" -> endY = height * 0.25f // Swipe up to scroll down
            "up" -> endY = height * 0.75f  // Swipe down to scroll up
            "left" -> endX = width * 0.25f // Swipe right to left
            "right" -> endX = width * 0.75f // Swipe left to right
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 300L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: ZoyaAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
