package com.vision.app

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Opt-in listener for replying only to allowlisted Instagram/WhatsApp notification senders.
 * It never clicks UI or sends messages without a notification-provided RemoteInput action.
 */
class NotificationReplyService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = ConcurrentHashMap<String, MutableList<String>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val repliedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Prefs.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.notificationAssistantEnabled) return
        if (sbn.packageName == packageName) return
        val platform = when (sbn.packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "com.instagram.android" -> "Instagram"
            else -> return
        }
        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isBlank() || message.isBlank()) return
        val senderKey = sender.removePrefix("@").lowercase()
        if (Prefs.namesFor(platform).none { it == senderKey }) return
        // Avoid duplicate processing of the same notification and only use supported direct-reply actions.
        val notificationKey = "${sbn.key}:${sbn.postTime}:${message.hashCode()}"
        if (!inFlight.add(notificationKey) || !repliedKeys.add(notificationKey)) return
        val action = findReplyAction(sbn.notification) ?: run {
            inFlight.remove(notificationKey)
            repliedKeys.remove(notificationKey)
            return
        }
        scope.launch {
            try {
                val threadKey = "$platform:$senderKey"
                val context = synchronized(recent) { recent[threadKey]?.takeLast(8)?.joinToString("\n").orEmpty() }
                val reply = ChatEngine.notificationReply(platform, sender, message, context)
                if (reply.isBlank() || reply == "NO_REPLY" || reply.length > 700) return@launch
                sendReply(action, reply)
                synchronized(recent) {
                    val thread = recent.getOrPut(threadKey) { mutableListOf() }
                    thread.add("Them: $message")
                    thread.add("Star: $reply")
                    while (thread.size > 12) thread.removeAt(0)
                }
            } catch (_: Exception) {
                // Fail closed: if AI/API fails, do not send a guessed reply.
            } finally {
                inFlight.remove(notificationKey)
            }
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        return notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true &&
                action.title?.toString()?.let { it.contains("reply", true) || it.contains("जवाब") || it.contains("उत्तर") } == true
        } ?: notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
    }

    private fun sendReply(action: Notification.Action, message: String) {
        val inputs = action.remoteInputs ?: return
        val intent = Intent()
        val results = Bundle()
        inputs.forEach { input -> results.putCharSequence(input.resultKey, message) }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        action.actionIntent.send(this, 0, intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
