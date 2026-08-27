package com.joeshannon.joetv.calendar

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.ServerSocket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64


class GoogleCalendarAuth(
    context: Context,
    private val clientId: String,
    private val clientSecret: String
) {

    private val connectivityManager =
        context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    private val activeNetworkDns = object : Dns {

        override fun lookup(
            hostname: String
        ): List<InetAddress> {

            val activeNetwork =
                connectivityManager.activeNetwork
                    ?: throw UnknownHostException(
                        "No active Android network"
                    )

            return try {
                activeNetwork
                    .getAllByName(hostname)
                    .toList()
            } catch (e: Exception) {
                throw UnknownHostException(
                    "Active network could not resolve $hostname: ${e.message}"
                ).apply {
                    initCause(e)
                }
            }
        }
    }

    private val httpClient =
        OkHttpClient.Builder()
            .dns(activeNetworkDns)
            .build()

    companion object {
        const val CALENDAR_SCOPE =
            "https://www.googleapis.com/auth/calendar.events.readonly"
    }

    data class AuthorizationRequest(
        val authorizationUrl: String,
        val redirectUri: String,
        val codeVerifier: String,
        val serverSocket: ServerSocket
    )

    data class GoogleTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Int
    )

    suspend fun createAuthorizationRequest(): AuthorizationRequest =
        withContext(Dispatchers.IO) {

            val serverSocket = ServerSocket()

            serverSocket.reuseAddress = true

            serverSocket.bind(
                InetSocketAddress(
                    "127.0.0.1",
                    0
                )
            )

            val port = serverSocket.localPort

            val redirectUri =
                "http://127.0.0.1:$port"

            val codeVerifier =
                generateCodeVerifier()

            val codeChallenge =
                generateCodeChallenge(
                    codeVerifier
                )

            val authorizationUrl =
                "https://accounts.google.com/o/oauth2/v2/auth"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "client_id",
                        clientId
                    )
                    .addQueryParameter(
                        "redirect_uri",
                        redirectUri
                    )
                    .addQueryParameter(
                        "response_type",
                        "code"
                    )
                    .addQueryParameter(
                        "scope",
                        CALENDAR_SCOPE
                    )
                    .addQueryParameter(
                        "access_type",
                        "offline"
                    )
                    .addQueryParameter(
                        "prompt",
                        "consent"
                    )
                    .addQueryParameter(
                        "code_challenge",
                        codeChallenge
                    )
                    .addQueryParameter(
                        "code_challenge_method",
                        "S256"
                    )
                    .build()
                    .toString()

            AuthorizationRequest(
                authorizationUrl = authorizationUrl,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier,
                serverSocket = serverSocket
            )
        }

    suspend fun waitForAuthorizationCode(
        authorizationRequest: AuthorizationRequest
    ): String =
        withContext(Dispatchers.IO) {

            val socket =
                authorizationRequest
                    .serverSocket
                    .accept()

            try {

                val reader =
                    socket
                        .getInputStream()
                        .bufferedReader()

                val requestLine =
                    reader.readLine()
                        ?: throw Exception(
                            "Empty OAuth redirect"
                        )

                val target =
                    requestLine
                        .split(" ")
                        .getOrNull(1)
                        ?: throw Exception(
                            "Invalid OAuth redirect"
                        )

                val uri =
                    URI(
                        "http://127.0.0.1$target"
                    )

                val parameters =
                    uri.rawQuery
                        ?.split("&")
                        ?.associate { pair ->

                            val parts =
                                pair.split(
                                    "=",
                                    limit = 2
                                )

                            val key =
                                java.net.URLDecoder.decode(
                                    parts[0],
                                    "UTF-8"
                                )

                            val value =
                                java.net.URLDecoder.decode(
                                    parts.getOrElse(1) { "" },
                                    "UTF-8"
                                )

                            key to value
                        }
                        ?: emptyMap()

                val error =
                    parameters["error"]

                if (error != null) {
                    throw Exception(
                        "Google authorization failed: $error"
                    )
                }

                val code =
                    parameters["code"]
                        ?: throw Exception(
                            "Google authorization code missing"
                        )

                val response =
                    """
                    HTTP/1.1 200 OK
                    Content-Type: text/html; charset=UTF-8
                    Connection: close

                    <html>
                    <body style="font-family:sans-serif;background:#111;color:white;text-align:center;padding-top:80px;">
                        <h2>JoeTV connected</h2>
                        <p>You can close this page and return to JoeTV.</p>
                    </body>
                    </html>
                    """.trimIndent()

                socket
                    .getOutputStream()
                    .write(
                        response.toByteArray()
                    )

                socket
                    .getOutputStream()
                    .flush()

                code

            } finally {

                socket.close()

                authorizationRequest
                    .serverSocket
                    .close()
            }
        }

    suspend fun exchangeAuthorizationCode(
        authorizationCode: String,
        authorizationRequest: AuthorizationRequest
    ): GoogleTokens =
        withContext(Dispatchers.IO) {

            val body =
                FormBody.Builder()
                    .add(
                        "client_id",
                        clientId
                    )
                    .add(
                        "client_secret",
                        clientSecret
                    )
                    .add(
                        "code",
                        authorizationCode
                    )
                    .add(
                        "code_verifier",
                        authorizationRequest.codeVerifier
                    )
                    .add(
                        "redirect_uri",
                        authorizationRequest.redirectUri
                    )
                    .add(
                        "grant_type",
                        "authorization_code"
                    )
                    .build()

            val request =
                Request.Builder()
                    .url(
                        "https://oauth2.googleapis.com/token"
                    )
                    .post(body)
                    .build()

            val responseBody =
                executeWithRetry(request)

            val json =
                JSONObject(responseBody)

            GoogleTokens(
                accessToken =
                    json.getString(
                        "access_token"
                    ),

                refreshToken =
                    json.optString(
                        "refresh_token"
                    ).takeIf {
                        it.isNotBlank()
                    },

                expiresIn =
                    json.getInt(
                        "expires_in"
                    )
            )
        }

    suspend fun refreshAccessToken(
        refreshToken: String
    ): GoogleTokens =
        withContext(Dispatchers.IO) {

            val body =
                FormBody.Builder()
                    .add(
                        "client_id",
                        clientId
                    )
                    .add(
                        "client_secret",
                        clientSecret
                    )
                    .add(
                        "refresh_token",
                        refreshToken
                    )
                    .add(
                        "grant_type",
                        "refresh_token"
                    )
                    .build()

            val request =
                Request.Builder()
                    .url(
                        "https://oauth2.googleapis.com/token"
                    )
                    .post(body)
                    .build()

            val responseBody =
                executeWithRetry(request)

            val json =
                JSONObject(responseBody)

            GoogleTokens(
                accessToken =
                    json.getString(
                        "access_token"
                    ),

                refreshToken =
                    refreshToken,

                expiresIn =
                    json.getInt(
                        "expires_in"
                    )
            )
        }

    private suspend fun executeWithRetry(
        request: Request
    ): String {

        var lastUnknownHostError: UnknownHostException? = null

        repeat(5) { attempt ->

            try {

                httpClient
                    .newCall(request)
                    .execute()
                    .use { response ->

                        val responseBody =
                            response.body
                                ?.string()
                                ?: throw Exception(
                                    "Empty Google response"
                                )

                        if (!response.isSuccessful) {

                            val json =
                                JSONObject(
                                    responseBody
                                )

                            throw Exception(
                                "Google request failed: ${
                                    json.optString(
                                        "error_description",
                                        json.optString("error")
                                    )
                                }"
                            )
                        }

                        return responseBody
                    }

            } catch (
                e: UnknownHostException
            ) {

                lastUnknownHostError = e

                if (attempt < 4) {
                    delay(2_000)
                }
            }
        }

        throw Exception(
            "Could not reach Google after several attempts: ${
                lastUnknownHostError?.message
                    ?: "DNS lookup failed"
            }"
        )
    }

    private fun generateCodeVerifier(): String {

        val bytes =
            ByteArray(64)

        SecureRandom()
            .nextBytes(bytes)

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun generateCodeChallenge(
        verifier: String
    ): String {

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    verifier.toByteArray(
                        Charsets.US_ASCII
                    )
                )

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }
}