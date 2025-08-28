package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.util.Log
import com.mtkresearch.breezeapp.engine.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlamaStackClient(private val config: LlamaStackConfig) {
    
    companion object {
        private const val TAG = "LlamaStackClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.timeout, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeout, TimeUnit.MILLISECONDS)
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createHeadersInterceptor())
            .retryOnConnectionFailure(true)
            .build()
    }
    
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
    }
    
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = if (config.apiKey != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .build()
            } else {
                originalRequest
            }
            chain.proceed(newRequest)
        }
    }
    
    private fun createHeadersInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "BreezeApp-Engine/1.0")
            
            // Add API-specific headers based on endpoint
            when {
                config.endpoint.contains("llamastack.ai") -> {
                    requestBuilder.header("X-LlamaStack-Client-Version", "0.2.14")
                }
                config.endpoint.contains("openrouter.ai") -> {
                    requestBuilder.header("HTTP-Referer", "https://github.com/mtkresearch/BreezeApp")
                    requestBuilder.header("X-Title", "BreezeApp Mobile AI")
                }
                config.endpoint.contains("openai.com") -> {
                    requestBuilder.header("OpenAI-Beta", "assistants=v2")
                }
            }
            
            chain.proceed(requestBuilder.build())
        }
    }
    
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${config.endpoint}/health")
                    .get()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Health check response: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "Health check failed", e)
                false
            }
        }
    }
    
    suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        return withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(ChatCompletionRequest.serializer(), request)
            Log.d(TAG, "Chat completion request: $requestJson")
            
            // Smart endpoint URL construction - handle different API formats
            val fullUrl = buildChatCompletionUrl(config.endpoint)
            
            Log.d(TAG, "Using endpoint URL: $fullUrl")
            
            // Log compatibility analysis
            logEndpointCompatibility(config.endpoint)
            
            val httpRequest = Request.Builder()
                .url(fullUrl)
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            var lastException: Exception? = null
            
            repeat(config.retryAttempts) { attempt ->
                try {
                    httpClient.newCall(httpRequest).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Chat completion response (${response.code}): $responseBody")
                        
                        if (response.isSuccessful) {
                            return@withContext json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
                        } else {
                            val errorMessage = parseErrorResponse(responseBody, response.code)
                            throw IOException("HTTP ${response.code}: $errorMessage")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Chat completion attempt ${attempt + 1} failed", e)
                    
                    if (attempt < config.retryAttempts - 1) {
                        Thread.sleep(config.retryDelayMs * (attempt + 1))
                    }
                }
            }
            
            throw lastException ?: IOException("All retry attempts failed")
        }
    }
    
    private fun buildChatCompletionUrl(endpoint: String): String {
        val baseEndpoint = endpoint.trimEnd('/')
        
        return when {
            // Already complete URL
            baseEndpoint.endsWith("/chat/completions") -> baseEndpoint
            
            // OpenAI/OpenRouter style: /v1 prefix
            baseEndpoint.endsWith("/v1") -> "$baseEndpoint/chat/completions"
            
            // LlamaStack native style: direct endpoint
            baseEndpoint.contains("llamastack.ai") -> "$baseEndpoint/v1/chat/completions"
            
            // Generic OpenAI-compatible
            else -> "$baseEndpoint/v1/chat/completions"
        }
    }
    
    private fun logEndpointCompatibility(endpoint: String) {
        val compatibility = when {
            endpoint.contains("llamastack.ai") -> "Native LlamaStack"
            endpoint.contains("openrouter.ai") -> "OpenRouter (Cross-compatible)"
            endpoint.contains("openai.com") -> "OpenAI (Cross-compatible)"
            endpoint.contains("localhost") || endpoint.contains("127.0.0.1") -> "Local development"
            else -> "Generic OpenAI-compatible"
        }
        Log.d(TAG, "API Compatibility: $compatibility")
    }
    
    private fun parseErrorResponse(responseBody: String, httpCode: Int): String {
        return try {
            // Try to parse standard OpenAI error format
            val errorJson = json.parseToJsonElement(responseBody).jsonObject
            val error = errorJson["error"]?.jsonObject
            val message = error?.get("message")?.jsonPrimitive?.content
            val type = error?.get("type")?.jsonPrimitive?.content
            
            when {
                message != null && type != null -> "[$type] $message"
                message != null -> message
                else -> responseBody.take(200)
            }
        } catch (e: Exception) {
            // Fallback to raw response if parsing fails
            responseBody.take(200)
        }
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 4096,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: List<ContentItem>
)

@Serializable
data class ContentItem(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ResponseMessage,
    val finish_reason: String?
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)