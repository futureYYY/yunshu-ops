package com.rackexcel.mobile.network

import com.rackexcel.mobile.image.PreparedImages
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RackJsonParser
import com.rackexcel.mobile.storage.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VisionApiException(
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class ConnectionCheckResult(
    val model: String,
    val latencyMillis: Long,
    val visualCheckPassed: Boolean,
)

class VisionApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(140, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun checkConnection(config: ModelConfig): ConnectionCheckResult = withContext(Dispatchers.IO) {
        validateConfig(config)
        val startedAt = System.nanoTime()
        val modelsRequest = Request.Builder()
            .url(endpoint(config.url, "models"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val modelsResponse = execute(modelsRequest)
        if (!modelsResponse.isSuccessful) throw apiError(modelsResponse.code, modelsResponse.body)
        val availableModels = runCatching {
            JSONObject(modelsResponse.body).optJSONArray("data")?.let { data ->
                (0 until data.length()).mapNotNull { index -> data.optJSONObject(index)?.optString("id") }
            }.orEmpty()
        }.getOrElse { emptyList() }
        if (availableModels.isNotEmpty() && config.model !in availableModels) {
            throw VisionApiException("模型列表中未发现 ${config.model}")
        }

        val probeRequest = Request.Builder()
            .url(endpoint(config.url, "chat/completions"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(ConnectionProbePayload.body(config.model).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val probeResponse = execute(probeRequest)
        if (!probeResponse.isSuccessful) throw apiError(probeResponse.code, probeResponse.body)
        val probeText = try {
            val root = JSONObject(probeResponse.body)
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw VisionApiException("模型自检响应缺少 message")
            extractContent(message)
        } catch (error: VisionApiException) {
            throw error
        } catch (error: Exception) {
            throw VisionApiException("模型自检响应解析失败: ${error.message ?: "未知错误"}")
        }
        val ready = runCatching {
            val start = probeText.indexOf('{')
            val end = probeText.lastIndexOf('}')
            JSONObject(if (start >= 0 && end >= start) probeText.substring(start, end + 1) else probeText).optBoolean("ready")
        }.getOrDefault(false)
        if (!ready) throw VisionApiException("模型视觉识别链路自检未返回 ready")
        ConnectionCheckResult(
            model = config.model,
            latencyMillis = (System.nanoTime() - startedAt) / 1_000_000,
            visualCheckPassed = true,
        )
    }

    suspend fun testConnection(config: ModelConfig): String = checkConnection(config).let { result ->
        "模型连接正常（${result.model}，${result.latencyMillis}ms）"
    }

    suspend fun analyze(
        config: ModelConfig,
        systemPrompt: String,
        preparedImages: PreparedImages,
        imageName: String,
    ): Rack = withContext(Dispatchers.IO) {
        validateConfig(config)
        val body = VisionRequestBuilder.build(
            model = config.model,
            systemPrompt = systemPrompt,
            originalImageDataUri = preparedImages.originalDataUri,
            cropDataUris = preparedImages.cropDataUris,
        )
        val request = Request.Builder()
            .url(endpoint(config.url, "chat/completions"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        if (!response.isSuccessful) throw apiError(response.code, response.body)
        val text = try {
            val root = JSONObject(response.body)
            val choices = root.optJSONArray("choices")
                ?: throw VisionApiException("模型响应缺少 choices")
            if (choices.length() == 0) throw VisionApiException("模型响应为空")
            val message = choices.optJSONObject(0)?.optJSONObject("message")
                ?: throw VisionApiException("模型响应缺少 message")
            extractContent(message)
        } catch (error: VisionApiException) {
            throw error
        } catch (error: Exception) {
            throw VisionApiException("模型响应解析失败: ${error.message ?: "未知错误"}")
        }
        RackJsonParser.parse(text, imageName)
    }

    private fun validateConfig(config: ModelConfig) {
        if (!config.url.startsWith("http://") && !config.url.startsWith("https://")) {
            throw VisionApiException("模型 URL 必须以 http:// 或 https:// 开头")
        }
        if (config.model.isBlank()) throw VisionApiException("请填写模型名称")
        if (config.apiKey.isBlank()) throw VisionApiException("请填写 API Key")
    }

    private fun execute(request: Request): ApiResponse {
        return try {
            httpClient.newCall(request).execute().use { response ->
                ApiResponse(response.code, response.isSuccessful, response.body?.string().orEmpty())
            }
        } catch (error: Exception) {
            throw VisionApiException("请求模型失败: ${error.message ?: "网络错误"}", cause = error)
        }
    }

    private fun apiError(code: Int, body: String): VisionApiException {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty().ifBlank { body.take(180) }
        return VisionApiException("模型接口返回 HTTP $code：$detail", httpStatus = code)
    }

    private fun extractContent(message: JSONObject): String {
        val content = message.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val part = content.optJSONObject(index) ?: continue
                    part.optString("text").takeIf { it.isNotBlank() }?.let(::append)
                }
            }
            else -> message.optString("text")
        }.trim().takeIf { it.isNotEmpty() }
            ?: throw VisionApiException("模型响应没有文本内容")
    }

    private fun endpoint(base: String, path: String): String =
        "${base.trimEnd('/')}/$path"

    private data class ApiResponse(val code: Int, val isSuccessful: Boolean, val body: String)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "RackExcelMobile/1.0 (Android)"
    }
}
