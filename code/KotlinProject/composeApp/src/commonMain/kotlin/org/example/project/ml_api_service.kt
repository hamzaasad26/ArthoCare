package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

object MlApiService {
    // TODO: move to environment config for production deployments.
    private const val BASE_URL = "http://10.0.2.2:8000"

    /**
     * Override this at app startup if running on a physical device:
     * e.g. "http://192.168.1.20:8000"
     */
    var baseUrlOverride: String? = null
    private val activeBaseUrl: String
        get() = baseUrlOverride ?: BASE_URL

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (t: CancellationException) {
            // Must never swallow: otherwise callers (e.g. RA Lens LaunchedEffect) keep running
            // after navigate-back and mutate snapshot state → "Coroutine scope left the composition".
            throw t
        } catch (t: Throwable) {
            Result.failure(enhanceConnectionError(t))
        }
    }

    private fun enhanceConnectionError(t: Throwable): Throwable {
        val msg = t.message.orEmpty()
        val looksLikeConnectivityFailure =
            msg.contains("failed to connect", ignoreCase = true) ||
                msg.contains("connect timed out", ignoreCase = true) ||
                msg.contains("connection refused", ignoreCase = true)

        if (!looksLikeConnectivityFailure) return t

        val hint = "Could not reach ML backend at $activeBaseUrl. " +
            "If using Android emulator, keep 10.0.2.2 and ensure FastAPI runs on port 8000. " +
            "If using a physical phone, set MlApiService.baseUrlOverride to your computer LAN IP " +
            "(example: http://192.168.1.20:8000) and ensure phone + computer are on the same Wi-Fi."
        return IllegalStateException(hint, t)
    }

    suspend fun predictStage1(payload: Stage1ApiRequest): Result<ApiPredictionResponse> = apiCall {
        val response = client.post("$activeBaseUrl/predict/stage1") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("Stage1 API error (${response.status.value}): $text")
        }
        response.body<ApiPredictionResponse>()
    }

    suspend fun predictStage3(payload: Stage3ApiRequest): Result<ApiPredictionResponse> = apiCall {
        val response = client.post("$activeBaseUrl/predict/stage3") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("Stage3 API error (${response.status.value}): $text")
        }
        response.body<ApiPredictionResponse>()
    }

    suspend fun predictOverall(payload: OverallApiRequest): Result<OverallApiResponse> = apiCall {
        val response = client.post("$activeBaseUrl/predict/overall") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("Overall API error (${response.status.value}): $text")
        }
        response.body<OverallApiResponse>()
    }

    suspend fun analyzeRaLensRom(payload: RaLensAnalyzeRequestApi): Result<RaLensAnalyzeResponseApi> = apiCall {
        val response = client.post("$activeBaseUrl/ralens/analyze-rom") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("RA Lens API error (${response.status.value}): $text")
        }
        response.body<RaLensAnalyzeResponseApi>()
    }

    suspend fun analyzeRaLensMedia(
        joint: String,
        side: String,
        bytes: ByteArray,
        filename: String = "capture.jpg"
    ): Result<RaLensAnalyzeResponseApi> = apiCall {
        val response = client.post("$activeBaseUrl/ralens/analyze-media") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("joint", joint)
                        append("side", side)
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            }
                        )
                    }
                )
            )
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("RA Lens media API error (${response.status.value}): $text")
        }
        response.body<RaLensAnalyzeResponseApi>()
    }

    suspend fun startRaLensDesktopAnalyzer(payload: RaLensDesktopStartRequestApi): Result<RaLensDesktopStartResponseApi> = apiCall {
        val response = client.post("$activeBaseUrl/ralens/desktop/start") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("RA Lens desktop start error (${response.status.value}): $text")
        }
        response.body<RaLensDesktopStartResponseApi>()
    }

    suspend fun getRaLensDesktopStatus(runId: String): Result<RaLensDesktopStatusResponseApi> = apiCall {
        val response = client.get("$activeBaseUrl/ralens/desktop/status/$runId")
        if (!response.status.isSuccess()) {
            val text = response.body<String>()
            throw IllegalStateException("RA Lens desktop status error (${response.status.value}): $text")
        }
        response.body<RaLensDesktopStatusResponseApi>()
    }
}

