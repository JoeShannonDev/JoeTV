package com.joeshannon.joetv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// -----------------------------------------------------------------------------
// JoeTV Brand Palette / Theme System
//
// JoeTV's cards, borders, and background are all drawn with a handful of
// named colors (JoeCyan, JoeBackgroundTop, and so on) referenced directly
// throughout HomeScreen.kt, AllAppsScreen.kt, etc. -- there are ~30 call
// sites across those files.
//
// Rather than threading a theme object as a parameter through every one of
// those composables (an invasive change touching every card, border, and
// background in the launcher), each of those named colors is declared here
// as a top-level `var ... by mutableStateOf(...)` instead of a plain `val`.
// Reading a mutableStateOf-backed property from inside a @Composable's body
// automatically subscribes that composable to it, exactly like `remember`-ed
// state -- so every existing "JoeCyan" / "JoeBackgroundTop" / etc. reference
// keeps working completely unchanged, and calling applyJoeTvTheme() below
// recomposes the whole launcher in the new palette with no other file
// touched.
// -----------------------------------------------------------------------------

/**
 * The themes JoeTV ships with. Three neon options in the spirit of the
 * original cyan/purple brand, plus four calmer, more "modern living room"
 * palettes for anyone who wants something less loud.
 */
enum class JoeTvTheme(val displayName: String) {
    NEON_CYAN("Neon Cyan"),
    NEON_PINK("Neon Pink"),
    NEON_LIME("Neon Lime"),
    CLASSIC_DARK("Classic Dark"),
    FOREST("Forest"),
    MIDNIGHT("Midnight"),
    SUNSET("Sunset")
}

/**
 * The full set of colors one JoeTV theme controls.
 */
data class JoeTvPalette(
    val accentPrimary: Color,
    val accentSecondary: Color,
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val backgroundBase: Color,
    val glowPrimary: Color,
    val glowSecondary: Color
)

private val neonCyanPalette = JoeTvPalette(
    accentPrimary = Color(0xFF22D3EE),
    accentSecondary = Color(0xFFA78BFA),
    backgroundTop = Color(0xFF11202A),
    backgroundMid = Color(0xFF130B1F),
    backgroundBottom = Color(0xFF030407),
    backgroundBase = Color(0xFF05070B),
    glowPrimary = Color(0x2622D3EE),
    glowSecondary = Color(0x24A78BFA)
)

private val neonPinkPalette = JoeTvPalette(
    accentPrimary = Color(0xFFEC4899),
    accentSecondary = Color(0xFF0EA5E9),
    backgroundTop = Color(0xFF1F0B1D),
    backgroundMid = Color(0xFF190A22),
    backgroundBottom = Color(0xFF080308),
    backgroundBase = Color(0xFF07050A),
    glowPrimary = Color(0x26EC4899),
    glowSecondary = Color(0x240EA5E9)
)

private val neonLimePalette = JoeTvPalette(
    accentPrimary = Color(0xFF84CC16),
    accentSecondary = Color(0xFFFF8C42),
    backgroundTop = Color(0xFF141F0D),
    backgroundMid = Color(0xFF1B190A),
    backgroundBottom = Color(0xFF060704),
    backgroundBase = Color(0xFF07080A),
    glowPrimary = Color(0x2684CC16),
    glowSecondary = Color(0x24FF8C42)
)

private val classicDarkPalette = JoeTvPalette(
    accentPrimary = Color(0xFFE5E7EB),
    accentSecondary = Color(0xFF9CA3AF),
    backgroundTop = Color(0xFF1C2128),
    backgroundMid = Color(0xFF14171C),
    backgroundBottom = Color(0xFF0A0B0D),
    backgroundBase = Color(0xFF08090A),
    glowPrimary = Color(0x1FE5E7EB),
    glowSecondary = Color(0x1C9CA3AF)
)

private val forestPalette = JoeTvPalette(
    accentPrimary = Color(0xFF10B981),
    accentSecondary = Color(0xFFFACC15),
    backgroundTop = Color(0xFF0D1F17),
    backgroundMid = Color(0xFF10190E),
    backgroundBottom = Color(0xFF050A06),
    backgroundBase = Color(0xFF060A07),
    glowPrimary = Color(0x2410B981),
    glowSecondary = Color(0x20FACC15)
)

private val midnightPalette = JoeTvPalette(
    accentPrimary = Color(0xFFC7D2E0),
    accentSecondary = Color(0xFF60A5FA),
    backgroundTop = Color(0xFF0B1330),
    backgroundMid = Color(0xFF0D1024),
    backgroundBottom = Color(0xFF04060F),
    backgroundBase = Color(0xFF05070E),
    glowPrimary = Color(0x1FC7D2E0),
    glowSecondary = Color(0x2460A5FA)
)

private val sunsetPalette = JoeTvPalette(
    accentPrimary = Color(0xFFFF8C42),
    accentSecondary = Color(0xFF8B5CF6),
    backgroundTop = Color(0xFF241209),
    backgroundMid = Color(0xFF1E0F1F),
    backgroundBottom = Color(0xFF0A0507),
    backgroundBase = Color(0xFF0A0608),
    glowPrimary = Color(0x26FF8C42),
    glowSecondary = Color(0x228B5CF6)
)

/**
 * Looks up the full color set for [theme]. Public so SettingsScreen.kt can
 * draw an accurate preview swatch for a theme that isn't necessarily the one
 * currently applied.
 */
fun paletteFor(theme: JoeTvTheme): JoeTvPalette = when (theme) {
    JoeTvTheme.NEON_CYAN -> neonCyanPalette
    JoeTvTheme.NEON_PINK -> neonPinkPalette
    JoeTvTheme.NEON_LIME -> neonLimePalette
    JoeTvTheme.CLASSIC_DARK -> classicDarkPalette
    JoeTvTheme.FOREST -> forestPalette
    JoeTvTheme.MIDNIGHT -> midnightPalette
    JoeTvTheme.SUNSET -> sunsetPalette
}

/**
 * The theme currently applied. Read by the Settings screen to highlight the
 * active choice; write it via [applyJoeTvTheme] rather than directly so the
 * derived colors below always stay in sync.
 */
var currentJoeTvTheme by mutableStateOf(JoeTvTheme.NEON_CYAN)
    private set

// Backing colors for every "JoeXxx" reference used throughout the launcher.
// Names match exactly what HomeScreen.kt, AllAppsScreen.kt, etc. already
// import and use -- only the `val` -> `var ... by mutableStateOf` changed.
var JoeCyan by mutableStateOf(neonCyanPalette.accentPrimary)
    private set

var JoePurple by mutableStateOf(neonCyanPalette.accentSecondary)
    private set

var JoeBackgroundTop by mutableStateOf(neonCyanPalette.backgroundTop)
    private set

var JoeBackgroundMid by mutableStateOf(neonCyanPalette.backgroundMid)
    private set

var JoeBackgroundBottom by mutableStateOf(neonCyanPalette.backgroundBottom)
    private set

var JoeBackgroundBase by mutableStateOf(neonCyanPalette.backgroundBase)
    private set

var JoeGlowCyan by mutableStateOf(neonCyanPalette.glowPrimary)
    private set

var JoeGlowPurple by mutableStateOf(neonCyanPalette.glowSecondary)
    private set

/**
 * Switches JoeTV to [theme]. Every composable reading JoeCyan, JoePurple,
 * JoeBackgroundTop, etc. recomposes automatically -- callers still need to
 * persist the choice themselves (see SettingsScreen.kt) so it survives a
 * restart.
 */
fun applyJoeTvTheme(theme: JoeTvTheme) {
    val palette = paletteFor(theme)

    currentJoeTvTheme = theme
    JoeCyan = palette.accentPrimary
    JoePurple = palette.accentSecondary
    JoeBackgroundTop = palette.backgroundTop
    JoeBackgroundMid = palette.backgroundMid
    JoeBackgroundBottom = palette.backgroundBottom
    JoeBackgroundBase = palette.backgroundBase
    JoeGlowCyan = palette.glowPrimary
    JoeGlowPurple = palette.glowSecondary
}
