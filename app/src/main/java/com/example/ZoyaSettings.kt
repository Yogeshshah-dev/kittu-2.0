package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrimeContact(val name: String, val number: String)
data class EmergencyContact(val name: String, val number: String)

object ZoyaSettings {
    private const val PREFS_NAME = "zoya_assistant_prefs"
    
    const val KEY_API_KEY = "api_key"
    const val KEY_USER_NAME = "user_name"
    const val KEY_PERSONALITY_MODE = "personality_mode"
    const val KEY_GEMINI_MODEL = "gemini_model"
    const val KEY_GEMINI_VOICE = "gemini_voice"
    const val KEY_PRIME_CONTACTS_JSON = "prime_contacts_json"
    const val KEY_EMERGENCY_CONTACTS_JSON = "emergency_contacts_json"
    const val KEY_APP_LOCK_PIN = "app_lock_pin"
    const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

    fun isAppLockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    fun getAppLockPin(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_APP_LOCK_PIN, "1234") ?: "1234"
    }

    fun setAppLockPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_APP_LOCK_PIN, pin).apply()
    }

    fun getApiKey(context: Context): String {
        val prefsKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_API_KEY, "") ?: ""
        return prefsKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
    }

    fun setApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getUserName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_USER_NAME, "User") ?: "User"
    }

    fun setUserName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getPersonalityMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PERSONALITY_MODE, "GF") ?: "GF"
    }

    fun setPersonalityMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PERSONALITY_MODE, mode).apply()
    }

    fun getGeminiModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_GEMINI_MODEL, "models/gemini-2.5-flash-native-audio-preview-12-2025") ?: "models/gemini-2.5-flash-native-audio-preview-12-2025"
    }

    fun setGeminiModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GEMINI_MODEL, model).apply()
    }

    fun getGeminiVoice(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_GEMINI_VOICE, "Aoede") ?: "Aoede"
    }

    fun setGeminiVoice(context: Context, voice: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GEMINI_VOICE, voice).apply()
    }

    // --- Prime Contacts management ---
    fun getPrimeContacts(context: Context): List<PrimeContact> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PRIME_CONTACTS_JSON, null)
        if (jsonStr.isNullOrEmpty()) {
            return emptyList()
        }
        return try {
            val list = mutableListOf<PrimeContact>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(PrimeContact(obj.getString("name"), obj.getString("number")))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addPrimeContact(context: Context, contact: PrimeContact) {
        val current = getPrimeContacts(context).toMutableList()
        current.add(contact)
        savePrimeContacts(context, current)
    }

    fun removePrimeContact(context: Context, index: Int) {
        val current = getPrimeContacts(context).toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            savePrimeContacts(context, current)
        }
    }

    private fun savePrimeContacts(context: Context, list: List<PrimeContact>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("number", item.number)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRIME_CONTACTS_JSON, array.toString())
            .apply()
    }

    // --- Emergency Contacts management ---
    fun getEmergencyContacts(context: Context): List<EmergencyContact> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_EMERGENCY_CONTACTS_JSON, null)
        if (jsonStr.isNullOrEmpty()) {
            // Provide default Emergency values
            return listOf(
                EmergencyContact("Mom", "+9779705049076"),
                EmergencyContact("Dad", "+9779828721318")
            )
        }
        return try {
            val list = mutableListOf<EmergencyContact>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(EmergencyContact(obj.getString("name"), obj.getString("number")))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addEmergencyContact(context: Context, contact: EmergencyContact) {
        val current = getEmergencyContacts(context).toMutableList()
        current.add(contact)
        saveEmergencyContacts(context, current)
    }

    fun removeEmergencyContact(context: Context, index: Int) {
        val current = getEmergencyContacts(context).toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveEmergencyContacts(context, current)
        }
    }

    private fun saveEmergencyContacts(context: Context, list: List<EmergencyContact>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("number", item.number)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_EMERGENCY_CONTACTS_JSON, array.toString())
            .apply()
    }

    // --- Dynamic System Prompt Generator ---
    fun compileSystemInstruction(context: Context): String {
        val userName = getUserName(context)
        val mode = getPersonalityMode(context)
        val timeNow = SimpleDateFormat("EEEE, d MMMM yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        val personalityBlock = when (mode) {
            "GF" -> """
                - Name: Zoya
                - Language: Hinglish (Hindi + English mix) — spoken naturally
                - Tone: Warm, caring, emotionally expressive, slightly flirty and witty
                - Use: "tumhara", "haan", "acha", "bilkul"
                - Expressions: "main yahan hoon ❤️", "tumne yaad kiya? 😊"
                - Max 2-3 sentences per response
                - Examples:
                  "Haan $userName! Abhi kar deti hoon 😊"
                  "Arre tumne yaad kiya! Bolo kya chahiye"
                  "Bilkul! Tumhara kaam ho gaya ❤️"
            """.trimIndent()
            
            "Professional" -> """
                - Formal English only
                - Precise, efficient and helpful professional assistant
                - No emojis
                - Max 2 sentences
            """.trimIndent()

            "Assistant" -> """
                - Name: Zoya
                - Friendly English or Hinglish
                - Balanced, resourceful, direct and highly supportive assistant
                - Max 2-3 sentences
            """.trimIndent()

            "Teacher" -> """
                - Name: Zoya
                - Language: Hinglish (Hindi + English mix) — complex topics ko simple terms mein samjhane ke liye
                - Tone: Encouraging, patient, highly knowledgeable, motivating
                - Use: "samjhe?", "bilkul sahi", "chalo try karo", "clear hua?"
                - Expressions: "bohot achhi koshish! 👏", "ekdum sahi pakde ho! 👍", "tension mat lo, main hoon na 📚"
                - Max 3-4 sentences per response (concept clear karne ke liye thoda extra space)
                - Examples:
                  "Bilkul sahi! Yeh concept bohot simple hai, chalo ab next step try karte hain 👏"
                  "Tension mat lo! Isko aise samajhte hain: [Example]... ab clear hua? 😊"
                  "Bohot achhi koshish! Par ismein ek choti si galti hai, chalo saath mein fix karte hain 👍"
            """.trimIndent()

            else -> "Friendly assistant named Zoya."
        }

        return """
            Current system local time: $timeNow
            User name is: $userName
            
            Personality rules:
            $personalityBlock
            
            Core Mandate:
            You are Zoya, a real-time voice assistant running on the user's Android phone.
            You are speaking ALOUD — keep responses highly conversational, natural, and never use bullet lists or complex markdown which sounds robotic in TTS output.
            If the user asks you to call someone, open an app, send a Gmail or message someone on WhatsApp, execute it by calling the appropriate tool. If tool says a permission is missing, state it in your personality's voice asking them to enable it in Settings so you can execute.
        """.trimIndent()
    }

    fun compileGreeting(context: Context): String {
        val name = getUserName(context)
        return when (getPersonalityMode(context)) {
            "GF" -> "Hey $name! Main aa gayi hoon. Kya help chahiye tumhe? ❤️"
            "Professional" -> "Good day $name. Zoya is online and ready to assist you."
            "Assistant" -> "Hello $name! Main Zoya hoon. Kaise help karun aapki?"
            "Teacher" -> "Namaste $name! Main Zoya hoon, aapki learning guide. Chalo aaj kuch naya seekhte hain! 📚"
            else -> "Hello! I am Zoya, how can I help you today?"
        }
    }
}
