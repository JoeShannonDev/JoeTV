package com.joeshannon.joetv.screens

// -----------------------------------------------------------------------------
// JoeTV Home Screen
//
// Main launcher screen for JoeTV.
//
// Responsibilities:
// • Display installed applications
// • Manage favorites and recently opened apps
// • Hide and restore applications
// • Display weather, date, and time
// • Search installed apps by name
// • Handle TV remote navigation
// • Launch Android TV applications
// -----------------------------------------------------------------------------


import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.joeshannon.joetv.calendar.GoogleCalendarConnectScreen
import com.joeshannon.joetv.calendar.GoogleCalendarApi
import com.joeshannon.joetv.calendar.GoogleCalendarEvent
import com.joeshannon.joetv.services.VoiceRecognizer
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeBackgroundBottom
import com.joeshannon.joetv.ui.theme.JoeBackgroundMid
import com.joeshannon.joetv.ui.theme.JoeBackgroundTop
import com.joeshannon.joetv.ui.theme.JoeTvBackgroundStyle
import com.joeshannon.joetv.ui.theme.currentJoeTvBackgroundStyle
import com.joeshannon.joetv.ui.theme.JoeCyan
import com.joeshannon.joetv.ui.theme.JoeGlowCyan
import com.joeshannon.joetv.ui.theme.JoeGlowPurple
import com.joeshannon.joetv.ui.theme.JoePurple
import com.joeshannon.joetv.weather.HourlyForecast
import com.joeshannon.joetv.weather.RadarState
import com.joeshannon.joetv.weather.WeatherScene
import com.joeshannon.joetv.weather.weatherSceneFor
import com.joeshannon.joetv.weather.loadRadarState
import com.joeshannon.joetv.weather.mostSevere
import com.joeshannon.joetv.weather.openRadarExternally



/**
 * Represents an installed application shown on the JoeTV home screen.
 */
data class JoeTvApp(
    val name: String,
    val packageName: String,
    val description: String,
    val initials: String
)


/**
 * Brush used for the cyan → purple focus border seen across JoeTV's
 * focusable cards (hero, calendar, app cards, media cards).
 */
internal fun joeFocusBrush(): Brush = Brush.linearGradient(
    colors = listOf(JoeCyan, JoePurple)
)


/**
 * Main entry point for the JoeTV launcher.
 *
 * Initializes app data, user preferences, weather, sounds,
 * and builds the complete home screen UI.
 */
@Composable
fun HomeScreen(context: Context) {

    // Live search query typed into the search bar below the hero banner.
    var searchQuery by remember {
        mutableStateOf("")
    }

    // True while the full-screen All Apps grid is showing.
    var showAllApps by remember {
        mutableStateOf(false)
    }

    // True while the full-screen theme picker is showing.
    var showSettings by remember {
        mutableStateOf(false)
    }

    // True while the full-screen background-style picker is showing.
    var showBackgrounds by remember {
        mutableStateOf(false)
    }

    // Package of the favorite currently "grabbed" for reordering, if any.
    var movingPackage by remember {
        mutableStateOf<String?>(null)
    }

    // Pressing Home re-enters the running launcher. MainActivity bumps this
    // signal so JoeTV drops whatever sub-screen was open and shows the home
    // screen, which is what the Home key does on every other launcher.
    LaunchedEffect(JoeTvNavigation.homeResetSignal) {
        showAllApps = false
        showSettings = false
        showBackgrounds = false
        movingPackage = null
        searchQuery = ""
    }

    // Restores whichever theme was picked last time, once per HomeScreen
    // launch. applyJoeTvTheme() defaults to Neon Cyan, so a fresh install
    // with nothing saved yet is unaffected.
    LaunchedEffect(Unit) {
        restoreSavedTheme(context)
        restoreSavedBackgroundStyle(context)
    }

    // True while the speech recognizer is actively listening. Drives the
    // full-screen "Listening..." overlay and the mic button's pulse.
    var isListening by remember {
        mutableStateOf(false)
    }

    // Short-lived message shown when voice search can't proceed (no
    // recognizer on the device, mic permission denied, etc.).
    var voiceStatusMessage by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(voiceStatusMessage) {
        if (voiceStatusMessage != null) {
            delay(2_600)
            voiceStatusMessage = null
        }
    }


    // Create the manager responsible for discovering installed apps.
    val appManager = remember(context.applicationContext) {
        AppManager(context.applicationContext)
    }

    // Handles all UI sounds used throughout JoeTV.
    val soundManager = remember(context.applicationContext) {
        JoeTvSoundManager(context.applicationContext)
    }

    val apps by appManager.apps
    val lifecycleOwner = LocalLifecycleOwner.current

    // Start managers when the screen appears and clean them up when leaving.
    DisposableEffect(appManager, soundManager, lifecycleOwner) {
        appManager.start()
        soundManager.playHome()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appManager.refresh()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appManager.stop()
            soundManager.release()
        }
    }

    // Persistent storage for favorites, recents, and hidden apps.
    val preferences = remember {
        context.getSharedPreferences(
            "joetv_preferences",
            Context.MODE_PRIVATE
        )
    }

    // Favorites are stored as an ORDERED list rather than a set, so the row
    // shows them in the order the user arranged rather than alphabetically.
    // The old "favorite_packages" set is still read as a fallback so an
    // existing install keeps its favorites the first time it runs this build.
    var favoriteOrder by remember {

        // Split into explicitly typed locals rather than one chained elvis
        // expression: two ?: operators in a row leave Kotlin unable to infer
        // the state type, which silently breaks every later use of this list.
        val storedOrder: List<String>? = preferences
            .getString("favorite_order", null)
            ?.split("|")
            ?.filter { it.isNotBlank() }

        val legacyOrder: List<String> = preferences
            .getStringSet(
                "favorite_packages",
                setOf(
                    "org.smarttube.stable",
                    "com.lagradost.cloudstream3",
                    "org.videolan.vlc"
                )
            )
            ?.sorted()
            ?: emptyList()

        mutableStateOf<List<String>>(
            storedOrder ?: legacyOrder
        )
    }

    // Membership lookups still want a set; order lives in favoriteOrder.
    val favoritePackages = favoriteOrder.toSet()

    var recentPackages by remember {
        mutableStateOf(
            preferences.getString(
                "recent_packages",
                ""
            )
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    var hiddenPackages by remember {
        mutableStateOf(
            preferences.getStringSet(
                "hidden_packages",
                emptySet()
            )?.toSet() ?: emptySet()
        )
    }

    var favoriteNotice by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(favoriteNotice) {
        if (favoriteNotice != null) {
            delay(1_800)
            favoriteNotice = null
        }
    }

    val visibleApps = apps.filterNot { app ->
        app.packageName in hiddenPackages
    }

    val hiddenApps = apps.filter { app ->
        app.packageName in hiddenPackages
    }

    // Ordered to match favoriteOrder, dropping any favorite whose app is no
    // longer installed or is currently hidden.
    val favoriteApps = favoriteOrder.mapNotNull { packageName ->
        visibleApps.find { app -> app.packageName == packageName }
    }

    val recentApps = recentPackages.mapNotNull { packageName ->
        visibleApps.find { app -> app.packageName == packageName }
    }

    // Apps matching the current search query. Recomputed on every keystroke;
    // fine at current app-list sizes, but worth debouncing if the list grows.
    val searchResults = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        visibleApps.filter { app ->
            app.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val isSearching = searchQuery.isNotBlank()


    /**
     * Saves an application to the Recently Opened section.
     */
    fun recordRecent(app: JoeTvApp) {
        val updatedRecents =
            (listOf(app.packageName) + recentPackages)
                .distinct()
                .take(6)

        recentPackages = updatedRecents

        preferences.edit()
            .putString(
                "recent_packages",
                updatedRecents.joinToString("|")
            )
            .apply()
    }


    // ---------------------------------------------------------------------
    // Voice search
    //
    // Tapping the mic (or pressing the remote's search/assist button, see
    // MainActivity.onKeyDown) starts JoeTV's bundled Vosk offline recognizer.
    //
    // This does NOT use android.speech.RecognizerIntent: LineageOS TV without
    // GApps has no system speech recognizer installed at all (no Google app,
    // no "Speech Services by Google"), so that intent has nothing to resolve
    // to on this device and used to fail with "No voice recognizer found on
    // this device". Vosk is bundled directly into JoeTV instead, so voice
    // search works fully offline with zero Google dependency.
    //
    // A spoken phrase that closely matches an installed app's name launches
    // that app directly; anything else is treated as a normal typed search,
    // reusing the existing "Search Results" section below.
    // ---------------------------------------------------------------------

    val voiceRecognizer = remember(context.applicationContext) {
        VoiceRecognizer(context.applicationContext)
    }

    // Unpacking the bundled model takes a moment on first launch after an
    // install, so it starts as soon as the home screen appears rather than
    // waiting for the first mic tap.
    DisposableEffect(voiceRecognizer) {
        voiceRecognizer.prepare()

        onDispose {
            voiceRecognizer.release()
        }
    }

    fun beginListening() {
        isListening = true

        voiceRecognizer.startListening(
            onResult = { spokenText ->
                isListening = false

                handleVoiceQuery(
                    spokenText = spokenText,
                    apps = visibleApps,
                    onLaunch = { app ->
                        recordRecent(app)
                        launchApp(
                            context = context,
                            packageName = app.packageName
                        )
                    },
                    onSearch = { text ->
                        searchQuery = text
                    }
                )
            },
            onError = { message ->
                isListening = false
                voiceStatusMessage = message
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            voiceStatusMessage = "Microphone permission is needed for voice search"
        }
    }

    fun startVoiceSearch() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            beginListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Lets the remote's dedicated search/mic/assist button (see
    // MainActivity.onKeyDown) start listening from anywhere in JoeTV, the
    // same way tapping the on-screen mic button does.
    LaunchedEffect(JoeTvNavigation.voiceSearchSignal) {
        if (JoeTvNavigation.voiceSearchSignal > 0) {
            startVoiceSearch()
        }
    }


    /**
     * Hides or restores an application from the home screen.
     */
    fun toggleHidden(app: JoeTvApp) {
        val wasHidden = app.packageName in hiddenPackages

        val updatedHidden =
            if (wasHidden) {
                hiddenPackages - app.packageName
            } else {
                hiddenPackages + app.packageName
            }

        hiddenPackages = updatedHidden

        preferences.edit()
            .putStringSet(
                "hidden_packages",
                updatedHidden
            )
            .apply()

        favoriteNotice = if (wasHidden) {
            "${app.name} restored to Your Apps"
        } else {
            "${app.name} hidden from JoeTV"
        }
    }


    /**
     * Persists a new favorites order.
     *
     * Both keys are written: "favorite_order" is what JoeTV reads, and the
     * legacy "favorite_packages" set is kept in sync so nothing else that
     * reads it goes stale.
     */
    fun saveFavoriteOrder(updated: List<String>) {
        favoriteOrder = updated

        preferences.edit()
            .putString(
                "favorite_order",
                updated.joinToString("|")
            )
            .putStringSet(
                "favorite_packages",
                updated.toSet()
            )
            .apply()
    }


    /**
     * Adds or removes an application from Favorites.
     *
     * New favorites are appended to the end of the row; the user promotes them
     * from there with Page Up.
     */
    fun toggleFavorite(app: JoeTvApp) {
        val wasFavorite = app.packageName in favoritePackages

        val updatedFavorites =
            if (wasFavorite) {
                favoriteOrder - app.packageName
            } else {
                favoriteOrder + app.packageName
            }

        // A card that stops being a favorite cannot stay grabbed.
        if (wasFavorite && movingPackage == app.packageName) {
            movingPackage = null
        }

        saveFavoriteOrder(updatedFavorites)

        favoriteNotice = if (wasFavorite) {
            "${app.name} removed from Favorites"
        } else {
            "${app.name} added to Favorites"
        }
    }


    /**
     * Slides a grabbed favorite one slot left (-1) or right (+1).
     *
     * The move is computed against the favorites the user can actually see, so
     * a favorite whose app is uninstalled or hidden never silently swallows a
     * keypress. The result is then folded back into the full stored order so
     * those off-screen entries keep their positions.
     */
    fun moveFavorite(app: JoeTvApp, offset: Int) {
        val visibleOrder = favoriteApps.map { it.packageName }

        val currentIndex = visibleOrder.indexOf(app.packageName)
        if (currentIndex < 0) return

        val targetIndex = currentIndex + offset
        if (targetIndex < 0 || targetIndex >= visibleOrder.size) return

        val reorderedVisible = visibleOrder.toMutableList()
        reorderedVisible.removeAt(currentIndex)
        reorderedVisible.add(targetIndex, app.packageName)

        val visibleSet = visibleOrder.toSet()
        val nextVisible = reorderedVisible.iterator()

        saveFavoriteOrder(
            favoriteOrder.map { packageName ->
                if (packageName in visibleSet) {
                    nextVisible.next()
                } else {
                    packageName
                }
            }
        )
    }


    /**
     * Grabs a favorite for reordering, or drops the one already grabbed.
     */
    fun toggleMove(app: JoeTvApp) {
        movingPackage = if (movingPackage == app.packageName) {
            favoriteNotice = "${app.name} moved"
            null
        } else {
            app.packageName
        }
    }

    // The All Apps grid replaces the home screen rather than layering over it,
    // and is declared here so it reuses the same app list, preferences and
    // launch helpers instead of building a second copy of all that state.
    if (showAllApps) {
        AllAppsScreen(
            context = context,
            soundManager = soundManager,
            apps = visibleApps,
            hiddenApps = hiddenApps,
            favoritePackages = favoritePackages,
            hiddenPackages = hiddenPackages,
            onOpen = { app ->
                recordRecent(app)
                launchApp(
                    context = context,
                    packageName = app.packageName
                )
            },
            onToggleFavorite = { app ->
                toggleFavorite(app)
            },
            onToggleHidden = { app ->
                toggleHidden(app)
            },
            onExit = {
                showAllApps = false
            }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            context = context,
            onExit = {
                showSettings = false
            }
        )
        return
    }

    if (showBackgrounds) {
        BackgroundsScreen(
            context = context,
            onExit = {
                showBackgrounds = false
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JoeBackgroundBase)
    ) {
        JoeTvMovingBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            item {
                JoeTvHero(
                    context = context
                )
            }

            item {
                JoeTvSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    isListening = isListening,
                    onVoiceSearch = { startVoiceSearch() }
                )
            }

            if (!isSearching) {
                item {
                    JoeTvHomeActions(
                        appCount = visibleApps.size,
                        onAllApps = {
                            showAllApps = true
                        },
                        onSettings = {
                            showSettings = true
                        },
                        onBackgrounds = {
                            showBackgrounds = true
                        }
                    )
                }
            }

            if (isSearching) {
                item {
                    SectionHeader(
                        title = "Search Results",
                        subtitle = "Showing apps matching \"$searchQuery\""
                    )
                }

                item {
                    if (searchResults.isEmpty()) {
                        EmptySearchResultsCard(query = searchQuery)
                    } else {
                        AppRow(
                            context = context,
                            soundManager = soundManager,
                            apps = searchResults,
                            favoritePackages = favoritePackages,
                            onFocused = { },
                            onOpen = { app ->
                                recordRecent(app)
                                launchApp(
                                    context = context,
                                    packageName = app.packageName
                                )
                            },
                            onToggleFavorite = { app ->
                                toggleFavorite(app)
                            },
                            onToggleHidden = { app ->
                                toggleHidden(app)
                            }
                        )
                    }
                }
            } else {
                item {
                    SectionHeader(
                        title = "Favorites",
                        subtitle =
                            if (movingPackage != null) {
                                "Moving  •  Left / Right to reposition  •  OK to drop"
                            } else {
                                "Page Up to move  •  Page Down to add or remove"
                            }
                    )
                }

                item {
                    if (favoriteApps.isEmpty()) {
                        EmptyFavoritesCard()
                    } else {
                        AppRow(
                            context = context,
                            soundManager = soundManager,
                            apps = favoriteApps,
                            favoritePackages = favoritePackages,
                            canReorder = true,
                            movingPackage = movingPackage,
                            onToggleMove = { app ->
                                toggleMove(app)
                            },
                            onMove = { app, offset ->
                                moveFavorite(app, offset)
                            },
                            onFocused = { },
                            onOpen = { app ->
                                recordRecent(app)
                                launchApp(
                                    context = context,
                                    packageName = app.packageName
                                )
                            },
                            onToggleFavorite = { app ->
                                toggleFavorite(app)
                            },
                            onToggleHidden = { app ->
                                toggleHidden(app)
                            }
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Recently Opened",
                        subtitle = "Jump back into your latest apps"
                    )
                }

                item {
                    if (recentApps.isEmpty()) {
                        EmptyRecentAppsCard()
                    } else {
                        AppRow(
                            context = context,
                            soundManager = soundManager,
                            apps = recentApps,
                            favoritePackages = favoritePackages,
                            onFocused = { },
                            onOpen = { app ->
                                recordRecent(app)
                                launchApp(
                                    context = context,
                                    packageName = app.packageName
                                )
                            },
                            onToggleFavorite = { app ->
                                toggleFavorite(app)
                            },
                            onToggleHidden = { app ->
                                toggleHidden(app)
                            }
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Continue Watching",
                        subtitle = "Jump back into your media"
                    )
                }

                item {
                    ContinueWatchingRow()
                }
            }

            item {
                JoeTvFooter()
            }
        }

        favoriteNotice?.let { message ->
            FavoriteNoticeBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
            )
        }

        voiceStatusMessage?.let { message ->
            FavoriteNoticeBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
            )
        }

        if (isListening) {
            JoeTvVoiceListeningOverlay()
        }
    }
}

@Composable
private fun FavoriteNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21A2030))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(
                horizontal = 24.dp,
                vertical = 14.dp
            )
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Hand-drawn magnifying-glass icon for the search bar. Avoids pulling in an
 * icon font/library for a single glyph.
 */
@Composable
private fun JoeTvSearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.7f)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.2.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(
            size.width * 0.42f,
            size.height * 0.42f
        )

        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth
            )
        )

        val handleStart = Offset(
            center.x + radius * 0.72f,
            center.y + radius * 0.72f
        )
        val handleEnd = Offset(
            size.width * 0.86f,
            size.height * 0.86f
        )

        drawLine(
            color = tint,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeWidth
        )
    }
}


/**
 * Pill-shaped search bar shown below the hero banner. Focusing it with the
 * D-pad brings up the on-screen keyboard; typing filters installed apps by
 * name live via the caller's `searchResults` computation.
 */
@Composable
private fun JoeTvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isListening: Boolean = false,
    onVoiceSearch: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 4.dp,
                bottom = 18.dp
            )
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(50)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(50)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .focusable()
            .padding(
                start = 20.dp,
                end = 8.dp
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            JoeTvSearchIcon(
                modifier = Modifier.size(18.dp)
            )

            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search apps, or say an app name",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    cursorBrush = joeFocusBrush(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            focused = focused || it.isFocused
                        }
                )
            }

            JoeTvMicButton(
                isListening = isListening,
                onClick = onVoiceSearch
            )
        }
    }
}


/**
 * Round microphone button that sits at the trailing edge of the search bar.
 * Tapping it (or pressing OK while it's focused) starts voice search; a
 * cyan → purple gradient and gentle pulse show while JoeTV is listening.
 */
@Composable
private fun JoeTvMicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val transition = rememberInfiniteTransition(
        label = "JoeTvMicPulse"
    )

    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "JoeTvMicPulseScale"
    )

    val scale = when {
        isListening -> pulse
        focused -> 1.1f
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (isListening) {
                    Brush.linearGradient(listOf(JoeCyan, JoePurple))
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                }
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = CircleShape
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = CircleShape
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (
                            event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter
                            )
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        JoeTvMicIcon(
            modifier = Modifier.size(16.dp),
            tint = if (isListening) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.75f)
            }
        )
    }
}


/**
 * Hand-drawn microphone glyph, in the same spirit as [JoeTvSearchIcon] --
 * avoids pulling in an icon font/library for a single icon.
 */
@Composable
private fun JoeTvMicIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        val capsuleWidth = w * 0.42f
        val capsuleHeight = h * 0.58f

        drawRoundRect(
            color = tint,
            topLeft = Offset(
                (w - capsuleWidth) / 2f,
                0f
            ),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                capsuleWidth / 2f
            )
        )

        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(
                w * 0.14f,
                capsuleHeight - h * 0.16f
            ),
            size = Size(w * 0.72f, h * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth
            )
        )

        drawLine(
            color = tint,
            start = Offset(w / 2f, capsuleHeight + h * 0.20f),
            end = Offset(w / 2f, h * 0.96f),
            strokeWidth = strokeWidth
        )

        drawLine(
            color = tint,
            start = Offset(w * 0.28f, h * 0.96f),
            end = Offset(w * 0.72f, h * 0.96f),
            strokeWidth = strokeWidth
        )
    }
}


/**
 * Full-screen overlay shown while the speech recognizer is listening.
 * Dims the launcher behind it so it's obvious voice search is active and
 * nothing else is focused while it's up.
 */
@Composable
private fun JoeTvVoiceListeningOverlay() {
    val transition = rememberInfiniteTransition(
        label = "JoeTvVoiceListening"
    )

    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "JoeTvVoiceListeningScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(JoeCyan, JoePurple))
                    ),
                contentAlignment = Alignment.Center
            ) {
                JoeTvMicIcon(
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Listening…",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Say an app name, like \"Netflix\" or \"YouTube\"",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp
            )
        }
    }
}


/**
 * Animated background displayed behind the launcher. Dispatches to whichever
 * style is currently selected on the Backgrounds screen (see
 * ui/theme/BackgroundStyle.kt).
 */
@Composable
internal fun JoeTvMovingBackground() {
    when (currentJoeTvBackgroundStyle) {
        JoeTvBackgroundStyle.NEBULA -> JoeTvNebulaBackground()
        JoeTvBackgroundStyle.AURORA -> JoeTvAuroraBackground()
        JoeTvBackgroundStyle.STARFIELD -> JoeTvStarfieldBackground()
        JoeTvBackgroundStyle.VIDEO -> JoeTvVideoBackground()
    }
}


/**
 * Original JoeTV background: two large soft-edged glow circles (cyan +
 * purple) drifting slowly past each other over a dark vertical gradient.
 */
@Composable
private fun JoeTvNebulaBackground() {
    val transition = rememberInfiniteTransition(
        label = "JoeTVBackground"
    )

    val blueX by transition.animateFloat(
        initialValue = -180f,
        targetValue = 210f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlueX"
    )

    val blueY by transition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlueY"
    )

    val purpleX by transition.animateFloat(
        initialValue = 190f,
        targetValue = -150f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PurpleX"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JoeBackgroundTop,
                        JoeBackgroundMid,
                        JoeBackgroundBottom
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = blueX.dp,
                    y = blueY.dp
                )
                .size(470.dp)
                .background(
                    color = JoeGlowCyan,
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = purpleX.dp,
                    y = 110.dp
                )
                .size(430.dp)
                .background(
                    color = JoeGlowPurple,
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )
    }
}


/**
 * Soft horizontal bands of color drifting sideways and fading in and out,
 * like an aurora seen through a window -- calmer than Nebula's two big
 * circles.
 */
@Composable
private fun JoeTvAuroraBackground() {
    val transition = rememberInfiniteTransition(
        label = "JoeTvAurora"
    )

    val drift by transition.animateFloat(
        initialValue = -70f,
        targetValue = 70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuroraDrift"
    )

    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuroraGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JoeBackgroundTop,
                        JoeBackgroundMid,
                        JoeBackgroundBottom
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bands = listOf(
                Triple(JoeGlowCyan, 0.16f, 1f),
                Triple(JoeGlowPurple, 0.42f, -1f),
                Triple(JoeGlowCyan, 0.70f, 1f)
            )

            bands.forEach { (color, heightFraction, direction) ->
                drawRoundRect(
                    color = color.copy(alpha = (color.alpha * glow).coerceIn(0f, 1f)),
                    topLeft = Offset(
                        -size.width * 0.15f + drift * direction,
                        size.height * heightFraction
                    ),
                    size = Size(
                        size.width * 1.3f,
                        size.height * 0.20f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.height * 0.10f
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )
    }
}


/**
 * A quiet field of slowly twinkling stars over the same dark gradient --
 * the "clean" option, with none of the large glow shapes the other two
 * styles use.
 */
@Composable
private fun JoeTvStarfieldBackground() {
    val stars = remember {
        val random = kotlin.random.Random(20260915)
        List(70) {
            Triple(
                random.nextFloat(),
                random.nextFloat(),
                0.6f + random.nextFloat() * 0.5f
            )
        }
    }

    val transition = rememberInfiniteTransition(
        label = "JoeTvStarfield"
    )

    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Twinkle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JoeBackgroundTop,
                        JoeBackgroundMid,
                        JoeBackgroundBottom
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEachIndexed { index, star ->
                val (fx, fy, baseAlpha) = star
                val phase = (index % 5) / 5f
                val wave = kotlin.math.sin(
                    (twinkle + phase) * (2 * Math.PI).toFloat()
                )
                val alpha = (baseAlpha * (0.5f + 0.5f * wave)).coerceIn(0f, 1f)

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 1.6.dp.toPx(),
                    center = Offset(size.width * fx, size.height * fy)
                )
            }
        }
    }
}

/**
 * Looks for the first video file in the app's private "wallpapers" folder
 * on external storage -- no runtime permission needed since it's an
 * app-specific directory (scoped storage). Swap the loop by pushing a new
 * file there with adb; no rebuild required. Sorted by name so a specific
 * file can be pinned first if more than one ever lands in the folder.
 *
 *   adb push myloop.mp4 /sdcard/Android/data/com.joeshannon.joetv/files/wallpapers/myloop.mp4
 *
 * Stick to H.264 (avoid VP9/AV1) -- see the Pi 5 codec notes elsewhere in
 * this project. HEVC works but only has partial hardware decode support.
 */
private val wallpaperVideoExtensions = setOf("mp4", "m4v", "mkv", "webm")

private fun findWallpaperVideoFile(context: Context): File? {
    val dir = context.getExternalFilesDir("wallpapers") ?: return null
    if (!dir.isDirectory) {
        return null
    }

    return dir.listFiles { file ->
        file.isFile && file.extension.lowercase() in wallpaperVideoExtensions
    }
        ?.sortedBy { it.name }
        ?.firstOrNull()
}

/**
 * Looping muted video, cropped to fill like a desktop live-wallpaper tool.
 * Falls back to Nebula if no video has been pushed to the wallpapers folder
 * yet, so picking this style never produces a black screen.
 */
@Composable
private fun JoeTvVideoBackground() {
    val context = LocalContext.current
    val videoFile = remember { findWallpaperVideoFile(context) }

    if (videoFile == null) {
        JoeTvNebulaBackground()
        return
    }

    val exoPlayer = remember(videoFile) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Same bottom-darkening treatment as the other styles, so card text
        // and focus borders stay legible over whatever the video shows.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )
    }
}

/**
 * Chooses a hero layout based on the active display aspect ratio.
 *
 * Native Tab S9+ landscape is approximately 16:10.
 * JoeTV TV mode is forced to 1920x1080, which is 16:9.
 */
@Composable
private fun JoeTvHero(
    context: Context
) {
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.coerceAtLeast(1)
    val screenHeight = configuration.screenHeightDp.coerceAtLeast(1)
    val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

    val isTvLayout =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                aspectRatio >= 1.59f

    if (isTvLayout) {
        JoeTvHeroTv(
            context = context,
        )
    } else {
        JoeTvHeroTablet(context = context)
    }
}


/**
 * Shared time and weather state used by both hero layouts.
 */
@Composable
private fun rememberHeroState(): Pair<LocalDateTime, String> {
    var currentTime by remember {
        mutableStateOf(LocalDateTime.now())
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(30_000)
        }
    }

    val greeting = when (currentTime.hour) {
        in 5..11 -> "Good morning, Joe"
        in 12..16 -> "Good afternoon, Joe"
        else -> "Good evening, Joe"
    }

    return Pair(currentTime, greeting)
}


/**
 * Compact 16:9 hero used when JoeTV is running at 1920x1080.
 */
@Composable
private fun JoeTvHeroTv(
    context: Context
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val (currentTime, greeting) = rememberHeroState()
    val radarState = rememberRadarState(context)

    val googleCalendarApi = remember(context.applicationContext) {
        GoogleCalendarApi(context.applicationContext)
    }

    val isGoogleCalendarConnected =
        googleCalendarApi.isConnected()

    // Bumped by clicking the calendar card, and whenever JoeTV returns to the
    // foreground. Restarting produceState is what forces an immediate fetch.
    var calendarRefreshKey by remember {
        mutableStateOf(0)
    }

    val heroLifecycleOwner = LocalLifecycleOwner.current

    // Coming back from another app is exactly when the calendar is most likely
    // to be stale, and it costs one request.
    DisposableEffect(heroLifecycleOwner) {
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
                calendarRefreshKey++
            }
        }

        heroLifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            heroLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val nextEvent by produceState<GoogleCalendarEvent?>(
        initialValue = null,
        key1 = isGoogleCalendarConnected,
        key2 = calendarRefreshKey
    ) {
        if (!isGoogleCalendarConnected) {
            value = null
            return@produceState
        }

        // Polls on the same pattern as the weather card above. Without this the
        // card only updated when the hero re-entered composition, so scrolling
        // away and back was the only way to refresh it.
        //
        // Five minutes also covers the case of an event simply starting: once
        // it is in the past the API returns the one after it, and the card
        // moves on by itself.
        while (true) {
            runCatching {
                googleCalendarApi.getNextEvent()
            }.onSuccess { event ->
                value = event
            }.onFailure { error ->
                // Keep showing the last known event rather than blanking the
                // card over one failed request -- Wi-Fi drops, tokens hiccup.
                println("JOETV_CALENDAR_REFRESH_FAILED=${error.message}")
            }

            delay(5 * 60 * 1_000L)
        }
    }

    val timeText = currentTime.format(
        DateTimeFormatter.ofPattern("h:mm a")
    )

    val dateText = currentTime.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp)
            .padding(
                start = 38.dp,
                end = 38.dp,
                top = 18.dp,
                bottom = 12.dp
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xF026324A),
                        Color(0xDD161C2A),
                        Color(0xC010141E)
                    )
                )
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(26.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(26.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 28.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1.2f)
            ) {
                Text(
                    text = "JOETV",
                    color = JoeCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = greeting,
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Everything you want to watch, all in one place.",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(15.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroPill(
                        text = timeText,
                        compact = true
                    )

                    HeroPill(
                        text = dateText,
                        compact = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Weather moved here (used to be Calendar's spot) since it now
            // shows current + hourly conditions and wants the flexible,
            // wider slot. Calendar took weather's old fixed-width spot,
            // widened a bit so a long event title has room to read in full.
            RadarHeroCard(
                state = radarState,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 220.dp)
                    .height(135.dp),
                labelFontSize = 15.sp,
                detailsFontSize = 10.sp,
                alertFontSize = 10.sp,
                tempFontSize = 26.sp,
                hourlyFontSize = 10.sp,
                cornerRadius = 22.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            CalendarHeroCard(
                event = nextEvent,
                permissionGranted = isGoogleCalendarConnected,
                modifier = Modifier.width(250.dp),
                onClick = {
                    calendarRefreshKey++
                }
            )
        }
    }
}

/**
 * Calendar preview shown in the TV hero.
 *
 * This currently displays a connection placeholder. The visual card is ready
 * for Google Calendar data once sign-in and API access are added.
 */
@Composable
private fun CalendarHeroCard(
    event: GoogleCalendarEvent?,
    permissionGranted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val eventTime = event?.let {
        java.time.Instant
            .ofEpochMilli(it.startTimeMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    val countdown = event?.let {
        val minutes =
            ((it.startTimeMillis - System.currentTimeMillis()) / 60_000L)
                .coerceAtLeast(0)

        when {
            minutes == 0L -> "Starting now"
            minutes < 60L -> "Starts in $minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60

                if (remainingMinutes == 0L) {
                    "Starts in $hours hr"
                } else {
                    "Starts in ${hours}h ${remainingMinutes}m"
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(135.dp)
            .graphicsLayer {
                val scale = if (focused) 1.03f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF253755),
                        Color(0xFF18243A)
                    )
                )
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(22.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (
                            event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter
                            )
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NEXT UP",
                color = JoeCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )

            Column {
                Text(
                    text = when {
                        !permissionGranted -> "Calendar"
                        event != null -> event.title
                        else -> "Nothing scheduled"
                    },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = when {
                        !permissionGranted -> "Permission required"
                        event != null -> eventTime.orEmpty()
                        else -> "You're all caught up"
                    },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = when {
                    !permissionGranted -> "Press OK to connect"
                    event != null -> countdown.orEmpty()
                    else -> "Enjoy your day"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Original spacious hero used at the tablet's native aspect ratio.
 */
@Composable
private fun JoeTvHeroTablet(context: Context) {
    var focused by remember {
        mutableStateOf(false)
    }

    val (currentTime, greeting) = rememberHeroState()
    val radarState = rememberRadarState(context)

    val timeText = currentTime.format(
        DateTimeFormatter.ofPattern("h:mm a")
    )

    val dateText = currentTime.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 18.dp
            )
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xF026324A),
                        Color(0xDD161C2A),
                        Color(0xC010141E)
                    )
                )
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(30.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(30.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 38.dp,
                    end = 390.dp
                )
        ) {
            Text(
                text = "JOETV",
                color = JoeCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = greeting,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "Everything you want to watch, all in one place.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroPill(timeText)
                HeroPill(dateText)
            }
        }

        RadarHeroCard(
            state = radarState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
                .width(400.dp)
                .height(230.dp),
            labelFontSize = 16.sp,
            detailsFontSize = 13.sp,
            tempFontSize = 40.sp,
            hourlyFontSize = 12.sp,
            showLocationFooter = true
        )
    }
}


/**
 * Polls the radar repository on a slow cadence (RainViewer itself only
 * refreshes about every 10 minutes) and keeps the previous frame and
 * weather numbers on screen across a failed refresh instead of blanking
 * the card.
 */
@Composable
private fun rememberRadarState(context: Context): RadarState? {
    var state by remember { mutableStateOf<RadarState?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            state = loadRadarState(context, state)
            delay(8 * 60 * 1_000L)
        }
    }

    return state
}


/**
 * Live radar + current-conditions card. Refreshes itself in the background,
 * badges a red severe-weather alert when one is active for the resolved
 * location, and taps through to whatever the device treats as the default
 * handler for that spot.
 *
 * Sizing comes entirely from [modifier] -- callers decide whether it gets a
 * fixed size (tablet) or a flexible one (TV hero row), which is what lets
 * it take the wider slot now that it shows more than just a radar image.
 */
@Composable
private fun RadarHeroCard(
    state: RadarState?,
    modifier: Modifier = Modifier,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    detailsFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    alertFontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    tempFontSize: androidx.compose.ui.unit.TextUnit = 30.sp,
    hourlyFontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    cornerRadius: androidx.compose.ui.unit.Dp = 30.dp,
    showLocationFooter: Boolean = false
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val alert = state?.alerts?.mostSevere()
    val minutesAgo = state?.generatedAtMillis?.let { generatedAt ->
        if (generatedAt <= 0L) null else (System.currentTimeMillis() - generatedAt) / 60_000L
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF10182B))
            .border(
                width = if (alert != null) 2.dp else 1.dp,
                color = if (alert != null) {
                    Color(0xFFEF4444)
                } else {
                    Color.White.copy(alpha = 0.18f)
                },
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(cornerRadius)
                    )
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable {
                state?.let { openRadarExternally(context, it) }
            }
    ) {
        if (state?.image != null) {
            Image(
                bitmap = state.image.asImageBitmap(),
                contentDescription = "Weather radar near ${state.locationLabel}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim top-to-bottom so both the temp readout up top and the
            // hourly strip down below stay legible over whatever the radar
            // colors happen to be underneath them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF26324A), Color(0xFF161C2A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading weather…",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = detailsFontSize
                )
            }
        }

        // Current temp + condition + high/low, top-left.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp, end = 90.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state?.currentTemperature?.let { "$it°" } ?: "--°",
                color = Color.White,
                fontSize = tempFontSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = state?.conditionLabel ?: "Loading…",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = detailsFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (state?.highTemperature != null && state.lowTemperature != null) {
                    Text(
                        text = "H ${state.highTemperature}°  •  L ${state.lowTemperature}°",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = detailsFontSize
                    )
                }
            }
        }

        if (alert != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFEF4444))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.event.uppercase(),
                    color = Color.White,
                    fontSize = alertFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Next few hours, bottom.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            if (state != null && state.hourly.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.hourly.take(6)) { hour ->
                        HourlyForecastChip(
                            hour = hour,
                            fontSize = hourlyFontSize
                        )
                    }
                }
            }

            if (showLocationFooter) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state?.locationLabel ?: "Radar",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = detailsFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when {
                        minutesAgo == null -> "Live radar"
                        minutesAgo <= 0L -> "Updated just now"
                        else -> "Updated ${minutesAgo}m ago"
                    },
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = detailsFontSize
                )
            }
        }
    }
}


/**
 * One hour of the forecast strip: hour label, a small dot colored by
 * condition, and the temperature.
 */
@Composable
private fun HourlyForecastChip(
    hour: HourlyForecast,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 7.dp, vertical = 6.dp)
    ) {
        Text(
            text = hour.label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = fontSize
        )

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(sceneDotColor(weatherSceneFor(hour.weatherCode)))
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "${hour.temperature}°",
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}


/**
 * Small color cue for the hourly strip's condition dot -- not a full icon
 * set, just enough to tell "clear" from "storm" at a glance.
 */
private fun sceneDotColor(scene: WeatherScene): Color = when (scene) {
    WeatherScene.CLEAR -> Color(0xFFFFDC72)
    WeatherScene.PARTLY_CLOUDY, WeatherScene.CLOUDY -> Color(0xFFB8C4D9)
    WeatherScene.FOG -> Color(0xFFCBD5E1)
    WeatherScene.RAIN -> Color(0xFF60A5FA)
    WeatherScene.STORM -> Color(0xFFFACC15)
    WeatherScene.SNOW -> Color(0xFFE0F2FE)
}

@Composable
private fun HeroPill(
    text: String,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = if (compact) 11.sp else 13.sp
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.padding(
            start = 48.dp,
            end = 48.dp,
            top = 18.dp,
            bottom = 5.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(joeFocusBrush())
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 14.sp
            )
        }
    }
}


/**
 * Displays a horizontally scrolling row of application cards.
 */
@Composable
internal fun AppRow(
    context: Context,
    soundManager: JoeTvSoundManager,
    apps: List<JoeTvApp>,
    favoritePackages: Set<String>,
    hiddenPackages: Set<String> = emptySet(),
    canReorder: Boolean = false,
    movingPackage: String? = null,
    onToggleMove: (JoeTvApp) -> Unit = { },
    onMove: (JoeTvApp, Int) -> Unit = { _, _ -> },
    onFocused: (JoeTvApp) -> Unit,
    onOpen: (JoeTvApp) -> Unit,
    onToggleFavorite: (JoeTvApp) -> Unit,
    onToggleHidden: (JoeTvApp) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(
            start = 48.dp,
            end = 48.dp,
            top = 10.dp,
            bottom = 28.dp
        )
    ) {
        items(
            items = apps,
            key = { app -> app.packageName }
        ) { app ->
            JoeTvAppCard(
                context = context,
                soundManager = soundManager,
                app = app,
                isFavorite = app.packageName in favoritePackages,
                isHidden = app.packageName in hiddenPackages,
                canReorder = canReorder,
                isMoving = movingPackage == app.packageName,
                onToggleMove = {
                    onToggleMove(app)
                },
                onMove = { offset ->
                    onMove(app, offset)
                },
                onFocused = {
                    onFocused(app)
                },
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


/**
 * Displays a single application card and handles focus,
 * launching, favorites, and hide shortcuts.
 */
@Composable
internal fun JoeTvAppCard(
    context: Context,
    soundManager: JoeTvSoundManager,
    app: JoeTvApp,
    isFavorite: Boolean,
    isHidden: Boolean,
    fillWidth: Boolean = false,
    canReorder: Boolean = false,
    isMoving: Boolean = false,
    onToggleMove: () -> Unit = { },
    onMove: (Int) -> Unit = { },
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val appIcon = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(
                    width = 96,
                    height = 96
                )
                .asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .then(
                if (fillWidth) {
                    Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                } else {
                    Modifier.size(
                        width = 215.dp,
                        height = 124.dp
                    )
                }
            )
            .graphicsLayer {
                // A grabbed card lifts further off the row than a merely
                // focused one, so it is obvious the card itself is moving.
                val scale = when {
                    isMoving -> 1.12f
                    focused -> 1.07f
                    else -> 1f
                }
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(19.dp))
            .background(
                when {
                    isMoving -> Color(0xFF16394A)
                    focused -> Color(0xFF36425E)
                    else -> Color(0xE0191D27)
                }
            )
            .then(
                if (isMoving) {
                    Modifier.border(
                        border = BorderStroke(3.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(19.dp)
                    )
                } else if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(19.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(19.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused

                if (it.isFocused) {
                    soundManager.playMove()
                    onFocused()
                }
            }
            // JoeTV controller shortcuts:
            // Page Up      = Grab / drop a favorite for reordering
            // Left / Right = Move a grabbed card along the row
            // Page Down    = Favorite / unfavorite
            // Bookmark / Y = Favorite / unfavorite
            // X            = Hide or restore
            // DPAD and OK otherwise continue through normal TV focus handling.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val nativeKeyCode =
                        event.key.nativeKeyCode

                    when {
                        // Page Up picks a favorite up and puts it back down.
                        // One button does both, so there is no mode to learn
                        // and no menu to open.
                        canReorder &&
                                nativeKeyCode ==
                                android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                            onToggleMove()
                            true
                        }

                        // While a card is grabbed, Left/Right drag the card
                        // itself instead of moving focus off it.
                        isMoving &&
                                nativeKeyCode ==
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onMove(-1)
                            true
                        }

                        isMoving &&
                                nativeKeyCode ==
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onMove(1)
                            true
                        }

                        // OK drops the card rather than launching the app, so
                        // a card being moved can never be opened by accident.
                        isMoving && (
                                nativeKeyCode ==
                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                        nativeKeyCode ==
                                        android.view.KeyEvent.KEYCODE_ENTER
                                ) -> {
                            onToggleMove()
                            true
                        }

                        // Back also drops the card, since that is the other
                        // reflex for "get me out of this mode".
                        isMoving &&
                                nativeKeyCode ==
                                android.view.KeyEvent.KEYCODE_BACK -> {
                            onToggleMove()
                            true
                        }

                        // Every other key is swallowed while a card is
                        // grabbed. Up/Down would carry focus out of the row
                        // and strand the card mid-move, and favoriting or
                        // hiding it would yank it out from under the user.
                        // Grab mode ends deliberately: OK, Page Up, or Back.
                        isMoving -> true

                        nativeKeyCode ==
                                android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                            onToggleFavorite()
                            true
                        }

                        event.key == Key.Bookmark ||
                                event.key == Key.ButtonY -> {
                            onToggleFavorite()
                            true
                        }

                        event.key == Key.ButtonX -> {
                            onToggleHidden()
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusable()
            .clickable {
                soundManager.playSelect {
                    onOpen()
                }
            }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "${app.name} icon",
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = app.initials,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isMoving) {
            Text(
                text = "◀  ▶",
                modifier = Modifier.align(Alignment.TopEnd),
                color = JoeCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (isFavorite) {
            Text(
                text = "★",
                modifier = Modifier.align(Alignment.TopEnd),
                color = Color(0xFFFFD54F),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isHidden) {
            Text(
                text = "HIDDEN",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (isMoving) {
                        "Left / Right to move  •  OK to drop"
                    } else {
                        app.description
                    },
                color =
                    if (isMoving) {
                        JoeCyan.copy(alpha = 0.95f)
                    } else {
                        Color.White.copy(alpha = 0.50f)
                    },
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyFavoritesCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 10.dp,
                bottom = 28.dp
            )
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Highlight an app and press Page Down to add it.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun EmptyRecentAppsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 10.dp,
                bottom = 28.dp
            )
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Open an app and it will appear here.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun EmptySearchResultsCard(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 10.dp,
                bottom = 28.dp
            )
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "No apps match \"$query\".",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp
        )
    }
}


/**
 * Placeholder media section for future streaming integrations.
 */
@Composable
private fun ContinueWatchingRow() {
    val mediaItems = listOf(
        "Recently Played",
        "Local Videos",
        "Recommended",
        "Watch Later"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(
            start = 48.dp,
            end = 48.dp,
            top = 10.dp,
            bottom = 30.dp
        )
    ) {
        items(mediaItems) { title ->
            MediaCard(title = title)
        }
    }
}

@Composable
private fun MediaCard(title: String) {
    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .size(
                width = 260.dp,
                height = 140.dp
            )
            .graphicsLayer {
                val scale = if (focused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(19.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF242B3D),
                        Color(0xFF151924)
                    )
                )
            )
            .then(
                if (focused) {
                    Modifier.border(
                        border = BorderStroke(2.dp, joeFocusBrush()),
                        shape = RoundedCornerShape(19.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(19.dp)
                    )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
            .padding(18.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.BottomStart),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Footer displayed at the bottom of the launcher.
 */
@Composable
private fun JoeTvFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 18.dp,
                bottom = 40.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = 24.dp,
                vertical = 20.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "JoeTV",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Custom entertainment system",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 13.sp
            )
        }

        Text(
            text = "Version 2.0",
            color = JoePurple,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Launches the selected application.
 *
 * Attempts to use the Android TV launch intent first,
 * then falls back to the standard Android launch intent.
 */
private fun launchApp(
    context: Context,
    packageName: String
) {
    if (packageName.isBlank()) return

    val launchIntent =
        context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)

    launchIntent?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}


/**
 * Interprets a phrase returned by the speech recognizer.
 *
 * If it closely matches an installed app's name, that app is launched
 * directly -- this is the common case, e.g. saying "Netflix" or "YouTube".
 * Otherwise the phrase is handed off as a normal typed search query, so
 * anything the recognizer heard still does something useful via the
 * existing "Search Results" section.
 */
private fun handleVoiceQuery(
    spokenText: String,
    apps: List<JoeTvApp>,
    onLaunch: (JoeTvApp) -> Unit,
    onSearch: (String) -> Unit
) {
    val cleaned = spokenText.trim()
    if (cleaned.isBlank()) return

    val bestMatch = apps
        .map { app -> app to voiceMatchScore(cleaned, app.name) }
        .maxByOrNull { (_, score) -> score }

    if (bestMatch != null && bestMatch.second >= 0.6) {
        onLaunch(bestMatch.first)
    } else {
        onSearch(cleaned)
    }
}


/**
 * Similarity score (0.0-1.0) between a spoken phrase and an app name.
 *
 * Speech recognition is rarely a perfect transcription, so this is
 * deliberately forgiving: an exact match or one phrase fully containing the
 * other (e.g. "you tube" heard for "YouTube") scores highly outright, and
 * anything else falls back to normalized Levenshtein distance.
 */
private fun voiceMatchScore(
    spoken: String,
    appName: String
): Double {
    val a = spoken.lowercase().trim()
    val b = appName.lowercase().trim()

    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    if (b.contains(a) || a.contains(b)) return 0.85

    val distance = levenshteinDistance(a, b)
    val maxLength = maxOf(a.length, b.length)

    return 1.0 - (distance.toDouble() / maxLength)
}


/**
 * Classic edit-distance calculation: the minimum number of single-character
 * insertions, deletions, or substitutions needed to turn [s1] into [s2].
 */
private fun levenshteinDistance(
    s1: String,
    s2: String
): Int {
    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

    for (i in 0..s1.length) dp[i][0] = i
    for (j in 0..s2.length) dp[0][j] = j

    for (i in 1..s1.length) {
        for (j in 1..s2.length) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1

            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }

    return dp[s1.length][s2.length]
}

private fun openBluetoothSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(fallbackIntent)
    }
}

private fun openSoundAssistant(context: Context) {
    val packageName = "com.samsung.android.soundassistant"

    val launchIntent =
        context.packageManager.getLaunchIntentForPackage(packageName)

    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        try {
            // Opens Sound Assistant in the Samsung Galaxy Store
            val storeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("samsungapps://ProductDetail/$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(storeIntent)
        } catch (exception: ActivityNotFoundException) {
            // Final fallback: open the Galaxy Store web listing
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://galaxystore.samsung.com/detail/$packageName"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(browserIntent)
        }
    }
}

/**
 * Row of home-screen actions sitting under the search bar.
 *
 * The full app list used to be rendered inline on the home screen, which made
 * the page long and pushed everything else below the fold. It now lives behind
 * this button instead.
 */
@Composable
private fun JoeTvHomeActions(
    appCount: Int,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onBackgrounds: () -> Unit
) {
    Row(
        modifier = Modifier.padding(
            start = 48.dp,
            end = 48.dp,
            top = 2.dp,
            bottom = 2.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JoeTvPillButton(
            label = "All Apps",
            badge = appCount.toString(),
            showGridIcon = true,
            onClick = onAllApps
        )

        Spacer(modifier = Modifier.width(12.dp))

        JoeTvPillButton(
            label = "Themes",
            onClick = onSettings
        )

        Spacer(modifier = Modifier.width(12.dp))

        JoeTvPillButton(
            label = "Backgrounds",
            onClick = onBackgrounds
        )
    }
}


/**
 * Focusable pill button used for JoeTV's screen-level actions.
 *
 * Shares the launcher's cyan → purple focus treatment so it reads as part of
 * the same surface as the app cards.
 */
@Composable
internal fun JoeTvPillButton(
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    showGridIcon: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                if (focused) {
                    joeFocusBrush()
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
                border = BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    brush =
                        if (focused) {
                            joeFocusBrush()
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.14f)
                                )
                            )
                        }
                ),
                shape = RoundedCornerShape(23.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
            .clickable {
                onClick()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showGridIcon) {
                JoeTvGridIcon(
                    tint =
                        if (focused) {
                            Color(0xFF06121A)
                        } else {
                            Color.White.copy(alpha = 0.85f)
                        }
                )

                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = label,
                color =
                    if (focused) {
                        Color(0xFF06121A)
                    } else {
                        Color.White.copy(alpha = 0.92f)
                    },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (badge != null) {
                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = badge,
                    color =
                        if (focused) {
                            Color(0xFF06121A).copy(alpha = 0.65f)
                        } else {
                            Color.White.copy(alpha = 0.42f)
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


/**
 * Small 2x2 grid glyph drawn with Canvas.
 *
 * Hand-drawn for the same reason as the search icon: it avoids pulling an
 * icon font or extended-icons dependency into the launcher for one shape.
 */
@Composable
private fun JoeTvGridIcon(
    tint: Color
) {
    Canvas(
        modifier = Modifier.size(15.dp)
    ) {
        val cell = size.width * 0.40f
        val gap = size.width - (cell * 2f)

        listOf(
            Offset(0f, 0f),
            Offset(cell + gap, 0f),
            Offset(0f, cell + gap),
            Offset(cell + gap, cell + gap)
        ).forEach { corner ->
            drawRoundRect(
                color = tint,
                topLeft = corner,
                size = Size(cell, cell),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    cell * 0.28f,
                    cell * 0.28f
                )
            )
        }
    }
}
