package com.joeshannon.joetv.calendar

import android.content.Context

class GoogleCalendarTokenStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun save(
        tokens: GoogleCalendarAuth.GoogleTokens
    ) {
        val expiresAt =
            System.currentTimeMillis() +
                    (tokens.expiresIn * 1000L)

        val editor =
            preferences.edit()
                .putString(
                    KEY_ACCESS_TOKEN,
                    tokens.accessToken
                )
                .putLong(
                    KEY_EXPIRES_AT,
                    expiresAt
                )

        if (!tokens.refreshToken.isNullOrBlank()) {
            editor.putString(
                KEY_REFRESH_TOKEN,
                tokens.refreshToken
            )
        }

        editor.apply()
    }

    fun accessToken(): String? =
        preferences.getString(
            KEY_ACCESS_TOKEN,
            null
        )

    fun refreshToken(): String? =
        preferences.getString(
            KEY_REFRESH_TOKEN,
            null
        )

    fun expiresAt(): Long =
        preferences.getLong(
            KEY_EXPIRES_AT,
            0L
        )

    fun hasRefreshToken(): Boolean =
        !refreshToken().isNullOrBlank()

    fun isAccessTokenUsable(): Boolean {
        val token =
            accessToken()

        if (token.isNullOrBlank()) {
            return false
        }

        // Refresh a little early instead of waiting
        // until the exact expiration second.
        return System.currentTimeMillis() <
                expiresAt() - 60_000L
    }

    fun isConnected(): Boolean =
        !accessToken().isNullOrBlank() ||
                hasRefreshToken()

    fun clear() {
        preferences
            .edit()
            .clear()
            .apply()
    }

    companion object {
        const val PREFS_NAME =
            "joetv_google_calendar"

        private const val KEY_ACCESS_TOKEN =
            "access_token"

        private const val KEY_REFRESH_TOKEN =
            "refresh_token"

        private const val KEY_EXPIRES_AT =
            "expires_at"
    }
}
