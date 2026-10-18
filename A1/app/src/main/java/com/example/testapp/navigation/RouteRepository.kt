package com.example.testapp.navigation

import android.content.Context
import com.example.testapp.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** OSRM 輸出是真實路段上的路徑，不是從兩點畫直線。 */
data class PlannedRoute(
    val destination: GeoPoint,
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromCache: Boolean = false,
    val fromOfflineEngine: Boolean = false,
)

sealed interface RouteResult {
    data class Success(val route: PlannedRoute) : RouteResult
    data class Error(val message: String, val destination: GeoPoint? = null) : RouteResult
}

/**
 * Demo 接線：
 * - 地點搜尋：Nominatim；查詢節流、傳 User-Agent、快取。
 * - 優先由 AI Box 上的本地 OSRM 規劃；失敗時嘗試線上 OSRM 與既有快取。
 * 公開服務有配額與可用性限制；正式部署請替換為自架伺服器。
 */
class RouteRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("flowcockpit_navigation", Context.MODE_PRIVATE)
    private val aiBoxPrefs = context.applicationContext.getSharedPreferences("ai_box_connection", Context.MODE_PRIVATE)
    private val aiBoxBase: String get() = "http://" + (aiBoxPrefs.getString("host", "10.0.2.2:8000") ?: "10.0.2.2:8000").trim()
    private val client = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun plan(origin: GeoPoint, name: String, preferred: GeoPoint?): RouteResult =
        withContext(Dispatchers.IO) {
            val target = preferred?.takeIf { it.isValid() } ?: offlineGeocode(name) ?: geocode(name)
                ?: return@withContext RouteResult.Error("無法確認地點，請輸入完整地名或由 AI 提供座標")
            val key = "route_${(origin.latitude * 1000).roundToInt()}_${(origin.longitude * 1000).roundToInt()}_${(target.latitude * 10000).roundToInt()}_${(target.longitude * 10000).roundToInt()}"
            val offline = runCatching { fetchOfflineRoute(origin, target) }.getOrNull()
            if (offline != null) {
                prefs.edit().putString(key, serialize(offline)).apply()
                return@withContext RouteResult.Success(offline)
            }
            val live = runCatching { fetchRoute(origin, target) }.getOrNull()
            if (live != null) {
                prefs.edit().putString(key, serialize(live)).apply()
                return@withContext RouteResult.Success(live)
            }
            val cached = prefs.getString(key, null)?.let { runCatching { parseSaved(it, target) }.getOrNull() }
            if (cached != null) RouteResult.Success(cached.copy(fromCache = true))
            else RouteResult.Error("無法取得路線：請確認筆電本地 OSRM 已啟動且平板與筆電在同一區網", target)
        }

    private fun offlineGeocode(name: String): GeoPoint? {
        if (name.isBlank()) return null
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val obj = JSONObject(get("$aiBoxBase/api/navigation/geocode?q=$query"))
        val item = obj.optJSONArray("results")?.optJSONObject(0) ?: return null
        return GeoPoint(item.getDouble("lat"), item.getDouble("lon")).takeIf { it.isValid() }
    }

    private fun fetchOfflineRoute(origin: GeoPoint, target: GeoPoint): PlannedRoute? {
        val url = "$aiBoxBase/api/navigation/route?start_lat=${origin.latitude}&start_lon=${origin.longitude}" +
            "&end_lat=${target.latitude}&end_lon=${target.longitude}"
        return parseOsrmRoute(get(url), target)?.copy(fromOfflineEngine = true)
    }

    private fun geocode(name: String): GeoPoint? {
        if (name.isBlank()) return null
        val key = "geo_${name.trim().lowercase()}"
        prefs.getString(key, null)?.let { stored ->
            runCatching {
                val p = JSONObject(stored)
                GeoPoint(p.getDouble("lat"), p.getDouble("lon"))
            }.getOrNull()?.takeIf { it.isValid() }?.let { return it }
        }
        // Public Nominatim policy: <= 1 req/s per app instance, cache results, distinct User-Agent.
        // This lock serializes calls even if requests originate from several UI interactions.
        synchronized(geocodeLock) {
            val elapsed = System.currentTimeMillis() - lastGeocodeMs
            if (elapsed < 1100) Thread.sleep(1100 - elapsed)
            lastGeocodeMs = System.currentTimeMillis()
            val q = URLEncoder.encode(name.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=3&countrycodes=tw&q=$q"
            val array = runCatching { JSONArray(get(url)) }.getOrNull() ?: return null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val point = GeoPoint(lat, lon)
                if (!point.isValid()) continue
                prefs.edit().putString(key, JSONObject().put("lat", lat).put("lon", lon).toString()).apply()
                return point
            }
        }
        return null
    }

    private fun fetchRoute(origin: GeoPoint, target: GeoPoint): PlannedRoute? {
        val base = "https://router.project-osrm.org/route/v1/driving/"
        val url = "$base${origin.longitude},${origin.latitude};${target.longitude},${target.latitude}" +
            "?overview=full&geometries=geojson&steps=false"
        return parseOsrmRoute(get(url), target)
    }

    private fun parseOsrmRoute(body: String, target: GeoPoint): PlannedRoute? {
        val obj = JSONObject(body)
        if (obj.optString("code") != "Ok") return null
        val route = obj.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val points = parseCoordinates(coords)
        if (points.size < 2) return null
        val distance = route.optDouble("distance", Double.NaN)
        val duration = route.optDouble("duration", Double.NaN)
        if (!distance.isFinite() || !duration.isFinite() || distance < 0 || duration < 0) return null
        return PlannedRoute(target, points, distance, duration)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "FlowCockpitMapDemo/1.0 (Android; map navigation prototype)")
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty response")
        }
    }

    private fun parseCoordinates(arr: JSONArray): List<GeoPoint> = buildList {
        for (i in 0 until minOf(arr.length(), 20_000)) {
            val row = arr.optJSONArray(i) ?: continue
            val p = GeoPoint(row.optDouble(1), row.optDouble(0))
            if (p.isValid()) add(p)
        }
    }

    private fun serialize(r: PlannedRoute): String = JSONObject()
        .put("distance", r.distanceMeters)
        .put("duration", r.durationSeconds)
        .put("coords", JSONArray().apply {
            r.geometry.forEach { p -> put(JSONArray().put(p.longitude).put(p.latitude)) }
        }).toString()

    private fun parseSaved(saved: String, destination: GeoPoint): PlannedRoute? {
        val obj = JSONObject(saved)
        val geometry = parseCoordinates(obj.getJSONArray("coords"))
        if (geometry.size < 2) return null
        return PlannedRoute(destination, geometry, obj.getDouble("distance"), obj.getDouble("duration"))
    }

    private companion object {
        val geocodeLock = Any()
        @Volatile var lastGeocodeMs = 0L
    }
}
