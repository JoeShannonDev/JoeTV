package com.joeshannon.joetv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// -----------------------------------------------------------------------------
// JoeTV Background Style
//
// A second, independent picker alongside the color theme above: which
// *animated background* the launcher and Settings/Backgrounds/All Apps
// screens draw behind everything else. Same reactive pattern as
// currentJoeTvTheme in Color.kt -- a top-level `var ... by mutableStateOf`,
// so switching styles recomposes immediately with no parameter threading.
//
// The actual drawing for each style lives in HomeScreen.kt
// (JoeTvMovingBackground and friends), since it's Canvas/animation code
// that belongs with the rest of the launcher's UI, not in this file --
// this file only owns *which one is selected*.
// -----------------------------------------------------------------------------

enum class JoeTvBackgroundStyle(val displayName: String) {
    NEBULA("Nebula"),
    AURORA("Aurora"),
    STARFIELD("Starfield"),
    VIDEO("Video")
}

/**
 * The background style currently applied. Read by the Backgrounds screen to
 * highlight the active choice; write it via [applyJoeTvBackgroundStyle]
 * rather than directly.
 */
var currentJoeTvBackgroundStyle by mutableStateOf(JoeTvBackgroundStyle.NEBULA)
    private set

fun applyJoeTvBackgroundStyle(style: JoeTvBackgroundStyle) {
    currentJoeTvBackgroundStyle = style
}
