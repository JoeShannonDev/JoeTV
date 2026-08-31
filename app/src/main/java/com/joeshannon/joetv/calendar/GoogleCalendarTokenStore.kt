package com.joeshannon.joetv.calendar

import android.content.Context
import com.joeshannon.joetv.BuildConfig

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

    /**
     * The refresh token JoeTV uses to mint new access tokens.
     *
     * A token saved by an on-device sign-in wins. Failing that, JoeTV falls
     * back to one baked in at build time from local.properties, which is how
     * this launcher is normally provisioned: the Pi has no browser and no
     * signed-in Google account, so the OAuth flow is run once on a desktop
     * machine and the resulting token travels with the build. That also means
     * Calendar survives a reinstall or a data wipe without re-authorizing.
     *
     * Returns null when neither exists, which callers read as "not connected".
     */
    fun refreshToken(): String? {
        val savedToken =
            preferences.getString(
                KEY_REFRESH_TOKEN,
                null
            )

        if (!savedToken.isNullOrBlank()) {
            return savedToken
        }

        return BuildConfig.GOOGLE_REFRESH_TOKEN
            .takeIf { it.isNotBlank() }
    }

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

    /**
     * Forgets the tokens saved on this device.
     *
     * Note this cannot clear a build-time refresh token; that one is part of
     * the APK and comes back on the next read. Disconnecting for real means
     * rebuilding without JOETV_GOOGLE_REFRESH_TOKEN, or revoking JoeTV's
     * access from the Google account itself.
     */
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
