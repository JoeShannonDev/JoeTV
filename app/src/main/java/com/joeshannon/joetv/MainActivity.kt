package com.joeshannon.joetv

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joeshannon.joetv.screens.HomeScreen
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