package com.joeshannon.joetv.calendar

// -----------------------------------------------------------------------------
// Google Service Account Authentication
//
// JoeTV reads a calendar that has been *shared with* a service account, rather
// than acting on behalf of a signed-in user.
//
// Why not normal OAuth:
// • Google's device flow (code on TV, enter it on a phone) does not support
//   Calendar scopes -- only sign-in, Drive and YouTube.
// • The loopback flow needs a browser and a keyboard, and this Pi has neither.
// • Refresh tokens expire after 7 days unless the OAuth consent screen is in
//   production, and reaching production requires a verified domain hosting a
//   homepage and privacy policy -- absurd overhead for reading one calendar.
//
// A service account sidesteps all of it. There is no consent screen, no
// publishing status, no refresh token and therefore nothing to expire. Access
// is granted by sharing the calendar with the service account's email address,
// and revoked by un-sharing it.
//
// The flow implemented here is Google's JWT bearer grant: build a short-lived
// assertion, sign it with the service account's RSA private key, and trade it
// for an access token good for one hour.
// -----------------------------------------------------------------------------

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class GoogleServiceAccountAuth(
    private val clientEmail: String,
    private val privateKeyBase64: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    // Cached access token and the moment it stops being usable. Tokens last an
    // hour, so re-signing a JWT on every calendar poll would be pure waste.
    private var cachedToken: String? = null
    private var cachedTokenExpiresAt: Long = 0L

    // Guards the cache so two concurrent refreshes cannot both mint a token.
    private val tokenMutex = Mutex()

    /**
     * True when this build carries service account credentials.
     */
    fun isConfigured(): Boolean =
        clientEmail.isNotBlank() &&
                privateKeyBase64.isNotBlank()

    /**
     * Returns a usable access token, minting a new one only when needed.
     *
     * @throws Exception if credentials are missing or Google rejects the
     * assertion. Callers treat a failure as "calendar unavailable".
     */
    suspend fun accessToken(): String = tokenMutex.withLock {

        val existingToken = cachedToken

        // Refresh a minute early rather than racing the exact expiry second.
        if (
            existingToken != null &&
            System.currentTimeMillis() < cachedTokenExpiresAt - 60_000L
        ) {
            return@withLock existingToken
        }

        val freshToken = requestAccessToken()

        cachedToken = freshToken.token
        cachedTokenExpiresAt =
            System.currentTimeMillis() + (freshToken.expiresIn * 1000L)

        freshToken.token
    }

    private data class AccessToken(
        val token: String,
        val expiresIn: Int
    )

    /**
     * Signs a fresh JWT assertion and exchanges it for an access token.
     */
    private suspend fun requestAccessToken(): AccessToken =
        withContext(Dispatchers.IO) {

            if (!isConfigured()) {
                throw IllegalStateException(
                    "JoeTV has no service account credentials. Add " +
                            "JOETV_GOOGLE_SERVICE_ACCOUNT_EMAIL and " +
                            "JOETV_GOOGLE_SERVICE_ACCOUNT_KEY to local.properties."
                )
            }

            val assertion = buildSignedAssertion()

            val body = FormBody.Builder()
                .add(
                    "grant_type",
                    "urn:ietf:params:oauth:grant-type:jwt-bearer"
                )
                .add(
                    "assertion",
                    assertion
                )
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->

                val responseBody =
                    response.body?.string()
                        ?: throw Exception(
                            "Empty response from Google token endpoint"
                        )

                if (!response.isSuccessful) {
                    // Google returns a JSON error body here; surfacing it makes
                    // a misconfigured key or an unshared calendar diagnosable
                    // from logcat instead of guesswork.
                    throw Exception(
                        "Google token request failed " +
                                "(${response.code}): $responseBody"
                    )
                }

                val json = JSONObject(responseBody)

                AccessToken(
                    token = json.getString("access_token"),
                    expiresIn = json.optInt("expires_in", 3600)
                )
            }
        }

    /**
     * Builds the signed JWT that proves JoeTV holds the service account's key.
     */
    private fun buildSignedAssertion(): String {

        val issuedAt = System.currentTimeMillis() / 1000L

        // Google caps assertion lifetime at one hour.
        val expiresAt = issuedAt + 3600L

        val header = JSONObject()
            .put("alg", "RS256")
            .put("typ", "JWT")

        val claims = JSONObject()
            .put("iss", clientEmail)
            .put("scope", CALENDAR_SCOPE)
            .put("aud", TOKEN_ENDPOINT)
            .put("iat", issuedAt)
            .put("exp", expiresAt)

        val signingInput =
            encodeSegment(header.toString()) +
                    "." +
                    encodeSegment(claims.toString())

        val signature = signRs256(signingInput)

        return "$signingInput.$signature"
    }

    /**
     * RSA-SHA256 signature over the JWT signing input, base64url encoded.
     */
    private fun signRs256(signingInput: String): String {

        val keyBytes =
            Base64.getDecoder().decode(privateKeyBase64)

        val privateKey =
            KeyFactory
                .getInstance("RSA")
                .generatePrivate(
                    PKCS8EncodedKeySpec(keyBytes)
                )

        val signer =
            Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(signingInput.toByteArray(Charsets.UTF_8))
            }

        return encodeSegment(signer.sign())
    }

    private fun encodeSegment(value: String): String =
        encodeSegment(value.toByteArray(Charsets.UTF_8))

    // JWT uses base64url without padding; the standard encoder would emit
    // '+', '/' and '=' and Google would reject the assertion.
    private fun encodeSegment(value: ByteArray): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value)

    companion object {
        private const val TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token"

        // Read-only: JoeTV only ever displays the next event.
        private const val CALENDAR_SCOPE =
            "https://www.googleapis.com/auth/calendar.readonly"
    }
}
