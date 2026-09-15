package com.joeshannon.joetv.screens

// -----------------------------------------------------------------------------
// JoeTV Backgrounds
//
// A second picker alongside Settings/Themes: which animated background the
// launcher draws behind everything else (see JoeTvMovingBackground in
// HomeScreen.kt for the actual drawing, and
// ui/theme/BackgroundStyle.kt for the reactive currentJoeTvBackgroundStyle
// state this screen reads and writes). Deliberately its own screen rather
// than a tab bolted onto Settings, per how it was asked for -- a separate
// button right next to Themes.
// -----------------------------------------------------------------------------

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.joeshannon.joetv.ui.theme.JoeBackgroundBase
import com.joeshannon.joetv.ui.theme.JoeTvBackgroundStyle
import com.joeshannon.joetv.ui.theme.applyJoeTvBackgroundStyle
import com.joeshannon.joetv.ui.theme.currentJoeTvBackgroundStyle

private const val BACKGROUND_PREFERENCE_KEY = "joetv_background_style"

/**
 * Restores whichever background style was picked last time. Safe to call
 * more than once; an unrecognized or missing value falls back to the
 * default (Nebula) JoeTvBackgroundStyle already starts as.
 */
internal fun restoreSavedBackgroundStyle(context: Context) {
    val preferences = context.getSharedPreferences(
        "joetv_preferences",
        Context.MODE_PRIVATE
    )

    val savedName = preferences.getString(BACKGROUND_PREFERENCE_KEY, null) ?: return

    val savedStyle = runCatching {
        JoeTvBackgroundStyle.valueOf(savedName)
    }.getOrNull() ?: return

    applyJoeTvBackgroundStyle(savedStyle)
}

private fun persistBackgroundStyle(context: Context, style: JoeTvBackgroundStyle) {
    context.getSharedPreferences(
        "joetv_preferences",
        Context.MODE_PRIVATE
    )
        .edit()
        .putString(BACKGROUND_PREFERENCE_KEY, style.name)
        .apply()
}


/**
 * Full-screen background-style picker. Same shell as SettingsScreen, just a
 * different grid.
 */
@Composable
internal fun BackgroundsScreen(
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
                                text = "Backgrounds",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Pick a vibe for JoeTV",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            items(JoeTvBackgroundStyle.entries.toList()) { style ->
                BackgroundStyleCard(
                    style = style,
                    isSelected = style == currentJoeTvBackgroundStyle,
                    onSelect = {
                        applyJoeTvBackgroundStyle(style)
                        persistBackgroundStyle(context, style)
                    }
                )
            }
        }
    }
}


/**
 * One selectable background swatch, with a small static preview of that
 * style's look rather than the full animation -- enough to tell them apart
 * at a glance without every card in the grid competing for motion.
 */
@Composable
private fun BackgroundStyleCard(
    style: JoeTvBackgroundStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B0F16))
            .then(
                if (focused || isSelected) {
                    Modifier.border(
                        border = BorderStroke(
                            width = if (focused) 3.dp else 2.dp,
                            brush = joeFocusBrush()
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
    ) {
        BackgroundStylePreview(
            style = style,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
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

        Text(
            text = style.displayName,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        )
    }
}


/**
 * A tiny, static stand-in for each style's animated look -- the same shapes
 * JoeTvMovingBackground draws, just held at one frame instead of animated,
 * so the grid isn't three simultaneously-animating cards.
 */
@Composable
private fun BackgroundStylePreview(
    style: JoeTvBackgroundStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    JoeBackgroundBase,
                    Color(0xFF05070B)
                )
            )
        )
    ) {
        when (style) {
            JoeTvBackgroundStyle.NEBULA -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-30).dp, y = (-20).dp)
                        .width(140.dp)
                        .height(140.dp)
                        .clip(CircleShape)
                        .background(Color(0x5522D3EE))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 24.dp, y = 24.dp)
                        .width(130.dp)
                        .height(130.dp)
                        .clip(CircleShape)
                        .background(Color(0x55A78BFA))
                )
            }

            JoeTvBackgroundStyle.AURORA -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bandColors = listOf(
                        Color(0x4022D3EE),
                        Color(0x4084CC16),
                        Color(0x40A78BFA)
                    )

                    bandColors.forEachIndexed { index, color ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(
                                -40f,
                                size.height * (0.15f + index * 0.28f)
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width + 80f,
                                size.height * 0.22f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                size.height * 0.11f
                            )
                        )
                    }
                }
            }

            JoeTvBackgroundStyle.STARFIELD -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stars = listOf(
                        0.10f to 0.20f, 0.28f to 0.55f, 0.42f to 0.15f,
                        0.55f to 0.70f, 0.68f to 0.32f, 0.80f to 0.60f,
                        0.18f to 0.80f, 0.90f to 0.20f, 0.62f to 0.10f,
                        0.35f to 0.90f, 0.75f to 0.85f, 0.48f to 0.45f
                    )

                    stars.forEach { (fx, fy) ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = 2.dp.toPx(),
                            center = Offset(size.width * fx, size.height * fy)
                        )
                    }
                }
            }

            JoeTvBackgroundStyle.VIDEO -> {
                // Static stand-in: a filmstrip-ish dark card with a play
                // triangle, same hand-drawn approach as the other glyphs in
                // this app rather than pulling in an icon library.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val circleRadius = size.height * 0.20f
                    val center = Offset(size.width * 0.5f, size.height * 0.46f)

                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f),
                        radius = circleRadius,
                        center = center
                    )

                    val triangleWidth = circleRadius * 0.75f
                    val triangleHeight = circleRadius * 0.9f
                    val path = Path().apply {
                        moveTo(center.x - triangleWidth * 0.35f, center.y - triangleHeight / 2f)
                        lineTo(center.x - triangleWidth * 0.35f, center.y + triangleHeight / 2f)
                        lineTo(center.x + triangleWidth * 0.55f, center.y)
                        close()
                    }
                    drawPath(path = path, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}
