package com.joeshannon.joetv.calendar

import android.content.Context
import com.joeshannon.joetv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GoogleCalendarEvent(
    val title: String,
    val startTimeMillis: Long,
    val isAllDay: Boolean
)

class GoogleCalendarApi(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val httpClient =
        OkHttpClient()

    // JoeTV authenticates as a service account that the target calendar has
    // been shared with, rather than as a signed-in user. See
    // GoogleServiceAccountAuth for why the user-facing OAuth flows do not fit
    // a device with no browser and no keyboard.
    private val auth =
        GoogleServiceAccountAuth(
            clientEmail = BuildConfig.GOOGLE_SERVICE_ACCOUNT_EMAIL,
            privateKeyBase64 = BuildConfig.GOOGLE_SERVICE_ACCOUNT_KEY,
            httpClient = httpClient
        )

    // Which calendar to read. Normally the owner's Gmail address, which is the
    // ID of their primary calendar. "primary" would resolve to the service
    // account's own empty calendar, so it is deliberately not the default.
    private val calendarId =
        BuildConfig.GOOGLE_CALENDAR_ID

    suspend fun getNextEvent(): GoogleCalendarEvent? =
        withContext(Dispatchers.IO) {

            if (!isConnected()) {
                return@withContext null
            }

            val accessToken =
                getValidAccessToken()
                    ?: return@withContext null

            val url =
                (
                    "https://www.googleapis.com/calendar/v3/calendars/" +
                            java.net.URLEncoder.encode(calendarId, "UTF-8") +
                            "/events"
                    )
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "timeMin",
                        Instant.now().toString()
                    )
                    .addQueryParameter(
                        "singleEvents",
                        "true"
                    )
                    .addQueryParameter(
                        "orderBy",
                        "startTime"
                    )
                    .addQueryParameter(
                        "maxResults",
                        "1"
                    )
                    .build()

            val request =
                Request.Builder()
                    .url(url)
                    .header(
                        "Authorization",
                        "Bearer $accessToken"
                    )
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .get()
                    .build()

            httpClient
                .newCall(request)
                .execute()
                .use { response ->

                    val body =
                        response.body?.string()
                            ?: throw Exception(
                                "Empty Google Calendar response"
                            )

                    if (!response.isSuccessful) {
                        throw Exception(
                            "Google Calendar API failed: ${response.code}"
                        )
                    }

                    val root =
                        JSONObject(body)

                    val items =
                        root.getJSONArray(
                            "items"
                        )

                    if (items.length() == 0) {
                        return@withContext null
                    }

                    val event =
                        items.getJSONObject(0)

                    val start =
                        event.getJSONObject(
                            "start"
                        )

                    val dateTime =
                        start.optString(
                            "dateTime"
                        )

                    val date =
                        start.optString(
                            "date"
                        )

                    val isAllDay =
                        dateTime.isBlank() &&
                                date.isNotBlank()

                    val startTimeMillis =
                        if (!isAllDay) {

                            Instant
                                .parse(dateTime)
                                .toEpochMilli()

                        } else {

                            LocalDate
                                .parse(date)
                                .atStartOfDay(
                                    ZoneId.systemDefault()
                                )
                                .toInstant()
                                .toEpochMilli()
                        }

                    GoogleCalendarEvent(
                        title =
                            event.optString(
                                "summary",
                                "Untitled event"
                            ),

                        startTimeMillis =
                            startTimeMillis,

                        isAllDay =
                            isAllDay
                    )
                }
        }

    /**
     * True when this build has everything it needs to read a calendar.
     *
     * There is no per-user connection step any more, so this is purely a
     * question of whether the build was configured.
     */
    fun isConnected(): Boolean =
        auth.isConfigured() &&
                calendarId.isNotBlank()

    /**
     * Returns an access token, or null if the service account cannot get one.
     *
     * Failures are swallowed to null rather than thrown: a calendar that
     * cannot be read should leave the hero card empty, never crash the
     * launcher. The cause is printed for logcat.
     */
    private suspend fun getValidAccessToken(): String? =
        runCatching {
            auth.accessToken()
        }.onFailure { error ->
            println("JOETV_CALENDAR_AUTH_FAILED=${error.message}")
        }.getOrNull()
}