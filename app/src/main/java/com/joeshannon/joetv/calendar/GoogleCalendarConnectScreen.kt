package com.joeshannon.joetv.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.joeshannon.joetv.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GoogleCalendarConnectScreen(
    onConnected: (GoogleCalendarAuth.GoogleTokens) -> Unit
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var status by remember {
        mutableStateOf("Not connected")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val tokenStore =
        remember(context.applicationContext) {
            GoogleCalendarTokenStore(
                context.applicationContext
            )
        }

    val auth =
        remember(context.applicationContext) {
            GoogleCalendarAuth(
                context = context.applicationContext,
                clientId = BuildConfig.GOOGLE_CLIENT_ID,
                clientSecret = BuildConfig.GOOGLE_CLIENT_SECRET
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = "Google Calendar",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text = "Connect your Google account to show upcoming events on JoeTV.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        if (isLoading) {

            Text(
                text = status,
                color = Color.White,
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )
        }

        errorMessage?.let { message ->

            Text(
                text = "Error: $message",
                color = Color.White,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )
        }

        Button(
            enabled = !isLoading,
            onClick = {

                scope.launch {

                    try {

                        isLoading = true
                        errorMessage = null

                        status =
                            "Preparing Google sign-in..."

                        val authorizationRequest =
                            auth.createAuthorizationRequest()

                        println(
                            "JOETV_OAUTH_URL=${authorizationRequest.authorizationUrl}"
                        )

                        status =
                            "Complete Google sign-in in the browser..."

                        val browserIntent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    authorizationRequest.authorizationUrl
                                )
                            )

                        try {

                            context.startActivity(
                                browserIntent
                            )

                        } catch (
                            e: ActivityNotFoundException
                        ) {

                            authorizationRequest
                                .serverSocket
                                .close()

                            throw Exception(
                                "No web browser is installed on this device."
                            )
                        }

                        status =
                            "Waiting for Google authorization..."

                        val authorizationCode =
                            auth.waitForAuthorizationCode(
                                authorizationRequest
                            )

                        status =
                            "Finishing connection..."

                        val tokens =
                            auth.exchangeAuthorizationCode(
                                authorizationCode =
                                    authorizationCode,
                                authorizationRequest =
                                    authorizationRequest
                            )

                        tokenStore.save(tokens)

                        println(
                            "JOETV_CALENDAR_CONNECTED=true refresh_token_saved=${tokenStore.hasRefreshToken()}"
                        )

                        status =
                            "Google Calendar connected"

                        isLoading = false

                        onConnected(
                            tokens
                        )

                    } catch (
                        e: Exception
                    ) {

                        isLoading = false

                        errorMessage =
                            e.message
                                ?: "Unknown Google authorization error"

                        status =
                            "Connection failed"
                    }
                }
            }
        ) {

            Text(
                text =
                    if (isLoading) {
                        "Connecting..."
                    } else {
                        "Connect Google Calendar"
                    },
                color = Color.White
            )
        }
    }
}