package com.joeshannon.joetv.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GoogleCalendarAuth(
    private val clientId: String,
    private val clientSecret: String
) {

    private val httpClient = OkHttpClient()

    data class DeviceAuthorization(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresIn: Int,
        val interval: Int
    )

    data class GoogleTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Int
    )

    suspend fun requestDeviceCode(): DeviceAuthorization =
        withContext(Dispatchers.IO) {

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add(
                    "scope",
                    "https://www.googleapis.com/auth/calendar.events.readonly"
                )
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/device/code")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    throw Exception(
                        "Device authorization failed: ${response.code}"
                    )
                }

                val json = JSONObject(
                    response.body?.string()
                        ?: throw Exception("Empty Google response")
                )

                DeviceAuthorization(
                    deviceCode = json.getString("device_code"),
                    userCode = json.getString("user_code"),
                    verificationUrl = json.getString("verification_url"),
                    expiresIn = json.getInt("expires_in"),
                    interval = json.optInt("interval", 5)
                )
            }
        }

    suspend fun waitForAuthorization(
        authorization: DeviceAuthorization
    ): GoogleTokens =
        withContext(Dispatchers.IO) {

            val startTime = System.currentTimeMillis()

            while (
                System.currentTimeMillis() - startTime <
                authorization.expiresIn * 1000L
            ) {

                delay(authorization.interval * 1000L)

                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("code", authorization.deviceCode)
                    .add(
                        "grant_type",
                        "http://oauth.net/grant_type/device_code"
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val json = JSONObject(
                        response.body?.string()
                            ?: throw Exception("Empty Google response")
                    )

                    if (response.isSuccessful) {

                        return@withContext GoogleTokens(
                            accessToken =
                                json.getString("access_token"),

                            refreshToken =
                                json.optString(
                                    "refresh_token"
                                ).takeIf {
                                    it.isNotBlank()
                                },

                            expiresIn =
                                json.getInt("expires_in")
                        )
                    }

                    when (json.optString("error")) {

                        "authorization_pending" -> {
                            // User has not approved yet.
                        }

                        "slow_down" -> {
                            delay(5_000)
                        }

                        "access_denied" -> {
                            throw Exception(
                                "Google authorization was denied"
                            )
                        }

                        "expired_token" -> {
                            throw Exception(
                                "Google authorization code expired"
                            )
                        }

                        else -> {
                            throw Exception(
                                "Google authorization failed: ${
                                    json.optString("error")
                                }"
                            )
                        }
                    }
                }
            }

            throw Exception(
                "Google authorization timed out"
            )
        }
}