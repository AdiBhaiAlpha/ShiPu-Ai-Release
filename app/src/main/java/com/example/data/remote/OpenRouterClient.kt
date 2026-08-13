package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterClient {
    private val ENCODED_KEY = "c2stb3ItdjEtYmU4YTQ3YmViZjJkN2YzM2M0NzYzYjhkNDM2OTQyNjZmMTQ0Y2I4MGM2MDhiZWY2NWMwMzlhN2I5YWJkN2JjNw=="
    private val OPENROUTER_API_KEY: String
        get() = try {
            String(android.util.Base64.decode(ENCODED_KEY, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    private val API_URL = "https://openrouter.ai/api/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatMessageInput(
        val role: String,
        val content: String
    )

    fun streamChatCompletion(
        messages: List<ChatMessageInput>,
        model: String = "openrouter/free",
        temperature: Float = 0.7f,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        val apiKey = OPENROUTER_API_KEY
        if (apiKey.isBlank()) {
            emit("[Error: OpenRouter API key is missing.]")
            return@flow
        }

        val jsonArray = JSONArray()

        // Inject system prompt if provided
        if (!systemPrompt.isNull_or_blank()) {
            val sysObj = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            jsonArray.put(sysObj)
        }

        for (msg in messages) {
            val msgObj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            }
            jsonArray.put(msgObj)
        }

        val requestBodyJson = JSONObject().apply {
            put("model", model.ifBlank { "openrouter/free" })
            put("messages", jsonArray)
            put("stream", true)
            put("temperature", temperature.toDouble())
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://shipuai.app")
            .addHeader("X-Title", "ShiPu AI")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "Unknown error"
            Log.e("OpenRouterClient", "Error response: ${response.code} $errBody")
            emit("[Error: OpenRouter API error ${response.code}. $errBody]")
            return@flow
        }

        val source = response.body?.source() ?: run {
            emit("[Error: Empty response body]")
            return@flow
        }

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("data: ")) {
                    val dataContent = trimmed.substring(6).trim()
                    if (dataContent == "[DONE]") {
                        break
                    }
                    try {
                        val json = JSONObject(dataContent)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            if (delta != null && delta.has("content")) {
                                val chunk = delta.getString("content")
                                if (chunk.isNotEmpty()) {
                                    emit(chunk)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed chunk line
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRouterClient", "Stream reading interrupted or failed", e)
            emit("\n[Stream interrupted]")
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
