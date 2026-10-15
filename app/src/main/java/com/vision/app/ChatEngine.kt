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
