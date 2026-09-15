package com.joeshannon.joetv.weather

import android.graphics.Bitmap

// -----------------------------------------------------------------------------
// Radar widget data models
// -----------------------------------------------------------------------------

/**
 * One active severe weather alert covering the radar's pinned or GPS
 * location, as reported by the National Weather Service.
 */
data class RadarAlert(
    val event: String,
    val severity: String,
    val headline: String
)

/**
 * One hour of the short-range forecast strip shown under the current
 * conditions.
 */
data class HourlyForecast(
    val label: String,
    val temperature: Int,
    val weatherCode: Int,
    val isDay: Boolean
)

/**
 * Everything the hero banner's radar card needs to draw itself: the
 * composited radar image, when it was generated, any active alerts for the
 * pinned/GPS/auto location, the current conditions, the next few hours, and
 * the location itself (so a tap can open the exact spot in another app).
 *
 * [image] is null only before the first successful load ever completes --
 * after that, a failed refresh keeps the previous image instead of blanking
 * the card (see loadRadarState in RadarRepository.kt). The same is true of
 * the weather fields: a failed forecast fetch keeps the last known numbers
 * rather than blanking them.
 */
data class RadarState(
    val image: Bitmap?,
    val generatedAtMillis: Long,
    val alerts: List<RadarAlert>,
    val latitude: Double,
    val longitude: Double,
    val zoom: Int,
    val locationLabel: String,
    val currentTemperature: Int?,
    val highTemperature: Int?,
    val lowTemperature: Int?,
    val conditionLabel: String?,
    val hourly: List<HourlyForecast>
)

/**
 * The single highest-priority alert to badge on the card, if any. NWS often
 * reports several overlapping alerts (e.g. a Severe Thunderstorm Warning
 * inside a Flood Watch) -- this picks the one worth interrupting the screen
 * for.
 */
fun List<RadarAlert>.mostSevere(): RadarAlert? {
    val severityRank = mapOf(
        "Extreme" to 0,
        "Severe" to 1,
        "Moderate" to 2,
        "Minor" to 3,
        "Unknown" to 4
    )
    return minByOrNull { severityRank[it.severity] ?: 5 }
}

/**
 * Coarse weather condition bucket, shared by the current-conditions read
 * and every hourly forecast entry. Mirrors Open-Meteo's WMO weather codes.
 */
enum class WeatherScene {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    STORM,
    SNOW
}

fun weatherSceneFor(code: Int): WeatherScene = when (code) {
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

fun weatherSceneLabel(scene: WeatherScene): String = when (scene) {
    WeatherScene.CLEAR -> "Clear"
    WeatherScene.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherScene.CLOUDY -> "Cloudy"
    WeatherScene.FOG -> "Foggy"
    WeatherScene.RAIN -> "Rain"
    WeatherScene.STORM -> "Thunderstorms"
    WeatherScene.SNOW -> "Snow"
}
