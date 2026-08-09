package com.joeshannon.joetv.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeshannon.joetv.BuildConfig
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GoogleCalendarConnectScreen(
    onConnected: (GoogleCalendarAuth.GoogleTokens) -> Unit
) {
    val scope = rememberCoroutineScope()

    var authorization by remember {
        mutableStateOf<GoogleCalendarAuth.DeviceAuthorization?>(null)
    }

    var status by remember {
        mutableStateOf("Not connected")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val auth = remember {
        GoogleCalendarAuth(
            clientId = BuildConfig.GOOGLE_CLIENT_ID,
            clientSecret = BuildConfig.GOOGLE_CLIENT_SECRET
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Google Calendar",
            color = Color.White
        )

        authorization?.let {
            Text(
                text = "Open this on your phone:",
                color = Color.White
            )

            Text(
                text = it.verificationUrl,
                color = Color.White
            )

            Text(
                text = "Enter code:",
                color = Color.White
            )

            Text(
                text = it.userCode,
                color = Color.White
            )
        }

        if (isLoading) {
            Text(
                text = status,
                color = Color.White
            )
        }

        errorMessage?.let {
            Text(
                text = "Error: $it",
                color = Color.White
            )
        }

        if (authorization == null) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            status = "Requesting Google sign-in..."

                            val deviceAuth =
                                auth.requestDeviceCode()

                            authorization = deviceAuth
                            status = "Waiting for authorization..."

                            val tokens =
                                auth.waitForAuthorization(
                                    deviceAuth
                                )

                            status = "Connected"
                            isLoading = false

                            onConnected(tokens)

                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage =
                                e.message ?: "Unknown error"
                        }
                    }
                }
            ) {
                Text(
                    text = "Connect Google Calendar",
                    color = Color.White
                )
            }
        }
    }
}