package com.example

import android.animation.TimeAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.ZoyaVoiceOrb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ZoyaRootApp()
            }
        }
    }
}

@Composable
fun ZoyaRootApp() {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(hasAllRequiredPermissions(context)) }
    var isUnlocked by remember { mutableStateOf(!ZoyaSettings.isAppLockEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_scaffold_root"),
        containerColor = Color(0xFF050505) // Ultra premium pitch-black canvas
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasPermissions) {
                if (isUnlocked) {
                    ZoyaSplitDashboard()
                } else {
                    ZoyaPinLockOverlay(onUnlock = { isUnlocked = true })
                }
            } else {
                ZoyaOnboardingScreen(
                    onRequestPermissions = {
                        val requestList = mutableListOf(
                            android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.READ_CONTACTS,
                            android.Manifest.permission.CALL_PHONE,
                            android.Manifest.permission.SEND_SMS,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestList.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(requestList.toTypedArray())
                    }
                )
            }
        }
    }
}

fun hasAllRequiredPermissions(context: Context): Boolean {
    val list = mutableListOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun ZoyaOnboardingScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Neon Security Emblem
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF1744).copy(alpha = 0.25f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Zoya Security Logo",
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Initiating Zoya SOS Assistant",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.testTag("onboarding_title"),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Zoya requires a suite of native host permissions to enable smart automation calling, messaging, and responsive Emergency coordinates GPS retrieval.",
            color = Color(0xFF888888),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Onboarding Check cards
        PermissionItemCard(
            title = "Voice Input Recognition (Mic)",
            desc = "Allows Zoya to hear and process seamless speech commands.",
            icon = Icons.Default.Mic,
            accentColor = Color(0xFFFF1744)
        )

        Spacer(modifier = Modifier.height(10.dp))

        PermissionItemCard(
            title = "Auto Calling & Contacts",
            desc = "Required to search contacts and dial phone numbers natively.",
            icon = Icons.Default.Phone,
            accentColor = Color(0xFFD500F9)
        )

        Spacer(modifier = Modifier.height(10.dp))

        PermissionItemCard(
            title = "SMS Deliveries & Coordinates GPS",
            desc = "Required for automatic SMS sending and GPS resolution on Emergency triggers.",
            icon = Icons.Default.GpsFixed,
            accentColor = Color(0xFF00E676)
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(58.dp)
                .testTag("grant_permissions_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF1744)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = "GRANT PERMISSIONS",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ZoyaSplitDashboard() {
    var selectedTab by remember { mutableStateOf(0) } // 0 -> Console, 1 -> Settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // 1. Customized Segmented Switcher Header Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(Color(0xFF111111), RoundedCornerShape(24.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selectedTab == 0) Color(0xFFFF1744) else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONSOLE ORB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 0) Color.White else Color(0xFF888888)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selectedTab == 1) Color(0xFFFF1744) else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SET_PARAMETERS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 1) Color.White else Color(0xFF888888)
                )
            }
        }

        // 2. Animated Screen Switcher content panel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedTab == 0) {
                ZoyaConsoleScreen()
            } else {
                ZoyaSettingsScreen()
            }
        }
    }
}

@Composable
fun ZoyaConsoleScreen() {
    val context = LocalContext.current
    
    // State indicators
    val zoyaState by ZoyaSessionManager.currentState.collectAsState()
    val userLevel by ZoyaSessionManager.userAudioLevel.collectAsState()
    val zoyaLevel by ZoyaSessionManager.zoyaAudioLevel.collectAsState()
    val labelText by ZoyaSessionManager.statusLabel.collectAsState()
    val serviceIsRunning by ZoyaSessionManager.isServiceRunning.collectAsState()
    val chatMessages by ZoyaSessionManager.chatMessages.collectAsState()

    // Battery & Clock States
    var timeString by remember { mutableStateOf("") }
    var batteryPercent by remember { mutableStateOf("100%") }

    LaunchedEffect(Unit) {
        while (true) {
            timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    // Auto-Starting Foreground service if needed
    LaunchedEffect(Unit) {
        if (!serviceIsRunning) {
            val serviceIntent = Intent(context, ZoyaForegroundService::class.java).apply {
                action = ZoyaForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // High Contrast Top Info Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ZOYA LIVE ENGINE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (serviceIsRunning) Color(0xFF00E676) else Color(0xFFFF1744), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (serviceIsRunning) "Agent Active" else "Agent Offline",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Tech stats and Power actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeString,
                    color = Color(0xFFFF1744),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 12.dp)
                )

                IconButton(
                    onClick = {
                        val stopIntent = Intent(context, ZoyaForegroundService::class.java).apply {
                            action = ZoyaForegroundService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    },
                    modifier = Modifier
                        .background(Color(0xFF1E1114), CircleShape)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power Kill",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live scrolling Audio Chat Dialog transcript logs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .background(Color(0xFF0A0A0A), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF161616), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Empty Chat",
                            tint = Color(0xFF333333),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transcribing messages yet.\nTap Orb and initiate speech conversation.",
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = Color(0xFF555555)
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(chatMessages.size) {
                    if (chatMessages.isNotEmpty()) {
                        listState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .background(if (msg.isUser) Color(0xFF1C0D11) else Color(0xFF111111))
                                    .border(
                                        width = 1.dp,
                                        color = if (msg.isUser) Color(0xFFFF1744).copy(alpha = 0.5f) else Color(0xFF222222),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) Color.White else Color(0xFFEEEEEE),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Sassy Neon Orb container
        Box(
            modifier = Modifier
                .size(200.dp)
                .clickable {
                    if (serviceIsRunning) {
                        if (zoyaState == ZoyaState.IDLE) {
                            val awakeIntent = Intent(context, ZoyaForegroundService::class.java).apply {
                                action = ZoyaForegroundService.ACTION_AWAKE
                            }
                            context.startService(awakeIntent)
                        } else {
                            val resetIntent = Intent(context, ZoyaForegroundService::class.java).apply {
                                action = ZoyaForegroundService.ACTION_START
                            }
                            context.startService(resetIntent)
                        }
                    }
                }
                .testTag("zoya_orb_container"),
            contentAlignment = Alignment.Center
        ) {
            ZoyaVoiceOrb(
                zoyaState = zoyaState,
                audioLevel = if (zoyaState == ZoyaState.SPEAKING) zoyaLevel else userLevel,
                modifier = Modifier.fillMaxSize(),
                orbSize = 180.dp
            )
        }

        // Live dynamic voice state parameter label
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F14)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E1E24))
        ) {
            Text(
                text = labelText,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        }

        // Quick Command Launcher Templates
        Text(
            text = "ZOYA AUTOMATION TEMPLATES",
            color = Color(0xFF555555),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChipItem("Call Mom", "Zoya, call Mom")
            SuggestionChipItem("Emergency SOS", "Zoya, emergency help!")
            SuggestionChipItem("Open YouTube", "Zoya, open YouTube")
            SuggestionChipItem("Gmail recipient", "Zoya, email my tech admin")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoyaSettingsScreen() {
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }

    // Forms States
    var apiKeyText by remember { mutableStateOf(ZoyaSettings.getApiKey(context)) }
    var userNameText by remember { mutableStateOf(ZoyaSettings.getUserName(context)) }
    var selectedModel by remember { mutableStateOf(ZoyaSettings.getGeminiModel(context)) }
    var selectedVoice by remember { mutableStateOf(ZoyaSettings.getGeminiVoice(context)) }
    var selectedMode by remember { mutableStateOf(ZoyaSettings.getPersonalityMode(context)) }

    var apiKeyHidden by remember { mutableStateOf(true) }

    // Lists States for Contacts
    var primeContactsList by remember { mutableStateOf(ZoyaSettings.getPrimeContacts(context)) }
    var emergencyContactsList by remember { mutableStateOf(ZoyaSettings.getEmergencyContacts(context)) }

    // Dialog state controllers
    var showAddPrimeDialog by remember { mutableStateOf(false) }
    var showAddEmergencyDialog by remember { mutableStateOf(false) }

    // Helper Dialog Inputs
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "SYSTEM PARAMETERS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // API Key Input Card
        SettingsSectionCard(title = "Gemini Live API Key Link") {
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                label = { Text("API Key Link", color = Color(0xFF666666)) },
                visualTransformation = if (apiKeyHidden) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = { apiKeyHidden = !apiKeyHidden }) {
                        Icon(
                            imageVector = if (apiKeyHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Key",
                            tint = Color.White
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF1744),
                    unfocusedBorderColor = Color(0xFF333333)
                )
            )
            Text(
                text = "If left empty, system fallback binds automatically to active Secrets Panel securely.",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Username Card
        SettingsSectionCard(title = "Your Identification (Master Name)") {
            OutlinedTextField(
                value = userNameText,
                onValueChange = { userNameText = it },
                label = { Text("Name", color = Color(0xFF666666)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF1744),
                    unfocusedBorderColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Model Selector
        SettingsSectionCard(title = "Voice Generation Model API Stream") {
            val models = listOf(
                "models/gemini-2.5-flash-native-audio-preview-12-2025" to "Native Audio (Human Voice) [Default]",
                "models/gemini-2.0-flash-live-001" to "Flash Live (Fast)",
                "models/gemini-2.5-flash-preview-native-audio-dialog" to "Pro Audio Dialogue"
            )
            
            models.forEach { (modelId, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedModel == modelId) Color(0xFF140D0F) else Color.Transparent)
                        .clickable { selectedModel = modelId }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedModel == modelId),
                        onClick = { selectedModel = modelId },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF1744))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = modelId, color = Color(0xFF666666), fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Voice Selector dropdown
        SettingsSectionCard(title = "Acoustic Vocal Timbre (Voice Type)") {
            val voices = listOf("Aoede", "Charon", "Kore", "Fenrir", "Puck", "Leda", "Orus", "Zephyr")
            var dropdownExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Voice: $selectedVoice", fontSize = 14.sp)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Color(0xFF111111))
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(text = voice, color = Color.White) },
                            onClick = {
                                selectedVoice = voice
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Personality Option Selector
        SettingsSectionCard(title = "Zoya Persona & Narrative Tuning") {
            val styles = listOf(
                "GF" to "Warm, flirty & teasing (Hinglish ❤️)",
                "Assistant" to "Resourceful, friendly & balanced (Eng/Hin 🤖)",
                "Teacher" to "Encouraging, helpful guide (Simples topics 📚)",
                "Professional" to "Efficient, precise, direct and English 💼"
            )
            styles.forEach { (modeVal, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedMode == modeVal) Color(0xFF140D0F) else Color.Transparent)
                        .clickable { selectedMode = modeVal }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedMode == modeVal),
                        onClick = { selectedMode = modeVal },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF1744))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "$modeVal Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = desc, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Prime Contacts management
        SettingsSectionCard(title = "Prime Call Contacts List") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Contacts loaded: ${primeContactsList.size}", color = Color.White, fontSize = 13.sp)
                Button(
                    onClick = { showAddPrimeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "+ ADD", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            primeContactsList.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF0A0A0F), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF1A1A1F), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = item.number, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            ZoyaSettings.removePrimeContact(context, idx)
                            primeContactsList = ZoyaSettings.getPrimeContacts(context)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF1744), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Emergency SOS Contacts Management
        SettingsSectionCard(title = "Emergency SOS Contacts List") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Contacts loaded: ${emergencyContactsList.size}", color = Color.White, fontSize = 13.sp)
                Button(
                    onClick = { showAddEmergencyDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "+ ADD", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            emergencyContactsList.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF14080A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF221113), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = item.number, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            ZoyaSettings.removeEmergencyContact(context, idx)
                            emergencyContactsList = ZoyaSettings.getEmergencyContacts(context)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF1744), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Integration & Permissions Section
        SettingsSectionCard(title = "Device Integration & Secure Permissions") {
            // 1. Accessibility Service Link
            var isAccessibilityEnabled by remember { mutableStateOf(ZoyaAccessibilityService.isConnected()) }
            LaunchedEffect(Unit) {
                while (true) {
                    isAccessibilityEnabled = ZoyaAccessibilityService.isConnected()
                    delay(1000)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Accessibility Gestures", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = if (isAccessibilityEnabled) "Connected & active" else "Not turned on", color = if (isAccessibilityEnabled) Color(0xFF00E676) else Color(0xFFFF1744), fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open Settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibilityEnabled) Color(0xFF222222) else Color(0xFFFF1744)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isAccessibilityEnabled) "CONFIGURE" else "GRANT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))

            // 2. Overlay Permission
            var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            LaunchedEffect(Unit) {
                while (true) {
                    isOverlayEnabled = Settings.canDrawOverlays(context)
                    delay(1000)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "System Overlay Bubble", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = if (isOverlayEnabled) "Bubble overlay enabled" else "Bubble overlay disabled", color = if (isOverlayEnabled) Color(0xFF00E676) else Color(0xFFFF1744), fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isOverlayEnabled) Color(0xFF222222) else Color(0xFFFF1744)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isOverlayEnabled) "CONFIGURE" else "GRANT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))

            // 3. Digital Assistant Selection / Voice input role helper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Default Assistant Role", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Designate Zoya for power button long press triggers", color = Color(0xFF888888), fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent("android.settings.VOICE_INPUT_SETTINGS").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "Search 'Default Assist App' in Settings.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "BIND ASSIST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))

            // 4. PIN app locker security toggle
            var appLockEnabled by remember { mutableStateOf(ZoyaSettings.isAppLockEnabled(context)) }
            var appLockPin by remember { mutableStateOf(ZoyaSettings.getAppLockPin(context)) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "App Lock Protection PIN", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = if (appLockEnabled) "App locker active (PIN: $appLockPin)" else "App locker inactive", color = if (appLockEnabled) Color(0xFF00E676) else Color(0xFF888888), fontSize = 11.sp)
                }
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = {
                        appLockEnabled = it
                        ZoyaSettings.setAppLockEnabled(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF1744),
                        checkedTrackColor = Color(0xFF441113)
                    )
                )
            }

            if (appLockEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = appLockPin,
                    onValueChange = { newVal ->
                        if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                            appLockPin = newVal
                            ZoyaSettings.setAppLockPin(context, newVal)
                        }
                    },
                    label = { Text("App Locker 4-Digit PIN", color = Color(0xFF666666)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF1744),
                        unfocusedBorderColor = Color(0xFF333333)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Save Global Button
        Button(
            onClick = {
                isSaving = true
                coroutineScope.launch {
                    ZoyaSettings.setApiKey(context, apiKeyText)
                    ZoyaSettings.setUserName(context, userNameText)
                    ZoyaSettings.setGeminiModel(context, selectedModel)
                    ZoyaSettings.setGeminiVoice(context, selectedVoice)
                    ZoyaSettings.setPersonalityMode(context, selectedMode)
                    
                    delay(800)
                    isSaving = false
                    Toast.makeText(context, "Parameters applied! Restart session to sync.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = "SAVE & SYNC ENGINE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    // Modal Add Dialogs
    if (showAddPrimeDialog) {
        AddContactDialog(
            title = "Add Prime Call Contact",
            onDismiss = {
                showAddPrimeDialog = false
                nameInput = ""
                phoneInput = ""
            },
            onSave = { name, phone ->
                ZoyaSettings.addPrimeContact(context, PrimeContact(name, phone))
                primeContactsList = ZoyaSettings.getPrimeContacts(context)
                showAddPrimeDialog = false
                nameInput = ""
                phoneInput = ""
            }
        )
    }

    if (showAddEmergencyDialog) {
        AddContactDialog(
            title = "Add Emergency SOS Contact",
            onDismiss = {
                showAddEmergencyDialog = false
                nameInput = ""
                phoneInput = ""
            },
            onSave = { name, phone ->
                ZoyaSettings.addEmergencyContact(context, EmergencyContact(name, phone))
                emergencyContactsList = ZoyaSettings.getEmergencyContacts(context)
                showAddEmergencyDialog = false
                nameInput = ""
                phoneInput = ""
            }
        )
    }
}

@Composable
fun AddContactDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF1744),
                        unfocusedBorderColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (e.g. +91...)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF1744),
                        unfocusedBorderColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty() && phone.isNotEmpty()) onSave(name, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
            ) {
                Text(text = "Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF111116)
    )
}

@Composable
fun PermissionItemCard(
    title: String,
    desc: String,
    icon: ImageVector,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F14)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E1E24))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, color = Color(0xFF888888), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF16161C))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF1744),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SuggestionChipItem(label: String, promptText: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .background(Color(0xFF0F0F14), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E1E24), RoundedCornerShape(12.dp))
            .clickable {
                // Instantly notify service or perform action with prompt
                val serviceIntent = Intent(context, ZoyaForegroundService::class.java).apply {
                    action = ZoyaForegroundService.ACTION_AWAKE
                }
                context.startService(serviceIntent)
                Toast.makeText(context, "Say: \"$promptText\"", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Text(text = label, color = Color(0xFFFF1744), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "\"$promptText\"", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        }
    }
}

@Composable
fun ZoyaPinLockOverlay(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }
    val correctPin = ZoyaSettings.getAppLockPin(context)
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Lock Emblem
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (isError) Color(0xFFFF1744) else Color(0xFFD500F9)).copy(alpha = 0.23f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Secured Portal",
                tint = if (isError) Color(0xFFFF1744) else Color(0xFFD500F9),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Zoya Security Gate",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isError) "Incorrect PIN. Try again!" else "Enter 4-digit security PIN to launch console",
            fontSize = 13.sp,
            color = if (isError) Color(0xFFFF1744) else Color(0xFF888888)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Circles Indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (i < inputCode.length) {
                                if (isError) Color(0xFFFF1744) else Color(0xFFD500F9)
                            } else {
                                Color(0xFF222222)
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (i < inputCode.length) Color.Transparent else Color(0xFF444444),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Elegant Numerical keypad grid dial pad
        val numKeys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            numKeys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { digit ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF111115))
                                .clickable {
                                    isError = false
                                    when (digit) {
                                        "C" -> inputCode = ""
                                        "⌫" -> {
                                            if (inputCode.isNotEmpty()) {
                                                inputCode = inputCode.dropLast(1)
                                            }
                                        }
                                        else -> {
                                            if (inputCode.length < 4) {
                                                inputCode += digit
                                                if (inputCode.length == 4) {
                                                    if (inputCode == correctPin) {
                                                        onUnlock()
                                                    } else {
                                                        isError = true
                                                        inputCode = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .border(1.dp, Color(0xFF1E1E24), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                color = if (digit == "C" || digit == "⌫") Color(0xFFFF1744) else Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
