package com.joeshannon.joetv.screens

// -----------------------------------------------------------------------------
// JoeTV Settings
//
// Currently just the theme picker -- a grid of every JoeTvTheme with a small
// preview swatch in that theme's own colors. Selecting one calls
// applyJoeTvTheme(), which recomposes the whole launcher immediately (see the
// comment atop ui/theme/Color.kt for how that works without touching every
// card in HomeScreen.kt), and persists the choice so it survives a restart.
// -----------------------------------------------------------------------------

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeTvPalette
import com.joeshannon.joetv.ui.theme.JoeTvTheme
import com.joeshannon.joetv.ui.theme.applyJoeTvTheme
import com.joeshannon.joetv.ui.theme.currentJoeTvTheme
import com.joeshannon.joetv.ui.theme.paletteFor

/**
 * Key under JoeTV's existing "joetv_preferences" SharedPreferences file. Read
 * once at launch (see HomeScreen's preferences setup) and written every time
 * a theme is picked here.
 */
internal const val THEME_PREFERENCE_KEY = "joetv_theme"

/**
 * Restores the theme saved from a previous run. Safe to call more than once;
 * an unrecognized or missing value falls back to the default (Neon Cyan)
 * JoeTvTheme already starts as, so this only needs to run when there's
 * something to change.
 */
internal fun restoreSavedTheme(context: Context) {
    val preferences = context.getSharedPreferences(
        "joetv_preferences",
        Context.MODE_PRIVATE
    )

    val savedName = preferences.getString(THEME_PREFERENCE_KEY, null) ?: return

    val savedTheme = runCatching {
        JoeTvTheme.valueOf(savedName)
    }.getOrNull() ?: return

    applyJoeTvTheme(savedTheme)
}

private fun persistTheme(context: Context, theme: JoeTvTheme) {
    context.getSharedPreferences(
        "joetv_preferences",
        Context.MODE_PRIVATE
    )
        .edit()
        .putString(THEME_PREFERENCE_KEY, theme.name)
        .apply()
}


/**
 * Full-screen theme picker.
 *
 * @param onExit Returns to the home screen. Wired to both the Back key and
 * the on-screen button, same pattern as [AllAppsScreen].
 */
@Composable
internal fun SettingsScreen(
    context: Context,
    onExit: () -> Unit
) {
    BackHandler {
        onExit()
    }

    val backFocusRequester = remember {
        FocusRequester()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JoeBackgroundBase)
    ) {
        JoeTvMovingBackground()

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 48.dp,
                end = 48.dp,
                top = 34.dp,
                bottom = 56.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JoeTvPillButton(
                            label = "Back",
                            modifier = Modifier.focusRequester(
                                backFocusRequester
                            ),
                            onClick = onExit
                        )

                        Spacer(modifier = Modifier.width(18.dp))

                        Column {
                            Text(
                                text = "Themes",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Pick a look for JoeTV",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            items(JoeTvTheme.entries.toList()) { theme ->
                ThemeCard(
                    theme = theme,
                    isSelected = theme == currentJoeTvTheme,
                    onSelect = {
                        applyJoeTvTheme(theme)
                        persistTheme(context, theme)
                    }
                )
            }
        }
    }
}


/**
 * One selectable theme swatch. Shows the theme's own background gradient and
 * accent colors so picking one is a preview, not a guess from a name alone.
 */
@Composable
private fun ThemeCard(
    theme: JoeTvTheme,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val palette: JoeTvPalette = paletteFor(theme)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(palette.backgroundTop, palette.backgroundMid)
                )
            )
            .then(
                if (focused || isSelected) {
                    Modifier.border(
                        border = BorderStroke(
                            width = if (focused) 3.dp else 2.dp,
                            brush = Brush.linearGradient(
                                listOf(palette.accentPrimary, palette.accentSecondary)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
            .clickable {
                onSelect()
            }
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .width(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.accentPrimary)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Active",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = theme.displayName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

