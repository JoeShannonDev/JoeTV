package com.joeshannon.joetv.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import com.joeshannon.joetv.BuildConfig
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeCyan
import com.joeshannon.joetv.ui.theme.JoePurple
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Google Calendar Connect Screen
//
// Walks the user through Google's OAuth flow so JoeTV can read upcoming events.
//
// TV-specific concerns handled here:
// • There is always a visible way out (Back button + Back key), because a TV
//   remote user who gets stuck mid-sign-in has no other escape.
// • Missing OAuth credentials are reported up front instead of failing deep
//   inside the flow with a confusing network error.
// • An in-flight sign-in is cancelled when the user leaves, so the local
//   redirect server socket does not stay bound.
// -----------------------------------------------------------------------------

@Composable
fun GoogleCalendarConnectScreen(
    onConnected: (GoogleCalendarAuth.GoogleTokens) -> Unit,
    onCancel: () -> Unit
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    // True when the app was built without Google OAuth credentials. Without
    // these the sign-in URL is malformed and Google rejects it, so JoeTV says
    // so plainly rather than sending the user into a broken browser flow.
    val credentialsMissing =
        remember {
            BuildConfig.GOOGLE_CLIENT_ID.isBlank() ||
                    BuildConfig.GOOGLE_CLIENT_SECRET.isBlank()
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

    // Tracks the running sign-in so leaving the screen can cancel it.
    var connectJob by remember {
        mutableStateOf<Job?>(null)
    }

    // Holds the request whose server socket needs closing if the user bails
    // out while JoeTV is still waiting on the Google redirect.
    var pendingRequest by remember {
        mutableStateOf<GoogleCalendarAuth.AuthorizationRequest?>(null)
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

    /**
     * Cancels any in-flight sign-in and releases the local redirect socket.
     */
    fun abandonConnection() {
        connectJob?.cancel()
        connectJob = null

        runCatching {
            pendingRequest?.serverSocket?.close()
        }

        pendingRequest = null
    }

    /**
     * Leaves the connect screen, cleaning up first.
     */
    fun leave() {
        abandonConnection()
        onCancel()
    }

    // The remote's Back key is the reflex for "get me out of here", so it maps
    // to the same exit as the on-screen button. This BackHandler is nested
    // inside MainActivity's launcher-level one, so it takes priority while
    // this screen is showing and stops Back from being swallowed.
    BackHandler {
        leave()
    }

    // If this screen leaves the composition some other way (for example the
    // Home key resetting JoeTV), do not leak the socket or the coroutine.
    DisposableEffect(Unit) {
        onDispose {
            connectJob?.cancel()

            runCatching {
                pendingRequest?.serverSocket?.close()
            }
        }
    }

    val backFocusRequester =
        remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JoeBackgroundBase)
    ) {

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
                text =
                    if (credentialsMissing) {
                        "This build of JoeTV has no Google sign-in credentials, " +
                                "so Calendar cannot connect yet."
                    } else {
                        "Connect your Google account to show upcoming events on JoeTV."
                    },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            if (credentialsMissing) {

                Text(
                    text = "Add JOETV_GOOGLE_CLIENT_ID and JOETV_GOOGLE_CLIENT_SECRET " +
                            "to local.properties, then rebuild JoeTV.",
                    color = JoeCyan.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )

                Spacer(
                    modifier = Modifier.height(24.dp)
                )
            }

            if (isLoading) {

                Text(
                    text = status,
                    color = Color.White,
                    fontSize = 16.sp
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "Press Back on your remote to cancel.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )
            }

            errorMessage?.let { message ->

                Text(
                    text = "Error: $message",
                    color = Color(0xFFFF8A8A),
                    fontSize = 15.sp
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(16.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                // Always present, always reachable. This is the guaranteed way
                // off this screen even if the sign-in flow misbehaves.
                JoeTvConnectButton(
                    label = "Back to JoeTV",
                    isPrimary = false,
                    modifier = Modifier.focusRequester(
                        backFocusRequester
                    ),
                    onClick = {
                        leave()
                    }
                )

                if (!credentialsMissing) {

                    JoeTvConnectButton(
                        label =
                            if (isLoading) {
                                "Connecting..."
                            } else {
                                "Connect Google Calendar"
                            },
                        isPrimary = true,
                        enabled = !isLoading,
                        onClick = {

                            connectJob = scope.launch {

                                try {

                                    isLoading = true
                                    errorMessage = null

                                    status =
                                        "Preparing Google sign-in..."

                                    val authorizationRequest =
                                        auth.createAuthorizationRequest()

                                    pendingRequest =
                                        authorizationRequest

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

                                        pendingRequest = null

                                        throw Exception(
                                            "No web browser is installed on this device. " +
                                                    "Install one, or sign in from a phone on the same network."
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
                                    pendingRequest = null
                                    connectJob = null

                                    onConnected(
                                        tokens
                                    )

                                } catch (
                                    e: Exception
                                ) {

                                    isLoading = false

                                    runCatching {
                                        pendingRequest?.serverSocket?.close()
                                    }

                                    pendingRequest = null
                                    connectJob = null

                                    errorMessage =
                                        e.message
                                            ?: "Unknown Google authorization error"

                                    status =
                                        "Connection failed"
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Land focus on the exit button so the very first D-pad press is useful
    // and the user is never focus-stranded on this screen.
    DisposableEffect(Unit) {
        runCatching {
            backFocusRequester.requestFocus()
        }

        onDispose { }
    }
}


/**
 * Focusable pill button styled to match the rest of JoeTV.
 *
 * The TV Material button was replaced here so this screen keeps the same
 * cyan/purple focus treatment used by the launcher's cards.
 */
@Composable
private fun JoeTvConnectButton(
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (focused) {
                    Brush.linearGradient(
                        listOf(JoeCyan, JoePurple)
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color(0xE0191D27),
                            Color(0xE0191D27)
                        )
                    )
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                brush =
                    if (focused) {
                        Brush.linearGradient(
                            listOf(JoeCyan, JoePurple)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.16f)
                            )
                        )
                    },
                shape = RoundedCornerShape(26.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled) {
                onClick()
            }
            .padding(
                horizontal = 26.dp
            ),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = label,
            color =
                if (focused) {
                    Color(0xFF06121A)
                } else {
                    Color.White.copy(
                        alpha = if (enabled) 0.92f else 0.45f
                    )
                },
            fontSize = 16.sp,
            fontWeight =
                if (isPrimary) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }
        )
    }
}
