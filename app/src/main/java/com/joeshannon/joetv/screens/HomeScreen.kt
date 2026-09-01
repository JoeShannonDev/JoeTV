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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import java.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
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
import android.speech.RecognizerIntent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.joeshannon.joetv.calendar.GoogleCalendarConnectScreen
import com.joeshannon.joetv.calendar.GoogleCalendarApi
import com.joeshannon.joetv.calendar.GoogleCalendarEvent
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeBackgroundBottom
import com.joeshannon.joetv.ui.theme.JoeBackgroundMid
import com.joeshannon.joetv.ui.theme.JoeBackgroundTop
import com.joeshannon.joetv.ui.theme.JoeCyan
import com.joeshannon.joetv.ui.theme.JoeGlowCyan
import com.joeshannon.joetv.ui.theme.JoeGlowPurple
import com.joeshannon.joetv.ui.theme.JoePurple



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

    // Package of the favorite currently "grabbed" for reordering, if any.
    var movingPackage by remember {
        mutableStateOf<String?>(null)
    }

    // Pressing Home re-enters the running launcher. MainActivity bumps this
    // signal so JoeTV drops whatever sub-screen was open and shows the home
    // screen, which is what the Home key does on every other launcher.
    LaunchedEffect(JoeTvNavigation.homeResetSignal) {
        showAllApps = false
        movingPackage = null
        searchQuery = ""
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
    // MainActivity.onKeyDown) starts Android's built-in speech recognizer.
    // A spoken phrase that closely matches an installed app's name launches
    // that app directly; anything else is treated as a normal typed search,
    // reusing the existing "Search Results" section below.
    // ---------------------------------------------------------------------

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false

        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
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
            }
        }
    }

    fun launchVoiceRecognizer() {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an app name, like \"Netflix\"")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        if (recognizerIntent.resolveActivity(context.packageManager) != null) {
            isListening = true
            speechRecognizerLauncher.launch(recognizerIntent)
        } else {
            voiceStatusMessage = "No voice recognizer found on this device"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVoiceRecognizer()
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
            launchVoiceRecognizer()
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
 * Animated background displayed behind the launcher.
 */
@Composable
internal fun JoeTvMovingBackground() {
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

private data class WeatherInfo(
    val temperature: Int,
    val apparentTemperature: Int,
    val high: Int,
    val low: Int,
    val weatherCode: Int,
    val isDay: Boolean
)

private enum class WeatherScene {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    STORM,
    SNOW
}





private fun weatherSceneFor(code: Int): WeatherScene = when (code) {
    0 -> WeatherScene.CLEAR
    1, 2 -> WeatherScene.PARTLY_CLOUDY
    3 -> WeatherScene.CLOUDY
    45, 48 -> WeatherScene.FOG
    51, 53, 55, 56, 57,
    61, 63, 65, 66, 67,
    80, 81, 82 -> WeatherScene.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherScene.SNOW
    95, 96, 99 -> WeatherScene.STORM
    else -> WeatherScene.PARTLY_CLOUDY
}

private fun weatherLabel(scene: WeatherScene): String = when (scene) {
    WeatherScene.CLEAR -> "Clear"
    WeatherScene.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherScene.CLOUDY -> "Cloudy"
    WeatherScene.FOG -> "Foggy"
    WeatherScene.RAIN -> "Rain"
    WeatherScene.STORM -> "Thunderstorms"
    WeatherScene.SNOW -> "Snow"
}


/**
 * Downloads the current weather from the Open-Meteo API.
 */
private suspend fun loadWeather(): WeatherInfo? = withContext(Dispatchers.IO) {
    /*
     * These coordinates are Manhattan, Kansas.
     * Change them later if you want JoeTV tied to another home location.
     */
    val latitude = 39.1836
    val longitude = -96.5717

    runCatching {
        val endpoint =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude" +
                    "&longitude=$longitude" +
                    "&current=temperature_2m,apparent_temperature,is_day,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=auto" +
                    "&forecast_days=1"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@runCatching null
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")

            WeatherInfo(
                temperature = current.getDouble("temperature_2m").toInt(),
                apparentTemperature =
                    current.getDouble("apparent_temperature").toInt(),
                high = daily.getJSONArray("temperature_2m_max")
                    .getDouble(0)
                    .toInt(),
                low = daily.getJSONArray("temperature_2m_min")
                    .getDouble(0)
                    .toInt(),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
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
        JoeTvHeroTablet()
    }
}


/**
 * Shared time and weather state used by both hero layouts.
 */
@Composable
private fun rememberHeroState(): Triple<LocalDateTime, WeatherInfo?, String> {
    var currentTime by remember {
        mutableStateOf(LocalDateTime.now())
    }

    val weather by produceState<WeatherInfo?>(initialValue = null) {
        while (true) {
            value = loadWeather()
            delay(30 * 60 * 1_000L)
        }
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

    return Triple(currentTime, weather, greeting)
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

    val (currentTime, weather, greeting) = rememberHeroState()

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

            CalendarHeroCard(
                event = nextEvent,
                permissionGranted = isGoogleCalendarConnected,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 175.dp)
                    .height(135.dp),
                onClick = {
                    calendarRefreshKey++
                }
            )

            Spacer(modifier = Modifier.width(14.dp))

            WeatherHeroCard(
                weather = weather,
                cardWidth = 175.dp,
                cardHeight = 135.dp,
                temperatureFontSize = 29.sp,
                conditionFontSize = 11.sp,
                detailsFontSize = 9.sp,
                cornerRadius = 22.dp
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
private fun JoeTvHeroTablet() {
    var focused by remember {
        mutableStateOf(false)
    }

    val (currentTime, weather, greeting) = rememberHeroState()

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
                HeroPill(
                    weather?.let {
                        "${it.temperature}° • ${weatherLabel(weatherSceneFor(it.weatherCode))}"
                    } ?: "Weather loading"
                )
            }
        }

        WeatherHeroCard(
            weather = weather,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
        )
    }
}


/**
 * Displays the weather card shown on the right side
 * of the hero banner.
 */
@Composable
private fun WeatherHeroCard(
    weather: WeatherInfo?,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 320.dp,
    cardHeight: androidx.compose.ui.unit.Dp = 210.dp,
    temperatureFontSize: androidx.compose.ui.unit.TextUnit = 42.sp,
    conditionFontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    detailsFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    cornerRadius: androidx.compose.ui.unit.Dp = 30.dp
) {
    val scene = weather?.let {
        weatherSceneFor(it.weatherCode)
    } ?: WeatherScene.PARTLY_CLOUDY

    val isDay = weather?.isDay ?: true

    val cardColors = when {
        !isDay -> listOf(
            Color(0xFF202B55),
            Color(0xFF131A36)
        )
        scene == WeatherScene.CLEAR -> listOf(
            Color(0xFF4C91FF),
            Color(0xFF6B65F6)
        )
        scene == WeatherScene.RAIN ||
                scene == WeatherScene.STORM -> listOf(
            Color(0xFF46627F),
            Color(0xFF28364D)
        )
        scene == WeatherScene.SNOW -> listOf(
            Color(0xFF73B7D8),
            Color(0xFF486F91)
        )
        else -> listOf(
            Color(0xFF527DCC),
            Color(0xFF5159B2)
        )
    }

    Box(
        modifier = modifier
            .size(
                width = cardWidth,
                height = cardHeight
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(cardColors)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        WeatherIllustration(
            scene = scene,
            isDay = isDay,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 22.dp,
                    bottom = 18.dp
                )
        ) {
            Text(
                text = weather?.let { "${it.temperature}°" } ?: "--°",
                color = Color.White,
                fontSize = temperatureFontSize,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = weather?.let {
                    weatherLabel(scene)
                } ?: "Loading weather",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = conditionFontSize,
                fontWeight = FontWeight.SemiBold
            )

            if (weather != null) {
                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "H ${weather.high}°  •  L ${weather.low}°",
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = detailsFontSize
                )
            }
        }
    }
}


/**
 * Draws the animated weather artwork.
 */
@Composable
private fun WeatherIllustration(
    scene: WeatherScene,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(
        label = "WeatherAnimation"
    )

    val drift by transition.animateFloat(
        initialValue = -6f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CloudDrift"
    )

    val rainOffset by transition.animateFloat(
        initialValue = -12f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rain"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Soft atmospheric glow.
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = w * 0.40f,
            center = Offset(w * 0.86f, h * 0.10f)
        )

        if (isDay) {
            drawCircle(
                color = Color(0xFFFFDC72),
                radius = 29.dp.toPx(),
                center = Offset(w * 0.75f, h * 0.30f)
            )
            drawCircle(
                color = Color(0xFFFFF1B0).copy(alpha = 0.30f),
                radius = 41.dp.toPx(),
                center = Offset(w * 0.75f, h * 0.30f)
            )
        } else {
            drawCircle(
                color = Color(0xFFFFF2C7),
                radius = 25.dp.toPx(),
                center = Offset(w * 0.76f, h * 0.28f)
            )
            drawCircle(
                color = cardBackgroundApprox(scene),
                radius = 23.dp.toPx(),
                center = Offset(w * 0.80f, h * 0.24f)
            )

            listOf(
                Offset(w * 0.59f, h * 0.18f),
                Offset(w * 0.87f, h * 0.16f),
                Offset(w * 0.92f, h * 0.38f)
            ).forEach {
                drawCircle(
                    color = Color.White.copy(alpha = 0.72f),
                    radius = 1.6.dp.toPx(),
                    center = it
                )
            }
        }

        when (scene) {
            WeatherScene.CLEAR -> {
                // Sun/moon is enough; keep this condition clean.
            }

            WeatherScene.PARTLY_CLOUDY,
            WeatherScene.CLOUDY,
            WeatherScene.RAIN,
            WeatherScene.STORM,
            WeatherScene.SNOW -> {
                val cloudX = w * 0.66f + drift.dp.toPx()
                val cloudY = h * 0.43f

                val cloudColor = when (scene) {
                    WeatherScene.STORM -> Color(0xFFD2DAE8)
                    WeatherScene.RAIN -> Color(0xFFE4EBF5)
                    else -> Color.White
                }

                drawCloud(
                    center = Offset(cloudX, cloudY),
                    cloudColor = cloudColor
                )

                if (scene == WeatherScene.CLOUDY) {
                    drawCloud(
                        center = Offset(
                            w * 0.80f - drift.dp.toPx(),
                            h * 0.32f
                        ),
                        scale = 0.72f,
                        cloudColor = Color.White.copy(alpha = 0.74f)
                    )
                }

                if (scene == WeatherScene.RAIN ||
                    scene == WeatherScene.STORM
                ) {
                    repeat(4) { index ->
                        val x = cloudX - 40.dp.toPx() +
                                index * 25.dp.toPx()
                        val y = cloudY + 30.dp.toPx() +
                                rainOffset.dp.toPx()

                        drawLine(
                            color = Color(0xFFB9E8FF),
                            start = Offset(x, y),
                            end = Offset(
                                x - 6.dp.toPx(),
                                y + 14.dp.toPx()
                            ),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                if (scene == WeatherScene.STORM) {
                    val lightning = Path().apply {
                        moveTo(
                            cloudX + 3.dp.toPx(),
                            cloudY + 24.dp.toPx()
                        )
                        lineTo(
                            cloudX - 10.dp.toPx(),
                            cloudY + 51.dp.toPx()
                        )
                        lineTo(
                            cloudX + 2.dp.toPx(),
                            cloudY + 48.dp.toPx()
                        )
                        lineTo(
                            cloudX - 7.dp.toPx(),
                            cloudY + 72.dp.toPx()
                        )
                        lineTo(
                            cloudX + 21.dp.toPx(),
                            cloudY + 40.dp.toPx()
                        )
                        lineTo(
                            cloudX + 8.dp.toPx(),
                            cloudY + 43.dp.toPx()
                        )
                        close()
                    }

                    drawPath(
                        path = lightning,
                        color = Color(0xFFFFE66D)
                    )
                }

                if (scene == WeatherScene.SNOW) {
                    repeat(5) { index ->
                        val x = cloudX - 48.dp.toPx() +
                                index * 24.dp.toPx()
                        val y = cloudY + 44.dp.toPx() +
                                ((index % 2) * 14).dp.toPx()

                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f),
                            radius = 3.1.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            WeatherScene.FOG -> {
                repeat(4) { index ->
                    val top = h * 0.26f +
                            index * 18.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(
                            alpha = 0.50f - index * 0.07f
                        ),
                        topLeft = Offset(
                            w * 0.52f +
                                    if (index % 2 == 0) drift.dp.toPx()
                                    else -drift.dp.toPx(),
                            top
                        ),
                        size = Size(
                            width = w * (0.36f - index * 0.025f),
                            height = 7.dp.toPx()
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            20.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    center: Offset,
    scale: Float = 1f,
    cloudColor: Color
) {
    val baseWidth = 128.dp.toPx() * scale
    val baseHeight = 39.dp.toPx() * scale

    drawRoundRect(
        color = cloudColor,
        topLeft = Offset(
            center.x - baseWidth / 2,
            center.y - 2.dp.toPx() * scale
        ),
        size = Size(baseWidth, baseHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            28.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 31.dp.toPx() * scale,
        center = Offset(
            center.x - 27.dp.toPx() * scale,
            center.y - 7.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 39.dp.toPx() * scale,
        center = Offset(
            center.x + 12.dp.toPx() * scale,
            center.y - 19.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 26.dp.toPx() * scale,
        center = Offset(
            center.x + 46.dp.toPx() * scale,
            center.y - 5.dp.toPx() * scale
        )
    )
}

private fun cardBackgroundApprox(
    scene: WeatherScene
): Color = when (scene) {
    WeatherScene.RAIN,
    WeatherScene.STORM -> Color(0xFF394C67)
    WeatherScene.SNOW -> Color(0xFF5A8DA8)
    else -> Color(0xFF405E9F)
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
    onAllApps: () -> Unit
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
