package com.joeshannon.joetv.weather

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

// -----------------------------------------------------------------------------
// JoeTV radar + weather widget
//
// Replaces the old text-only "72* / H 78 L 61" weather card with a live
// radar image plus current conditions and a short hourly forecast, all
// built from open, key-free sources:
//
//   - Basemap tiles:  CARTO dark tiles (no API key; attribution required)
//   - Radar overlay:  RainViewer (rainviewer.com/api.html), refreshed about
//                      every 10 minutes, no API key
//   - Conditions:     Open-Meteo (current + hourly + daily in one call)
//   - Severe alerts:  National Weather Service (api.weather.gov), no key
//   - IP geolocation: ipapi.co, for the optional "auto" location mode
//
// There's no pan/zoom map here -- just a grid of 256px tiles stitched into
// one glanceable, self-refreshing picture, always centered exactly on the
// pinned/GPS/auto point (not just "somewhere in its tile" -- see the tile
// math below). Tapping it hands off to whatever the device treats as the
// default handler for a location (see openRadarExternally).
// -----------------------------------------------------------------------------

private const val TILE_SIZE = 256

/**
 * Composite canvas is 3 tiles square. Tiles are fetched from a wider,
 * 5-tile-radius net around the target point so that *wherever* the exact
 * point falls within its own tile, the whole 3-tile canvas still ends up
 * fully covered -- see buildRadarComposite for the math.
 */
private const val CANVAS_TILES = 3
private const val FETCH_RADIUS = 2

private const val RAINVIEWER_INDEX =
    "https://api.rainviewer.com/public/weather-maps.json"

/**
 * RainViewer color scheme 2 = "Universal Blue", a legible blue-to-red scale
 * that reads well over a dark basemap. Options "1_1" = smoothed tiles with
 * snow shown in its own color.
 */
private const val RAINVIEWER_COLOR_SCHEME = 2
private const val RAINVIEWER_OPTIONS = "1_1"

/**
 * NWS asks every client to identify itself with a real contact method, but
 * this repo is public -- a generic contact keeps that promise without
 * baking a personal address into source control.
 */
private const val NWS_USER_AGENT =
    "JoeTV-AndroidTV-Launcher (github.com/JoeShannonDev/JoeTV)"

/**
 * Below this alpha, a RainViewer pixel is trace/very light precipitation.
 * "Clean visuals" mode drops these so a light drizzle 200 miles away
 * doesn't compete for attention with a real cell.
 */
private const val FAINT_PRECIP_ALPHA_THRESHOLD = 55

/** How often the "auto" IP-based location is allowed to re-check itself. */
private const val AUTO_IP_REFRESH_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000L

/**
 * Fetches everything the radar hero card needs: resolves the current
 * location (pinned, GPS, or IP-based auto), downloads and composites the
 * map + radar tiles, pulls current conditions + the next few hours, and
 * checks for active NWS alerts.
 *
 * Never throws -- on any failure this falls back to [previous]'s image,
 * alerts, and weather numbers rather than blanking the card, or an
 * empty-but-valid state if there is no previous state yet.
 */
suspend fun loadRadarState(
    context: Context,
    previous: RadarState?
): RadarState = withContext(Dispatchers.IO) {
    val zoom = getRadarZoom(context)
    val location = resolveRadarLocation(context)

    val image = runCatching {
        buildRadarComposite(
            latitude = location.latitude,
            longitude = location.longitude,
            zoom = zoom,
            cleanVisuals = getCleanVisuals(context)
        )
    }.onFailure {
        println("JOETV_RADAR_IMAGE_FAILED=${it.message}")
    }.getOrNull()

    val alerts = runCatching {
        loadActiveAlerts(location.latitude, location.longitude)
    }.onFailure {
        println("JOETV_RADAR_ALERTS_FAILED=${it.message}")
    }.getOrNull() ?: previous?.alerts ?: emptyList()

    val weather = runCatching {
        loadWeatherData(location.latitude, location.longitude)
    }.onFailure {
        println("JOETV_RADAR_WEATHER_FAILED=${it.message}")
    }.getOrNull()

    RadarState(
        image = image ?: previous?.image,
        generatedAtMillis = if (image != null) {
            System.currentTimeMillis()
        } else {
            previous?.generatedAtMillis ?: 0L
        },
        alerts = alerts,
        latitude = location.latitude,
        longitude = location.longitude,
        zoom = zoom,
        locationLabel = location.label,
        currentTemperature = weather?.currentTemperature ?: previous?.currentTemperature,
        highTemperature = weather?.highTemperature ?: previous?.highTemperature,
        lowTemperature = weather?.lowTemperature ?: previous?.lowTemperature,
        conditionLabel = weather?.conditionLabel ?: previous?.conditionLabel,
        hourly = weather?.hourly ?: previous?.hourly ?: emptyList()
    )
}

/**
 * Pinned by default; GPS or IP-based auto if the user opted into one in
 * Settings, each falling back to the pinned spot (silently) when it can't
 * resolve -- a TV box has no GPS hardware, and a network hiccup shouldn't
 * blank the card.
 */
private fun resolveRadarLocation(context: Context): RadarLocation =
    when (getRadarLocationMode(context)) {
        RadarLocationMode.PINNED -> getPinnedRadarLocation(context)
        RadarLocationMode.GPS ->
            resolveGpsLocation(context) ?: getPinnedRadarLocation(context)
        RadarLocationMode.AUTO_IP ->
            resolveAutoIpLocation(context) ?: getPinnedRadarLocation(context)
    }

private fun resolveGpsLocation(context: Context): RadarLocation? {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) return null

    return runCatching {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
            .filter { locationManager.isProviderEnabled(it) }
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
            ?.let {
                RadarLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    label = "Current location"
                )
            }
    }.getOrNull()
}

/**
 * Guesses the radar's location from the device's public IP address, so it
 * re-centers itself automatically if the Pi (or its network) ever moves,
 * without needing GPS hardware. Re-checked at most every
 * [AUTO_IP_REFRESH_INTERVAL_MILLIS] -- an IP-based location isn't going to
 * change minute to minute, and there's no reason to lean on a free API more
 * than that.
 */
private fun resolveAutoIpLocation(context: Context): RadarLocation? {
    val cached = getCachedAutoLocation(context)
    val isStale = System.currentTimeMillis() - getAutoLocationResolvedAt(context) >
            AUTO_IP_REFRESH_INTERVAL_MILLIS

    if (cached != null && !isStale) return cached

    val fresh = runCatching { fetchIpLocation() }.getOrNull()
    if (fresh != null) {
        setCachedAutoLocation(context, fresh)
        return fresh
    }

    // Lookup failed (offline, rate-limited, etc.) -- keep using the last
    // known auto location rather than falling all the way back to the
    // pinned default, if we have one.
    return cached
}

private fun fetchIpLocation(): RadarLocation? {
    val body = fetchText("https://ipapi.co/json/") ?: return null
    val root = JSONObject(body)
    if (root.has("error")) return null

    val latitude = root.optDouble("latitude", Double.NaN)
    val longitude = root.optDouble("longitude", Double.NaN)
    if (latitude.isNaN() || longitude.isNaN()) return null

    val city = root.optString("city").takeIf { it.isNotBlank() }
    val region = root.optString("region").takeIf { it.isNotBlank() }
    val label = listOfNotNull(city, region)
        .joinToString(", ")
        .ifBlank { "Current network location" }

    return RadarLocation(latitude = latitude, longitude = longitude, label = label)
}

// --- Tile math ---------------------------------------------------------

private fun longitudeToTileX(longitude: Double, zoom: Int): Double =
    (longitude + 180.0) / 360.0 * 2.0.pow(zoom)

private fun latitudeToTileY(latitude: Double, zoom: Int): Double {
    val latRad = Math.toRadians(latitude)
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * 2.0.pow(zoom)
}

// --- Compositing ---------------------------------------------------------

/**
 * Composites a [CANVAS_TILES]-tile-square radar image that is always
 * centered exactly on ([latitude], [longitude]) -- not just "whichever tile
 * contains that point centered", which is a different (and visibly wrong)
 * thing. A point is essentially never at the exact center of its own tile,
 * so tiles are placed using each one's *exact* fractional offset from the
 * target point rather than snapped to a tile grid. [FETCH_RADIUS] is wide
 * enough that the canvas stays fully covered no matter where within its
 * tile the point happens to fall.
 */
private suspend fun buildRadarComposite(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    cleanVisuals: Boolean
): Bitmap? = coroutineScope {
    val frame = latestRainviewerFrame() ?: return@coroutineScope null

    val exactX = longitudeToTileX(longitude, zoom)
    val exactY = latitudeToTileY(latitude, zoom)
    val baseTileX = floor(exactX).toInt()
    val baseTileY = floor(exactY).toInt()

    val canvasSize = TILE_SIZE * CANVAS_TILES
    val centerPx = canvasSize / 2.0

    val composite = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(composite)
    canvas.drawColor(Color.parseColor("#0F1A2E"))

    val placements = (-FETCH_RADIUS..FETCH_RADIUS).flatMap { dy ->
        (-FETCH_RADIUS..FETCH_RADIUS).mapNotNull { dx ->
            val tileX = baseTileX + dx
            val tileY = baseTileY + dy

            // Exact pixel position of this tile's top-left corner, measured
            // from the target point rather than from the base tile -- this
            // is what keeps the target point pinned to the canvas center
            // regardless of where it falls within its own tile.
            val left = centerPx + (tileX - exactX) * TILE_SIZE
            val top = centerPx + (tileY - exactY) * TILE_SIZE

            val offCanvas = left + TILE_SIZE <= 0 || left >= canvasSize ||
                    top + TILE_SIZE <= 0 || top >= canvasSize

            if (offCanvas) null else Triple(tileX, tileY, left to top)
        }
    }

    val tileJobs = placements.map { (tileX, tileY, position) ->
        async {
            val basemap = fetchBitmap(basemapTileUrl(tileX, tileY, zoom))
            val radar = fetchBitmap(radarTileUrl(frame, tileX, tileY, zoom))
                ?.let { if (cleanVisuals) dropFaintPrecipitation(it) else it }

            Triple(position.first, position.second, basemap to radar)
        }
    }

    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    tileJobs.awaitAll().forEach { (left, top, tiles) ->
        val (basemap, radar) = tiles
        basemap?.let { canvas.drawBitmap(it, left.toFloat(), top.toFloat(), paint) }
        radar?.let { canvas.drawBitmap(it, left.toFloat(), top.toFloat(), paint) }
    }

    // The target point is always exactly at the canvas center now.
    drawLocationMarker(canvas, centerPx.toFloat(), centerPx.toFloat())

    composite
}

private fun drawLocationMarker(canvas: Canvas, x: Float, y: Float) {
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(x, y, 9f, ringPaint)
    canvas.drawCircle(x, y, 5f, dotPaint)
}

private fun basemapTileUrl(x: Int, y: Int, zoom: Int) =
    "https://basemaps.cartocdn.com/dark_nolabels/$zoom/$x/$y.png"

private fun radarTileUrl(frame: RainviewerFrame, x: Int, y: Int, zoom: Int) =
    "${frame.host}${frame.path}/$TILE_SIZE/$zoom/$x/$y/" +
            "$RAINVIEWER_COLOR_SCHEME/$RAINVIEWER_OPTIONS.png"

private data class RainviewerFrame(val host: String, val path: String)

private fun latestRainviewerFrame(): RainviewerFrame? = runCatching {
    val body = fetchText(RAINVIEWER_INDEX) ?: return null
    val root = JSONObject(body)
    val host = root.getString("host")
    val pastFrames = root.getJSONObject("radar").getJSONArray("past")
    val latest = pastFrames.getJSONObject(pastFrames.length() - 1)
    RainviewerFrame(host = host, path = latest.getString("path"))
}.getOrNull()

/**
 * Zeroes out low-alpha (trace-level) precipitation pixels so faint,
 * far-away drizzle doesn't visually compete with a real storm cell. Cheap
 * per-pixel pass over a single 256x256 tile.
 */
private fun dropFaintPrecipitation(source: Bitmap): Bitmap {
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

    for (i in pixels.indices) {
        val alpha = Color.alpha(pixels[i])
        if (alpha in 1 until FAINT_PRECIP_ALPHA_THRESHOLD) {
            pixels[i] = Color.TRANSPARENT
        }
    }

    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return result
}

// --- Current conditions + hourly forecast (Open-Meteo) ---------------------

private data class WeatherData(
    val currentTemperature: Int,
    val highTemperature: Int,
    val lowTemperature: Int,
    val conditionLabel: String,
    val hourly: List<HourlyForecast>
)

/**
 * One call covering current conditions, today's high/low, and enough
 * hourly points to pull the next 6 hours from -- forecast_days=2 so there
 * are always 6 hours of data left even if it's called at 11pm.
 */
private fun loadWeatherData(latitude: Double, longitude: Double): WeatherData? = runCatching {
    val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=fahrenheit&timezone=auto&forecast_days=2"

    val body = fetchText(url) ?: return null
    val root = JSONObject(body)
    val current = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val hourly = root.getJSONObject("hourly")

    val hourlyTimes = hourly.getJSONArray("time")
    val hourlyTemps = hourly.getJSONArray("temperature_2m")
    val hourlyCodes = hourly.getJSONArray("weather_code")
    val hourlyIsDay = hourly.getJSONArray("is_day")

    val nowMillis = System.currentTimeMillis()
    val hourFormatter = DateTimeFormatter.ofPattern("h a")
    val forecast = mutableListOf<HourlyForecast>()

    for (i in 0 until hourlyTimes.length()) {
        val parsedTime = runCatching {
            LocalDateTime.parse(hourlyTimes.getString(i))
        }.getOrNull() ?: continue

        val epochMillis = parsedTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Skip anything more than a few minutes in the past so "now" always
        // shows the current or very next hour first.
        if (epochMillis < nowMillis - 30 * 60 * 1_000L) continue

        forecast.add(
            HourlyForecast(
                label = parsedTime.format(hourFormatter),
                temperature = hourlyTemps.getDouble(i).toInt(),
                weatherCode = hourlyCodes.getInt(i),
                isDay = hourlyIsDay.getInt(i) == 1
            )
        )

        if (forecast.size >= 6) break
    }

    WeatherData(
        currentTemperature = current.getDouble("temperature_2m").toInt(),
        highTemperature = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt(),
        lowTemperature = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt(),
        conditionLabel = weatherSceneLabel(weatherSceneFor(current.getInt("weather_code"))),
        hourly = forecast
    )
}.getOrNull()

// --- Severe alerts (NWS) --------------------------------------------------

private fun loadActiveAlerts(latitude: Double, longitude: Double): List<RadarAlert> {
    val url = "https://api.weather.gov/alerts/active?point=$latitude,$longitude"
    val body = fetchText(url, userAgent = NWS_USER_AGENT) ?: return emptyList()

    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    return (0 until features.length()).mapNotNull { index ->
        val properties = features.getJSONObject(index).optJSONObject("properties")
            ?: return@mapNotNull null

        RadarAlert(
            event = properties.optString("event", "Weather Alert"),
            severity = properties.optString("severity", "Unknown"),
            headline = properties.optString(
                "headline",
                properties.optString("event", "")
            )
        )
    }
}

// --- Networking ------------------------------------------------------------

private fun fetchText(url: String, userAgent: String? = null): String? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("Accept", "application/json")
        if (userAgent != null) {
            setRequestProperty("User-Agent", userAgent)
        }
    }

    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun fetchBitmap(url: String): Bitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
    }

    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

// --- Geocoding (for pinning a custom location in Settings) ---------------

data class GeocodeResult(
    val name: String,
    val admin1: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val label: String
        get() = listOfNotNull(name, admin1, country).joinToString(", ")
}

/** Same Open-Meteo family JoeTV already used for weather -- free, no key. */
suspend fun searchLocations(query: String): List<GeocodeResult> =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${Uri.encode(query)}&count=5&language=en&format=json"

        val body = fetchText(url) ?: return@withContext emptyList()
        val results = JSONObject(body).optJSONArray("results")
            ?: return@withContext emptyList()

        (0 until results.length()).map { index ->
            val item = results.getJSONObject(index)
            GeocodeResult(
                name = item.getString("name"),
                admin1 = item.optString("admin1").takeIf { it.isNotBlank() },
                country = item.optString("country").takeIf { it.isNotBlank() },
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude")
            )
        }
    }

// --- Opening the location elsewhere ---------------------------------------

/**
 * Best-effort "open this in your primary maps/weather app". Android has no
 * universal deep link straight into a specific weather app's radar screen,
 * so this hands off to whatever the device treats as the default handler
 * for a location -- the closest equivalent on this platform -- and falls
 * back to RainViewer's own live map in a browser if nothing claims it.
 */
fun openRadarExternally(context: Context, state: RadarState) {
    val geoUri = Uri.parse(
        "geo:${state.latitude},${state.longitude}?z=${state.zoom}" +
                "&q=${state.latitude},${state.longitude}(${Uri.encode(state.locationLabel)})"
    )
    if (tryStart(context, Intent(Intent.ACTION_VIEW, geoUri))) return

    val webUri = Uri.parse(
        "https://www.rainviewer.com/map.html?loc=${state.latitude}," +
                "${state.longitude},${state.zoom}&layer=radar&sm=1"
    )
    if (tryStart(context, Intent(Intent.ACTION_VIEW, webUri))) return

    Toast.makeText(context, "No app available to open the radar", Toast.LENGTH_SHORT).show()
}

private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
    context.startActivity(intent)
    true
}.getOrDefault(false)
