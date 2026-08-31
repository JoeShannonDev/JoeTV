package com.joeshannon.joetv.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// -----------------------------------------------------------------------------
// JoeTV Brand Palette
//
// Neon cyan + violet accent, matching the boot animation's cyan/purple
// "JOETV" wordmark. Centralized here so the launcher doesn't scatter raw
// hex literals across composables.
// -----------------------------------------------------------------------------

val JoeCyan = Color(0xFF22D3EE)
val JoePurple = Color(0xFFA78BFA)

// Background gradient stops (dark, with a faint cyan/purple tint instead of
// the old plain blue-black gradient).
val JoeBackgroundTop = Color(0xFF11202A)
val JoeBackgroundMid = Color(0xFF130B1F)
val JoeBackgroundBottom = Color(0xFF030407)
val JoeBackgroundBase = Color(0xFF05070B)

// Soft glow blobs used in the animated background.
val JoeGlowCyan = Color(0x2622D3EE)
val JoeGlowPurple = Color(0x24A78BFA)
