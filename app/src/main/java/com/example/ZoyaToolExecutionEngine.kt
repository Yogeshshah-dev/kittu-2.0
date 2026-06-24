package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONObject

class ZoyaToolExecutionEngine(private val context: Context) {

    /**
     * Executes tool calls and returns a JSON result feedback to send back to Gemini.
     */
    fun executeTool(name: String, args: JSONObject): String {
        return try {
            when (name) {
                "openApp" -> {
                    val packageName = args.optString("packageName")
                    if (packageName.isEmpty()) {
                        return createResultJson(false, "App package name was empty.")
                    }
                    val result = openApp(packageName)
                    createResultJson(result, if (result) "Successfully launched app $packageName" else "App $packageName is not installed on this device.")
                }
                "searchAndCallContact" -> {
                    val contactName = args.optString("contactName")
                    if (contactName.isEmpty()) {
                        return createResultJson(false, "Contact name was empty.")
                    }
                    val resultMessage = searchAndCallContact(contactName)
                    createResultJson(!resultMessage.contains("failed") && !resultMessage.contains("permission"), resultMessage)
                }
                "sendWhatsAppMessage" -> {
                    val contactName = args.optString("contactName")
                    val message = args.optString("message")
                    if (contactName.isEmpty()) {
                        return createResultJson(false, "Contact name was empty.")
                    }
                    val resultMessage = sendWhatsAppMessage(contactName, message)
                    createResultJson(!resultMessage.contains("failed") && !resultMessage.contains("permission"), resultMessage)
                }
                "sendGmail" -> {
                    val recipientEmail = args.optString("recipientEmail")
                    val subject = args.optString("subject")
                    val body = args.optString("body")
                    val result = sendGmail(recipientEmail, subject, body)
                    createResultJson(result, if (result) "Successfully pre-filled email to $recipientEmail" else "Failed to open Gmail composer.")
                }
                "executeSystemAutomation" -> {
                    val actionType = args.optString("actionType")
                    val scrollDir = args.optString("scrollDirection")
                    val clickX = args.optDouble("clickPercentX", -1.0)
                    val clickY = args.optDouble("clickPercentY", -1.0)
                    
                    val resultMsg = executeSystemAutomation(actionType, scrollDir, clickX, clickY)
                    createResultJson(!resultMsg.contains("failed"), resultMsg)
                }
                else -> createResultJson(false, "Unknown tool call: $name")
            }
        } catch (e: Exception) {
            createResultJson(false, "Execution error: ${e.message}")
        }
    }

    private fun createResultJson(success: Boolean, message: String): String {
        return JSONObject().apply {
            put("success", success)
            put("result", message)
        }.toString()
    }

    private fun openApp(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                // If specific launcher intent not found, let's try direct category intent
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun searchAndCallContact(contactName: String): String {
        // Permissions Check
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "failed. Missing READ_CONTACTS permission. Ask the user in a sassy tone to turn on Contacts Permission in settings."
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "failed. Missing CALL_PHONE permission. Ask the user in a sassy tone to turn on Phone Dialing Permission in settings so I can make calls."
        }

        val phoneNumber = lookupContactNumber(contactName)
        if (phoneNumber == null) {
            return "failed. I searched for '$contactName' in your address book but couldn't find any matching contacts."
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            "Successfully triggered phone call to $contactName ($phoneNumber)."
        } catch (e: Exception) {
            "failed. Could not trigger natural phone call. ${e.message}"
        }
    }

    private fun sendWhatsAppMessage(contactName: String, message: String): String {
        // Permissions Check
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "failed. Missing READ_CONTACTS permission. Ask the user sassy-like to enable Contacts in Settings so I can search WhatsApp numbers."
        }

        val phoneNumber = lookupContactNumber(contactName)
        
        return try {
            val cleanPhone = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: ""
            // WhatsApp Web deep-link works flawlessly
            val uriStr = if (cleanPhone.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
            } else {
                "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (cleanPhone.isNotEmpty()) {
                "Successfully opened WhatsApp messenger with pre-filled message for $contactName ($cleanPhone)."
            } else {
                "Successfully opened WhatsApp contact selector with pre-filled text because contact '$contactName' phone wasn't found."
            }
        } catch (e: Exception) {
            "failed. Could not open WhatsApp. Make sure WhatsApp is installed."
        }
    }

    private fun sendGmail(recipientEmail: String, subject: String, body: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun lookupContactNumber(namePattern: String): String? {
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$namePattern%")
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numColumnIndex >= 0) {
                    return cursor.getString(numColumnIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun executeSystemAutomation(actionType: String, scrollDirection: String, clickX: Double, clickY: Double): String {
        val service = ZoyaAccessibilityService.instance
        if (service == null) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "failed. Accessibility Service integration is disabled on this phone. I have automatically redirected the user to Android Accessibility Settings so they can enable the Zoya Voice Assistant service. Please speak to them and guide them to turn it on."
        }

        return try {
            when (actionType.uppercase()) {
                "HOME" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    if (success) "Successfully went to Home screen." else "failed to go Home."
                }
                "BACK" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    if (success) "Successfully executed system Back action." else "failed to go Back."
                }
                "NOTIFICATIONS" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    if (success) "Successfully pulled down notification panel." else "failed to open notification shade."
                }
                "QUICK_SETTINGS" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    if (success) "Successfully pulled down Quick Settings tile menu." else "failed to open Quick Settings."
                }
                "RECENTS" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                    if (success) "Successfully displayed recently used apps panel." else "failed to show Recents list."
                }
                "POWER_DIALOG" -> {
                    val success = service.performGlobalActionWrapper(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                    if (success) "Successfully raised master power/restart overlay dialog." else "failed to request system Power dialog."
                }
                "SCROLL" -> {
                    val success = service.performScroll(scrollDirection)
                    if (success) "Successfully performed scroll gesture $scrollDirection." else "failed to scroll $scrollDirection."
                }
                "CLICK" -> {
                    if (clickX in 0.0..1.0 && clickY in 0.0..1.0) {
                        val metrics = context.resources.displayMetrics
                        val absX = (clickX * metrics.widthPixels).toFloat()
                        val absY = (clickY * metrics.heightPixels).toFloat()
                        val success = service.performClickAt(absX, absY)
                        if (success) "Successfully tapped coordinate position ($absX, $absY)." else "failed to dispatch physical click at coordinate position ($absX, $absY)."
                    } else {
                        "failed. Absolute coordinates percentages must fall within 0.0 to 1.0 values bounds."
                    }
                }
                else -> "failed. Unknown action gesture type: $actionType."
            }
        } catch (e: Exception) {
            "failed. Gesture dispatch error: ${e.message}"
        }
    }
}
