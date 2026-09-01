package com.joeshannon.joetv

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joeshannon.joetv.screens.HomeScreen
import com.joeshannon.joetv.screens.JoeTvNavigation
import com.joeshannon.joetv.ui.theme.JoeTVTheme

class MainActivity : ComponentActivity() {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val hideBarsRunnable = Runnable {
        hideSystemBars()
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        hideSystemBars()

        setContent {
            JoeTVTheme {

                BackHandler {
                    // JoeTV is the root HOME launcher.
                    // Back should not close the launcher.
                }

                HomeScreen(
                    context = this@MainActivity
                )
            }
        }
    }

    /**
     * Called when the Home key (or any other launcher intent) re-enters an
     * already-running JoeTV.
     *
     * JoeTV keeps its sub-screens (All Apps, the Google Calendar connect flow)
     * as Compose state, and Android reuses this Activity instance rather than
     * recreating it. Without this, pressing Home while on a sub-screen would
     * appear to do nothing. Resetting here makes Home behave the way it does on
     * every other launcher: it always lands you back on the home screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        JoeTvNavigation.requestHomeReset()
    }

    /**
     * Lets a TV remote's dedicated mic/search/assist button start JoeTV's
     * voice search, the same way tapping the on-screen mic button does.
     *
     * Different remotes map their mic button to different key codes, so all
     * three are handled here rather than guessing which one Joe's remote
     * sends.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST -> {
                JoeTvNavigation.requestVoiceSearch()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            300
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            1_000
        )
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onConfigurationChanged(
        newConfig: Configuration
    ) {
        super.onConfigurationChanged(
            newConfig
        )

        hideSystemBars()

        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            500
        )

        mainHandler.postDelayed(
            hideBarsRunnable,
            1_500
        )
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(
            hideBarsRunnable
        )

        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(
                WindowInsetsCompat.Type.statusBars() or
                        WindowInsetsCompat.Type.navigationBars()
            )

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}