package com.example

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.ZoyaVoiceOrb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ZoyaOverlayController(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = mViewModelStore
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateJob: Job? = null

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun startListeningToState() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stateJob = coroutineScope.launch {
            ZoyaSessionManager.currentState.collectLatest { state ->
                if (state != ZoyaState.IDLE) {
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }
    }

    fun stop() {
        stateJob?.cancel()
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun showOverlay() {
        if (overlayView != null) return

        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.e("ZoyaOverlay", "Cannot draw overlays: SYSTEM_ALERT_WINDOW permission is missing.")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120 // elevated safely above system navigation keys
        }

        val frame = FrameLayout(context).apply {
            setViewTreeLifecycleOwner(this@ZoyaOverlayController)
            setViewTreeViewModelStoreOwner(this@ZoyaOverlayController)
            setViewTreeSavedStateRegistryOwner(this@ZoyaOverlayController)
        }

        val composeView = ComposeView(context).apply {
            setContent {
                ZoyaOverlayBubbleView(onClose = {
                    hideOverlay()
                    // Send intent to gracefully terminate active live audio websocket
                    val intent = android.content.Intent(context, ZoyaForegroundService::class.java).apply {
                        action = "com.example.action.STOP_ACTIVE_ONLY" // Custom action to restore stand-by word listening
                    }
                    context.startService(intent)
                })
            }
        }

        frame.addView(composeView)
        try {
            windowManager.addView(frame, params)
            overlayView = frame
            Log.d("ZoyaOverlay", "Overlay window added successfully.")
        } catch (e: Exception) {
            Log.e("ZoyaOverlay", "Error adding overlay window: ${e.message}")
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
                Log.d("ZoyaOverlay", "Overlay window removed.")
            } catch (e: Exception) {
                Log.e("ZoyaOverlay", "Error removing overlay window: ${e.message}")
            }
            overlayView = null
        }
    }
}

@Composable
fun ZoyaOverlayBubbleView(onClose: () -> Unit) {
    val zoyaState by ZoyaSessionManager.currentState.collectAsState()
    val statusLabel by ZoyaSessionManager.statusLabel.collectAsState()
    val userLevel by ZoyaSessionManager.userAudioLevel.collectAsState()
    val zoyaLevel by ZoyaSessionManager.zoyaAudioLevel.collectAsState()

    val colorPrimary = when (zoyaState) {
        ZoyaState.IDLE -> Color(0xFFFF1744)
        ZoyaState.LISTENING -> Color(0xFF00E676)
        ZoyaState.THINKING -> Color(0xFF00B0FF)
        ZoyaState.SPEAKING -> Color(0xFFD500F9)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colorPrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xF20B0B0C)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dynamic Glowing Voice Assistant Orb Structure (Watermark-Free, fully vector programmatic rendering)
                ZoyaVoiceOrb(
                    zoyaState = zoyaState,
                    audioLevel = if (zoyaState == ZoyaState.SPEAKING) zoyaLevel else userLevel,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Mid Content transcript
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 64.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "ZOYA ASSISTANT ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        color = Color.White,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Power Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color(0xFF221114), CircleShape)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close session",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
