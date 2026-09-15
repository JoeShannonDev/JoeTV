package com.joeshannon.joetv.weather

import android.content.Context

// -----------------------------------------------------------------------------
// Radar widget preferences
//
// Reuses the same "joetv_preferences" SharedPreferences file the theme
// picker already writes to (see SettingsScreen.kt) rather than introducing
// DataStore or a second preferences file.
// -----------------------------------------------------------------------------

private const val PREFS_NAME = "joetv_preferences"

private const val KEY_ZOOM = "joetv_radar_zoom"
private const val KEY_LATITUDE = "joetv_radar_lat"
private const val KEY_LONGITUDE = "joetv_radar_lon"
private const val KEY_LABEL = "joetv_radar_label"
private const val KEY_LOCATION_MODE = "joetv_radar_location_mode"
private const val KEY_CLEAN_VISUALS = "joetv_radar_clean_visuals"

private const val KEY_AUTO_LATITUDE = "joetv_radar_auto_lat"
private const val KEY_AUTO_LONGITUDE = "joetv_radar_auto_lon"
private const val KEY_AUTO_LABEL = "joetv_radar_auto_label"
private const val KEY_AUTO_RESOLVED_AT = "joetv_radar_auto_resolved_at"

/** Manhattan, Kansas -- the same default the old temperature card used. */
const val RADAR_DEFAULT_LATITUDE = 39.1836
const val RADAR_DEFAULT_LONGITUDE = -96.5717
const val RADAR_DEFAULT_LABEL = "Manhattan, KS"

const val RADAR_MIN_ZOOM = 5
const val RADAR_MAX_ZOOM = 10
const val RADAR_DEFAULT_ZOOM = 7

/**
 * How the radar decides where to point itself.
 *
 * - [PINNED]: a location set by hand in Settings (or the KS default).
 * - [GPS]: the device's own GPS/network location provider. Only useful on
 *   the tablet build -- the Pi has no GPS hardware, so this silently falls
 *   back to the pinned spot there.
 * - [AUTO_IP]: guesses the location from the device's public IP address, so
 *   the radar re-centers itself automatically if the Pi (or its network)
 *   ever moves, without needing GPS hardware at all. Re-checked every few
 *   hours rather than on every refresh, since it's not going to change
 *   minute to minute.
 */
enum class RadarLocationMode {
    PINNED,
    GPS,
    AUTO_IP
}

data class RadarLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun getRadarZoom(context: Context): Int =
    prefs(context)
        .getInt(KEY_ZOOM, RADAR_DEFAULT_ZOOM)
        .coerceIn(RADAR_MIN_ZOOM, RADAR_MAX_ZOOM)

fun setRadarZoom(context: Context, zoom: Int) {
    prefs(context).edit()
        .putInt(KEY_ZOOM, zoom.coerceIn(RADAR_MIN_ZOOM, RADAR_MAX_ZOOM))
        .apply()
}

fun getPinnedRadarLocation(context: Context): RadarLocation {
    val stored = prefs(context)
    return RadarLocation(
        latitude = stored
            .getFloat(KEY_LATITUDE, RADAR_DEFAULT_LATITUDE.toFloat())
            .toDouble(),
        longitude = stored
            .getFloat(KEY_LONGITUDE, RADAR_DEFAULT_LONGITUDE.toFloat())
            .toDouble(),
        label = stored.getString(KEY_LABEL, RADAR_DEFAULT_LABEL)
            ?: RADAR_DEFAULT_LABEL
    )
}

fun setPinnedRadarLocation(context: Context, location: RadarLocation) {
    prefs(context).edit()
        .putFloat(KEY_LATITUDE, location.latitude.toFloat())
        .putFloat(KEY_LONGITUDE, location.longitude.toFloat())
        .putString(KEY_LABEL, location.label)
        .apply()
}

fun getRadarLocationMode(context: Context): RadarLocationMode {
    val stored = prefs(context).getString(KEY_LOCATION_MODE, null)
        ?: return RadarLocationMode.PINNED

    return runCatching { RadarLocationMode.valueOf(stored) }
        .getOrDefault(RadarLocationMode.PINNED)
}

fun setRadarLocationMode(context: Context, mode: RadarLocationMode) {
    prefs(context).edit().putString(KEY_LOCATION_MODE, mode.name).apply()
}

/** Last IP-based location lookup, if one has ever succeeded. */
fun getCachedAutoLocation(context: Context): RadarLocation? {
    val stored = prefs(context)
    if (!stored.contains(KEY_AUTO_LATITUDE)) return null

    return RadarLocation(
        latitude = stored.getFloat(KEY_AUTO_LATITUDE, 0f).toDouble(),
        longitude = stored.getFloat(KEY_AUTO_LONGITUDE, 0f).toDouble(),
        label = stored.getString(KEY_AUTO_LABEL, "Current network location")
            ?: "Current network location"
    )
}

fun setCachedAutoLocation(context: Context, location: RadarLocation) {
    prefs(context).edit()
        .putFloat(KEY_AUTO_LATITUDE, location.latitude.toFloat())
        .putFloat(KEY_AUTO_LONGITUDE, location.longitude.toFloat())
        .putString(KEY_AUTO_LABEL, location.label)
        .putLong(KEY_AUTO_RESOLVED_AT, System.currentTimeMillis())
        .apply()
}

fun getAutoLocationResolvedAt(context: Context): Long =
    prefs(context).getLong(KEY_AUTO_RESOLVED_AT, 0L)

fun getCleanVisuals(context: Context): Boolean =
    prefs(context).getBoolean(KEY_CLEAN_VISUALS, true)

fun setCleanVisuals(context: Context, clean: Boolean) {
    prefs(context).edit().putBoolean(KEY_CLEAN_VISUALS, clean).apply()
}
