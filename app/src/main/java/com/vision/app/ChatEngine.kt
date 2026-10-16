package com.vision.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Text chat with friendly handling of Gemini API failures. */
object ChatEngine {
    private const val MODEL = "gemini-flash-latest" // change here if you want a specific model
    private val client = OkHttpClient.Builder().connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(45, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).retryOnConnectionFailure(true).build()

    suspend fun notificationReply(platform: String, sender: String, incoming: String, recentContext: String): String = withContext(Dispatchers.IO) {
        require(Prefs.apiKey.isNotBlank()) { "NO_REPLY" }
        val sensitiveRequest = Regex(
            "(?i)(password|passcode|otp|one.?time.?password|api.?key|secret.?key|private.?key|verification code|bank details|address|live location|send (me )?(a )?(photo|picture|selfie|private)|nude|intimate|पासवर्ड|ओटीपी|गुप्त कुंजी|प्राइवेट फोटो|निजी फोटो|लोकेशन|बैंक डिटेल)"
        ).containsMatchIn(incoming)
        if (sensitiveRequest) return@withContext "मैं निजी जानकारी, पासवर्ड या निजी फ़ोटो साझा नहीं कर सकता। आप राज को सीधे संदेश भेज सकते हैं।"
        val prompt = """
You are Star, Raj's AI assistant replying to an incoming $platform notification from $sender.
Be transparent that you are an AI assistant, not Raj. Speak naturally in the sender's language, briefly and politely.
You may have casual conversation but must never claim to be Raj, invent facts about Raj, promise Raj will do something, or disclose private information.
You know only that the creator is named Raj and that you are his assistant. Do not infer his activities or personal details. If asked what Raj is doing, say you do not know for sure and suggest the sender message Raj directly; do not claim you can pass messages to Raj because no such forwarding feature exists.
Never request passwords, OTPs, API keys, private photos, financial details, or sensitive personal information. Decline such requests politely.
Reply only to the incoming message; do not initiate unrelated topics. If the sender clearly ends the conversation or there is nothing useful to say, return exactly [NO_REPLY].
Use this recent conversation context only for continuity:
$recentContext
Incoming message: $incoming
Return only the reply text, no quotation marks.
""".trimIndent()
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", incoming)))))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .header("x-goog-api-key", Prefs.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@withContext "API_ERROR"
            val parts = JSONObject(s).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return@withContext "NO_REPLY"
            (0 until parts.length()).mapNotNull { i -> parts.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() } }
                .joinToString("\n").trim().takeIf { it.isNotBlank() && !it.contains("[NO_REPLY]") } ?: "NO_REPLY"
        }
    }

    suspend fun reply(history: List<Pair<Boolean, String>>): String = withContext(Dispatchers.IO) {
        require(Prefs.apiKey.isNotBlank()) { "Add your Gemini API key in Settings first." }
        val contents = JSONArray()
        history.forEach { (user, text) ->
            contents.put(JSONObject()
                .put("role", if (user) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", Persona.PROMPT + Persona.TEXT_NOTE))))
            .put("contents", contents)
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .header("x-goog-api-key", Prefs.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) when (r.code) {
                400, 401, 403 -> "Gemini rejected the request or API key. Check the key and API access in Settings."
                404 -> "The selected Gemini model isn't available for this API. Please check the model configuration."
                429 -> "Gemini is rate-limiting requests or the API quota is exhausted. Wait a little and try again."
                500, 502, 503, 504 -> "Gemini is busy or temporarily unavailable. Please try again in a moment."
                else -> "Gemini request failed (HTTP ${r.code}). Please try again."
            }
            else runCatching {
                val candidates = JSONObject(s).optJSONArray("candidates")
                    ?: throw IllegalStateException("Gemini returned no answer. Please try again.")
                if (candidates.length() == 0) throw IllegalStateException("Gemini returned no answer. Please try again.")
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: throw IllegalStateException("Gemini returned an empty answer. Please try again.")
                (0 until parts.length()).mapNotNull { i -> parts.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() } }.joinToString("\n")
            }.getOrElse { "Gemini returned an empty answer. Please try again." }
        }
    }
}
