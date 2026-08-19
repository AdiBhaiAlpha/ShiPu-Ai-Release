package com.example.data.remote

import android.util.Log
import com.example.data.model.GenerationErrorType
import com.example.data.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Production-grade OpenRouter API streaming client.
 *
 * Features:
 * - Resilient SSE chunk parsing supporting multi-byte UTF-8, Bangla Unicode, markdown, and code blocks.
 * - Sane streaming timeouts (connect: 15s, read: 45s per chunk, call: unlimited for long responses).
 * - Distinct error classification (Network, Timeout, Rate Limit, Auth, Server, Parsing).
 * - Structured StreamEvent emission (Connecting, FirstToken, ChunkReceived, Completed, Failed).
 * - Zero emission of fake text markers like "[Stream interrupted]".
 */
class OpenRouterClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // Unlimited for long streaming responses
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
) {
    companion object {
        private const val TAG = "OpenRouterClient"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"

        private const val ENCODED_KEY = "c2stb3ItdjEtYmU4YTQ3YmViZjJkN2YzM2M0NzYzYjhkNDM2OTQyNjZmMTQ0Y2I4MGM2MDhiZWY2NWMwMzlhN2I5YWJkN2JjNw=="
    }

    private val defaultApiKey: String
        get() = try {
            String(android.util.Base64.decode(ENCODED_KEY, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Throwable) {
            try {
                String(java.util.Base64.getDecoder().decode(ENCODED_KEY), Charsets.UTF_8)
            } catch (e2: Throwable) {
                ""
            }
        }

    data class ChatMessageInput(
        val role: String,
        val content: String
    )

    fun streamChatCompletion(
        messages: List<ChatMessageInput>,
        model: String = "openrouter/free",
        temperature: Float = 0.7f,
        systemPrompt: String? = null,
        generationId: String = "gen_${UUID.randomUUID().toString().take(12)}",
        customApiKey: String? = null
    ): Flow<StreamEvent> = flow {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else defaultApiKey

        if (apiKey.isBlank()) {
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = "",
                    errorType = GenerationErrorType.AUTHENTICATION_FAILED,
                    errorMessage = "OpenRouter API key is missing or not configured.",
                    isRetryable = false
                )
            )
            return@flow
        }

        emit(StreamEvent.Connecting(generationId))
        Log.d(TAG, "GENERATION_START id=$generationId model=$model")

        val jsonArray = JSONArray()

        // Inject system prompt if provided
        if (!systemPrompt.isNullOrBlank()) {
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
            put("temperature", temperature.toDouble().coerceIn(0.0, 2.0))
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("HTTP-Referer", "https://shipuai.app")
            .addHeader("X-Title", "ShiPu AI")
            .post(requestBody)
            .build()

        var response: Response? = null
        val accumulatedResponse = java.lang.StringBuilder()
        var hasReceivedFirstToken = false
        var chunkCount = 0

        try {
            response = client.newCall(request).execute()
            val code = response.code

            Log.d(TAG, "HTTP_RESPONSE_RECEIVED id=$generationId status=$code")

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP $code"
                val (errorType, isRetryable, userMessage) = classifyHttpError(code, errBody)

                Log.e(TAG, "API_ERROR id=$generationId code=$code type=$errorType msg=$userMessage")
                emit(
                    StreamEvent.StreamFailed(
                        generationId = generationId,
                        partialContent = "",
                        errorType = errorType,
                        errorMessage = userMessage,
                        isRetryable = isRetryable,
                        statusCode = code
                    )
                )
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(
                    StreamEvent.StreamFailed(
                        generationId = generationId,
                        partialContent = "",
                        errorType = GenerationErrorType.PARSING_ERROR,
                        errorMessage = "Empty response body received from server.",
                        isRetryable = true
                    )
                )
                return@flow
            }

            val source = body.source()

            while (!source.exhausted()) {
                val rawLine = source.readUtf8Line() ?: break
                val line = rawLine.trim()

                if (line.isEmpty() || line.startsWith(":")) {
                    // Skip SSE comments / keepalives / empty lines
                    continue
                }

                if (line.startsWith("data:")) {
                    val dataContent = line.removePrefix("data:").trim()

                    if (dataContent == "[DONE]") {
                        Log.d(TAG, "STREAM_COMPLETED id=$generationId totalChars=${accumulatedResponse.length} chunks=$chunkCount")
                        break
                    }

                    if (dataContent.startsWith("{") && dataContent.endsWith("}")) {
                        try {
                            val json = JSONObject(dataContent)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val delta = firstChoice.optJSONObject("delta")
                                if (delta != null && delta.has("content")) {
                                    val textChunk = delta.optString("content", "")
                                    if (textChunk.isNotEmpty()) {
                                        if (!hasReceivedFirstToken) {
                                            hasReceivedFirstToken = true
                                            Log.d(TAG, "FIRST_TOKEN id=$generationId")
                                            emit(StreamEvent.FirstTokenReceived(generationId))
                                        }

                                        accumulatedResponse.append(textChunk)
                                        chunkCount++
                                        emit(
                                            StreamEvent.ChunkReceived(
                                                generationId = generationId,
                                                chunk = textChunk,
                                                totalLength = accumulatedResponse.length
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed SSE JSON chunk: ${e.message}")
                        }
                    }
                }
            }

            val finalContent = accumulatedResponse.toString()
            emit(
                StreamEvent.StreamCompleted(
                    generationId = generationId,
                    fullContent = finalContent,
                    tokenCount = chunkCount
                )
            )

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "TIMEOUT id=$generationId partialLen=${accumulatedResponse.length}", e)
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = accumulatedResponse.toString(),
                    errorType = GenerationErrorType.TIMEOUT,
                    errorMessage = "The response took too long to stream.",
                    isRetryable = true
                )
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "NETWORK_ERROR (DNS) id=$generationId", e)
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = accumulatedResponse.toString(),
                    errorType = GenerationErrorType.NETWORK_UNAVAILABLE,
                    errorMessage = "No internet connection available.",
                    isRetryable = true
                )
            )
        } catch (e: ConnectException) {
            Log.e(TAG, "NETWORK_ERROR (Connect) id=$generationId", e)
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = accumulatedResponse.toString(),
                    errorType = GenerationErrorType.NETWORK_UNAVAILABLE,
                    errorMessage = "Failed to connect to AI servers.",
                    isRetryable = true
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "CONNECTION_INTERRUPTED id=$generationId partialLen=${accumulatedResponse.length}", e)
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = accumulatedResponse.toString(),
                    errorType = GenerationErrorType.CONNECTION_INTERRUPTED,
                    errorMessage = "Connection interrupted while streaming.",
                    isRetryable = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "UNKNOWN_ERROR id=$generationId", e)
            emit(
                StreamEvent.StreamFailed(
                    generationId = generationId,
                    partialContent = accumulatedResponse.toString(),
                    errorType = GenerationErrorType.UNKNOWN,
                    errorMessage = "An unexpected error occurred during generation.",
                    isRetryable = true
                )
            )
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun classifyHttpError(code: Int, errorBody: String): Triple<GenerationErrorType, Boolean, String> {
        return when (code) {
            401 -> Triple(
                GenerationErrorType.AUTHENTICATION_FAILED,
                false,
                "Authentication failed. Please verify the OpenRouter API key in settings."
            )
            403 -> Triple(
                GenerationErrorType.AUTHENTICATION_FAILED,
                false,
                "Access forbidden. Your account does not have permission for this model."
            )
            429 -> Triple(
                GenerationErrorType.RATE_LIMITED,
                true,
                "Rate limit exceeded. Please wait a moment before sending another message."
            )
            400, 422 -> Triple(
                GenerationErrorType.CLIENT_ERROR,
                false,
                "Invalid request format or model parameters."
            )
            500, 502, 503, 504 -> Triple(
                GenerationErrorType.SERVER_ERROR,
                true,
                "AI service is temporarily unavailable (HTTP $code). Retrying may succeed."
            )
            else -> Triple(
                GenerationErrorType.UNKNOWN,
                code in 500..599,
                "AI service returned error HTTP $code."
            )
        }
    }
}
