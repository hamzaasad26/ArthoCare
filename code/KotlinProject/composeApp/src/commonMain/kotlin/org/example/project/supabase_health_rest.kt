package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object SupabaseHealthRest {
    val timelineJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(timelineJson)
        }
    }

    private fun base(table: String) =
        "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$table"

    suspend inline fun <reified T> post(table: String, body: T, preferRepresentation: Boolean = false): Result<Unit> {
        return try {
            val response: HttpResponse = client.post(base(table)) {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append(HttpHeaders.Authorization, "Bearer ${AuthService.bearerForSupabaseRest()}")
                    append(HttpHeaders.ContentType, "application/json")
                    if (preferRepresentation) append("Prefer", "return=representation")
                }
                setBody(body)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("POST $table failed: ${response.status} ${response.bodyAsText()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend inline fun <reified R> get(
        table: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {}
    ): Result<List<R>> {
        return try {
            val response: HttpResponse = client.get(base(table)) {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append(HttpHeaders.Authorization, "Bearer ${AuthService.bearerForSupabaseRest()}")
                }
                configure()
            }
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(Exception("GET $table failed: ${response.status} ${response.bodyAsText()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
