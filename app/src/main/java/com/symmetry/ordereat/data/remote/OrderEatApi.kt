package com.symmetry.ordereat.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ApiConfig(val scriptUrl: String, val token: String) {
    val isConfigured: Boolean get() = scriptUrl.isNotBlank() && token.isNotBlank()
}

/** 後端回 ok=false、或網路／格式錯誤 */
open class ApiException(message: String) : IOException(message)

/** Google 端暫時性錯誤，稍後重試通常就會好 */
class TransientApiException(message: String) : ApiException(message)

/**
 * Apps Script Web App 用戶端。
 * 回應統一為 { ok, data } / { ok, error }；HTTP 一律 200，所以要看 ok。
 * POST 會被 302 轉址到 googleusercontent，OkHttp 預設會跟著轉（改成 GET 取回結果）。
 */
@Singleton
class OrderEatApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: ApiConfig,
) {
    /**
     * Google 轉址那段偶爾會卡 30～60 秒（script 本身只跑 0.5 秒），卡住時重打通常幾秒就回來。
     * 所以可重試的請求設較短的整體逾時，逾時就換下一次，不要乾等。
     */
    private val readClient = client.newBuilder().callTimeout(READ_CALL_TIMEOUT_S, TimeUnit.SECONDS).build()
    private val writeClient = client.newBuilder().callTimeout(WRITE_CALL_TIMEOUT_S, TimeUnit.SECONDS).build()

    suspend fun bootstrap(): BootstrapDto = get("bootstrap", BootstrapDto.serializer())

    /** 開放中的團：團狀態＋整團明細（含插件跟單），只讀 */
    suspend fun getSessionLines(sessionId: String): SessionLinesDto =
        get("getSessionLines", SessionLinesDto.serializer(), mapOf("sessionId" to sessionId))

    /** 後端以 sessionId 覆蓋寫入，重送不會重複，所以可以自動重試 */
    suspend fun submitSession(payload: SubmitSessionPayload): SubmitResultDto =
        post("submitSession", SubmitSessionPayload.serializer(), payload, SubmitResultDto.serializer(), retryable = true)

    /** 整份名單覆寫，重送結果相同 */
    suspend fun syncMembers(payload: SyncMembersPayload): SyncMembersResultDto =
        post("syncMembers", SyncMembersPayload.serializer(), payload, SyncMembersResultDto.serializer(), retryable = true)

    /** 整團刪除；找不到也算成功，可重試 */
    suspend fun deleteSession(sessionId: String): DeleteSessionResultDto =
        post("deleteSession", DeleteSessionPayload.serializer(), DeleteSessionPayload(sessionId), DeleteSessionResultDto.serializer(), retryable = true)

    suspend fun uploadPhoto(payload: UploadPhotoPayload): UploadPhotoResultDto =
        post("uploadPhoto", UploadPhotoPayload.serializer(), payload, UploadPhotoResultDto.serializer())

    private suspend fun <T> get(
        action: String,
        resultSerializer: KSerializer<T>,
        params: Map<String, String> = emptyMap(),
    ): T {
        val url = baseUrl().newBuilder()
            .addQueryParameter("action", action)
            .addQueryParameter("token", config.token)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder().url(url).get().build()
        // 讀取是安全的，Google 暫時性錯誤自動重試
        return retrying { execute(readClient, request, resultSerializer) }
    }

    private suspend fun <P, T> post(
        action: String,
        payloadSerializer: KSerializer<P>,
        payload: P,
        resultSerializer: KSerializer<T>,
        retryable: Boolean = false,
    ): T {
        val body = buildJsonObject {
            put("token", config.token)
            put("action", action)
            put("payload", json.encodeToJsonElement(payloadSerializer, payload))
        }
        val request = Request.Builder()
            .url(baseUrl())
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return if (retryable) {
            retrying { execute(writeClient, request, resultSerializer) }
        } else {
            execute(client, request, resultSerializer)
        }
    }

    /** 逾時、轉址後 404、排隊等鎖逾時等暫時性錯誤，稍等再試，最多 [MAX_ATTEMPTS] 次 */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        repeat(MAX_ATTEMPTS - 1) { attempt ->
            try {
                return block()
            } catch (e: TransientApiException) {
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return block()
    }

    private fun baseUrl() = config.scriptUrl.takeIf { config.isConfigured }?.toHttpUrlOrNull()
        ?: throw ApiException("尚未設定後端網址或 Token（local.properties）")

    private suspend fun <T> execute(client: OkHttpClient, request: Request, resultSerializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val text = try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404 || response.code == 429 || response.code >= 500) {
                        throw TransientApiException("Google 暫時沒有回應（HTTP ${response.code}），請稍後再試")
                    }
                    if (!response.isSuccessful) throw ApiException("伺服器錯誤（HTTP ${response.code}）")
                    response.body.string()
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: InterruptedIOException) {
                throw TransientApiException("Google 回應太慢，請稍後再試")
            } catch (e: IOException) {
                throw TransientApiException("無法連線：${e.message ?: "網路錯誤"}")
            }
            val envelope = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw ApiException("伺服器回應格式錯誤，請確認部署的存取權是「所有人」")
            val ok = envelope["ok"]?.jsonPrimitive?.booleanOrNull == true
            if (!ok) {
                val error = envelope["error"]?.jsonPrimitive?.contentOrNull ?: "未知錯誤"
                throw if (isTransientServerError(error)) TransientApiException("Google 忙碌中，請稍後再試（$error）") else ApiException(error)
            }
            try {
                json.decodeFromJsonElement(resultSerializer, envelope["data"] ?: JsonNull)
            } catch (e: IllegalArgumentException) {
                throw ApiException("資料格式不符：${e.message}")
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_ATTEMPTS = 3
        const val READ_CALL_TIMEOUT_S = 20L

        /** 送單要搶鎖寫入，給長一點 */
        const val WRITE_CALL_TIMEOUT_S = 40L

        /** Apps Script 的鎖等待逾時、服務逾時、呼叫太頻繁 */
        fun isTransientServerError(message: String): Boolean {
            val m = message.lowercase()
            return listOf("lock", "timed out", "timeout", "too many times", "temporarily").any { it in m }
        }
    }
}
