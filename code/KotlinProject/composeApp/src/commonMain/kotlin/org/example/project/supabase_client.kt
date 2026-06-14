package org.example.project

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object SupabaseClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    private val baseUrl = SupabaseConfig.SUPABASE_URL
    private val apiKey = SupabaseConfig.SUPABASE_KEY

    /** Insert user (auth record only: username, password_hash, full_name) */
    suspend fun insertUser(signUpRequest: SignUpRequest): Result<User> {
        return try {
            val response: HttpResponse = client.post("$baseUrl/rest/v1/users") {
                headers {
                    append("apikey", apiKey)
                    append("Authorization", "Bearer $apiKey")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=representation")
                }
                setBody(signUpRequest)
            }

            if (response.status.isSuccess()) {
                val users: List<User> = response.body()
                Result.success(users.first())
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Signup failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Insert user profile (extended profile data) */
    suspend fun insertUserProfile(profile: UserProfileInsert): Result<Unit> {
        return try {
            val response: HttpResponse = client.post("$baseUrl/rest/v1/user_profiles") {
                headers {
                    append("apikey", apiKey)
                    append("Authorization", "Bearer $apiKey")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=minimal")
                }
                setBody(profile)
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Profile creation failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get user by username (Login check) */
    suspend fun getUserByUsername(username: String): Result<User?> {
        return try {
            val response: HttpResponse = client.get("$baseUrl/rest/v1/users") {
                headers {
                    append("apikey", apiKey)
                    append("Authorization", "Bearer $apiKey")
                }
                parameter("username", "eq.$username")
                parameter("select", "id,username,password_hash,full_name,created_at")
            }

            if (response.status.isSuccess()) {
                val users: List<User> = response.body()
                Result.success(users.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Check if username exists */
    suspend fun checkUsernameExists(username: String): Boolean {
        return try {
            val result = getUserByUsername(username)
            result.isSuccess && result.getOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    /** Get user profile by user_id */
    suspend fun getUserProfileByUserId(userId: String): Result<UserProfile?> {
        return try {
            val response: HttpResponse = client.get("$baseUrl/rest/v1/user_profiles") {
                headers {
                    append("apikey", apiKey)
                    append("Authorization", "Bearer $apiKey")
                }
                parameter("user_id", "eq.$userId")
                parameter(
                    "select",
                    "user_id,date_of_birth,gender,race_ethnicity,physical_activity,smoking,drinking,calories_per_day,protein_g,carbs_g,fat_g,caffeine_g,fiber_g,hypertension,diabetes,hyperlipidemia"
                )
            }

            if (response.status.isSuccess()) {
                val profiles: List<UserProfile> = response.body()
                Result.success(profiles.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch user profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
