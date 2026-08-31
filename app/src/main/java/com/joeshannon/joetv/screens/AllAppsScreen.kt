package com.joeshannon.joetv.screens

// -----------------------------------------------------------------------------
// JoeTV All Apps
//
// Full-screen grid of every app JoeTV can launch.
//
// The home screen used to render the entire app list inline as a long
// horizontal row, which meant scrolling sideways through dozens of apps to
// reach the end. That list lives here instead: a proper grid, laid out in rows,
// navigable with the D-pad in two dimensions.
//
// Card behaviour (open, favorite, hide) is shared with the home screen rather
// than reimplemented, so the same shortcuts work in both places.
// -----------------------------------------------------------------------------

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase

/**
 * Full-screen grid listing every app available to JoeTV.
 *
 * @param apps Visible (non-hidden) apps, already sorted by AppManager.
 * @param hiddenApps Apps the user has hidden, shown in their own section so
 * they can be restored without digging through settings.
 * @param onExit Returns to the home screen. Wired to both the Back key and the
 * on-screen button so there is always a visible way out.
 */
@Composable
internal fun AllAppsScreen(
    context: Context,
    soundManager: JoeTvSoundManager,
    apps: List<JoeTvApp>,
    hiddenApps: List<JoeTvApp>,
    favoritePackages: Set<String>,
    hiddenPackages: Set<String>,
    onOpen: (JoeTvApp) -> Unit,
    onToggleFavorite: (JoeTvApp) -> Unit,
    onToggleHidden: (JoeTvApp) -> Unit,
    onExit: () -> Unit
) {

    // Back is the reflex for leaving a sub-screen. This handler is nested
    // inside MainActivity's launcher-level one, so it wins while All Apps is
    // showing and Back stops being a no-op here.
    BackHandler {
        onExit()
    }

    val backFocusRequester =
        remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JoeBackgroundBase)
    ) {

        JoeTvMovingBackground()

        LazyVerticalGrid(
            // Adaptive rather than a fixed column count: JoeTV runs on
            // whatever the attached TV reports, and this keeps the cards a
            // sensible size instead of stretching them on a wide panel.
            columns = GridCells.Adaptive(minSize = 210.dp),
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
                                text = "All Apps",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${apps.size} apps  •  " +
                                        "OK to open  •  Page Down to favorite  •  X to hide",
                                color = Color.White.copy(alpha = 0.46f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                }
            }

            items(
                items = apps,
                key = { app -> app.packageName }
            ) { app ->
                JoeTvAppCard(
                    context = context,
                    soundManager = soundManager,
                    app = app,
                    isFavorite = app.packageName in favoritePackages,
                    isHidden = false,
                    fillWidth = true,
                    onFocused = { },
                    onOpen = {
                        onOpen(app)
                    },
                    onToggleFavorite = {
                        onToggleFavorite(app)
                    },
                    onToggleHidden = {
                        onToggleHidden(app)
                    }
                )
            }

            if (hiddenApps.isNotEmpty()) {

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(modifier = Modifier.height(26.dp))

                        SectionHeaderInline(
                            title = "Hidden Apps",
                            subtitle = "Press X to restore an app"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(
                    items = hiddenApps,
                    key = { app -> "hidden_${app.packageName}" }
                ) { app ->
                    JoeTvAppCard(
                        context = context,
                        soundManager = soundManager,
                        app = app,
                        isFavorite = app.packageName in favoritePackages,
                        isHidden = app.packageName in hiddenPackages,
                        fillWidth = true,
                        onFocused = { },
                        onOpen = {
                            onOpen(app)
                        },
                        onToggleFavorite = {
                            onToggleFavorite(app)
                        },
                        onToggleHidden = {
                            onToggleHidden(app)
                        }
                    )
                }
            }
        }
    }

    // Put focus on Back when the screen opens so the first D-pad press does
    // something predictable and the user is never focus-stranded.
    DisposableEffect(Unit) {
        runCatching {
            backFocusRequester.requestFocus()
        }

        onDispose { }
    }
}


/**
 * Header used inside the All Apps grid.
 *
 * SectionHeader carries the home screen's outer padding, which would double up
 * on the grid's own content padding, so the grid uses this flush variant.
 */
@Composable
private fun SectionHeaderInline(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 14.sp
        )
    }
}
