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

    private val tokenStore =
        GoogleCalendarTokenStore(
            appContext
        )

    private val auth =
        GoogleCalendarAuth(
            context = appContext,
            clientId = BuildConfig.GOOGLE_CLIENT_ID,
            clientSecret = BuildConfig.GOOGLE_CLIENT_SECRET
        )

    private val httpClient =
        OkHttpClient()

    suspend fun getNextEvent(): GoogleCalendarEvent? =
        withContext(Dispatchers.IO) {

            val accessToken =
                getValidAccessToken()
                    ?: return@withContext null

            val url =
                "https://www.googleapis.com/calendar/v3/calendars/primary/events"
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

    fun isConnected(): Boolean =
        tokenStore.isConnected()

    private suspend fun getValidAccessToken(): String? {

        if (tokenStore.isAccessTokenUsable()) {
            return tokenStore.accessToken()
        }

        val refreshToken =
            tokenStore.refreshToken()
                ?: return null

        val refreshedTokens =
            auth.refreshAccessToken(
                refreshToken
            )

        tokenStore.save(
            refreshedTokens
        )

        return refreshedTokens.accessToken
    }
}