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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeTvPalette
import com.joeshannon.joetv.ui.theme.JoeTvTheme
import com.joeshannon.joetv.ui.theme.applyJoeTvTheme
import com.joeshannon.joetv.ui.theme.currentJoeTvTheme
import com.joeshannon.joetv.ui.theme.paletteFor
import com.joeshannon.joetv.weather.GeocodeResult
import com.joeshannon.joetv.weather.RADAR_MAX_ZOOM
import com.joeshannon.joetv.weather.RADAR_MIN_ZOOM
import com.joeshannon.joetv.weather.RadarLocation
import com.joeshannon.joetv.weather.getCleanVisuals
import com.joeshannon.joetv.weather.getPinnedRadarLocation
import com.joeshannon.joetv.weather.getRadarZoom
import com.joeshannon.joetv.weather.RadarLocationMode
import com.joeshannon.joetv.weather.getRadarLocationMode
import com.joeshannon.joetv.weather.searchLocations
import com.joeshannon.joetv.weather.setCleanVisuals
import com.joeshannon.joetv.weather.setPinnedRadarLocation
import com.joeshannon.joetv.weather.setRadarZoom
import com.joeshannon.joetv.weather.setRadarLocationMode
import kotlinx.coroutines.delay

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

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(10.dp))
                RadarSettingsSection(context = context)
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



/**
 * Radar widget controls: GPS lock vs. a pinned location, zoom level, and
 * the "clean visuals" light-precipitation filter. Backed by the same
 * "joetv_preferences" file as the theme choice above (see
 * weather/RadarPreferences.kt).
 */
@Composable
private fun RadarSettingsSection(context: Context) {
    var locationMode by remember { mutableStateOf(getRadarLocationMode(context)) }
    var zoom by remember { mutableStateOf(getRadarZoom(context)) }
    var cleanVisuals by remember { mutableStateOf(getCleanVisuals(context)) }
    var pinnedLocation by remember { mutableStateOf(getPinnedRadarLocation(context)) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationMode = RadarLocationMode.GPS
            setRadarLocationMode(context, RadarLocationMode.GPS)
        }
    }

    // Debounced so typing a city name doesn't fire a geocoding request on
    // every keystroke.
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        delay(400)

        searchResults = runCatching {
            searchLocations(searchQuery)
        }.getOrDefault(emptyList())
    }

    Column {
        Text(
            text = "Radar Location",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Powers the live radar card in the hero banner",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            JoeTvPillButton(
                label = when (locationMode) {
                    RadarLocationMode.PINNED -> "Location: Pinned"
                    RadarLocationMode.GPS -> "Location: Current (GPS)"
                    RadarLocationMode.AUTO_IP -> "Location: Auto (Network)"
                },
                onClick = {
                    val nextMode = when (locationMode) {
                        RadarLocationMode.PINNED -> RadarLocationMode.GPS
                        RadarLocationMode.GPS -> RadarLocationMode.AUTO_IP
                        RadarLocationMode.AUTO_IP -> RadarLocationMode.PINNED
                    }

                    if (nextMode == RadarLocationMode.GPS) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            locationMode = RadarLocationMode.GPS
                            setRadarLocationMode(context, RadarLocationMode.GPS)
                        } else {
                            locationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        }
                    } else {
                        locationMode = nextMode
                        setRadarLocationMode(context, nextMode)
                    }
                }
            )

            JoeTvPillButton(
                label = if (cleanVisuals) "Clean visuals: On" else "Clean visuals: Off",
                onClick = {
                    cleanVisuals = !cleanVisuals
                    setCleanVisuals(context, cleanVisuals)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Zoom",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            JoeTvPillButton(
                label = "−",
                onClick = {
                    zoom = (zoom - 1).coerceIn(RADAR_MIN_ZOOM, RADAR_MAX_ZOOM)
                    setRadarZoom(context, zoom)
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = zoom.toString(),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(10.dp))

            JoeTvPillButton(
                label = "+",
                onClick = {
                    zoom = (zoom + 1).coerceIn(RADAR_MIN_ZOOM, RADAR_MAX_ZOOM)
                    setRadarZoom(context, zoom)
                }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (locationMode == RadarLocationMode.PINNED) {
            Text(
                text = "Pinned: ${pinnedLocation.label}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = "Search a city to pin the radar there…",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp
                )
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                cursorBrush = joeFocusBrush(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                searchResults.forEach { result ->
                    JoeTvPillButton(
                        label = result.label,
                        onClick = {
                            val newLocation = RadarLocation(
                                latitude = result.latitude,
                                longitude = result.longitude,
                                label = result.label
                            )
                            setPinnedRadarLocation(context, newLocation)
                            pinnedLocation = newLocation
                            locationMode = RadarLocationMode.PINNED
                            setRadarLocationMode(context, RadarLocationMode.PINNED)
                            searchQuery = ""
                            searchResults = emptyList()
                        }
                    )
                }
            }
        }
    }
}
