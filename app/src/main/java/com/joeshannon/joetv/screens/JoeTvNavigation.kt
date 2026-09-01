package com.joeshannon.joetv.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// -----------------------------------------------------------------------------
// JoeTV Navigation
//
// JoeTV is a single-Activity launcher: every "screen" (home, All Apps, the
// Google Calendar connect flow) is just Compose state inside MainActivity.
//
// That creates one problem specific to being the HOME app: pressing the Home
// key re-launches MainActivity, but because the Activity instance is reused,
// the old screen state survives and the user stays stuck on whatever sub-screen
// they were on. This object gives MainActivity a way to say "go back to the
// home screen" without owning that state itself.
// -----------------------------------------------------------------------------

object JoeTvNavigation {

    /**
     * Incremented every time something outside the composition asks JoeTV to
     * return to the home screen. HomeScreen observes this and clears any
     * sub-screen state when it changes.
     */
    var homeResetSignal by mutableStateOf(0)
        private set

    /**
     * Asks JoeTV to drop back to the home screen. Safe to call from the
     * Activity (for example from onNewIntent when the Home key is pressed).
     */
    fun requestHomeReset() {
        homeResetSignal++
    }

    /**
     * Incremented whenever something outside the composition (namely, the
     * remote's search/voice/assist button via MainActivity.onKeyDown) asks
     * JoeTV to start listening. HomeScreen observes this and starts the
     * voice search flow the same way it would from the on-screen mic button.
     */
    var voiceSearchSignal by mutableStateOf(0)
        private set

    /**
     * Asks JoeTV to start voice search. Safe to call from the Activity (for
     * example from onKeyDown when the remote's mic/search button is pressed).
     */
    fun requestVoiceSearch() {
        voiceSearchSignal++
    }
}
