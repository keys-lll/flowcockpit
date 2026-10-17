package com.example.testapp.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.testapp.model.GeoPoint
import com.example.testapp.navigation.PlannedRoute
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 真實 OSM 圖磚；資料依 OpenFreeMap 的使用政策提供，需網路或既有 SDK 快取。 */
private const val TILE_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapLibreView(
    origin: GeoPoint,
    hasRealLocation: Boolean,
    destination: GeoPoint?,
    route: PlannedRoute?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val renderer = remember { MapRenderer() }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(origin.latitude, origin.longitude))
                    .zoom(13.5)
                    .build()
                map.setStyle(TILE_STYLE) { style -> renderer.attach(this, map, style) }
            }
        }
    }

    // Compose 在 Activity 恢復之後才建立 MapView 的情形也要補上 start/resume。
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> Unit // 由 onDispose 銷毀，避免重複呼叫
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            renderer.detach()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { renderer.update(origin, hasRealLocation, destination, route) },
    )
}

/** 地圖 layer 建立一次，之後直接更新 GeoJSON，減少重繪時黑畫面。 */
private class MapRenderer {
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var currentOrigin: GeoPoint? = null
    private var hasRealLocation: Boolean = false
    private var currentDestination: GeoPoint? = null
    private var currentRoute: PlannedRoute? = null
    private var lastCameraKey: String? = null

    fun attach(view: MapView, loadedMap: MapLibreMap, loadedStyle: Style) {
        mapView = view
        map = loadedMap
        style = loadedStyle
        fun source(id: String) = GeoJsonSource(id, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        loadedStyle.addSource(source("navigation-line"))
        loadedStyle.addSource(source("navigation-origin"))
        loadedStyle.addSource(source("navigation-destination"))
        loadedStyle.addLayer(
            LineLayer("navigation-route-shadow", "navigation-line")
                .withProperties(lineColor(AndroidColor.rgb(16, 28, 47)), lineWidth(11f), lineCap(LINE_CAP_ROUND)),
        )
        loadedStyle.addLayer(
            LineLayer("navigation-route", "navigation-line")
                .withProperties(lineColor(AndroidColor.rgb(46, 160, 255)), lineWidth(6f), lineCap(LINE_CAP_ROUND)),
        )
        loadedStyle.addLayer(
            CircleLayer("navigation-car", "navigation-origin")
                .withProperties(
                    circleColor(AndroidColor.rgb(14, 134, 255)),
                    circleRadius(10f),
                    circleStrokeColor(AndroidColor.WHITE),
                    circleStrokeWidth(3f),
                ),
        )
        loadedStyle.addLayer(
            CircleLayer("navigation-pin", "navigation-destination")
                .withProperties(
                    circleColor(AndroidColor.rgb(255, 175, 45)),
                    circleRadius(10f),
                    circleStrokeColor(AndroidColor.WHITE),
                    circleStrokeWidth(3f),
                ),
        )
        redraw()
    }

    fun update(origin: GeoPoint, hasRealLocation: Boolean, destination: GeoPoint?, route: PlannedRoute?) {
        currentOrigin = origin
        this.hasRealLocation = hasRealLocation
        currentDestination = destination
        currentRoute = route
        redraw()
    }

    private fun redraw() {
        val origin = currentOrigin ?: return
        val style = style ?: return
        val map = map ?: return
        (style.getSource("navigation-origin") as? GeoJsonSource)?.setGeoJson(
            Point.fromLngLat(origin.longitude, origin.latitude),
        )
        val target = currentDestination
        (style.getSource("navigation-destination") as? GeoJsonSource)?.let { source ->
            if (target != null) source.setGeoJson(Point.fromLngLat(target.longitude, target.latitude))
            else source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        }
        val linePoints = currentRoute?.geometry.orEmpty()
        (style.getSource("navigation-line") as? GeoJsonSource)?.let { source ->
            if (linePoints.size >= 2) source.setGeoJson(
                LineString.fromLngLats(linePoints.map { Point.fromLngLat(it.longitude, it.latitude) }),
            ) else source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        }
        // 不要跟著每次 GPS 更新重新縮放鏡頭。
        // 首次取得 GPS、目的地或路線改變時，才重新調整視野。
        val cameraKey = "${hasRealLocation}:${target?.latitude},${target?.longitude}:${currentRoute?.geometry?.hashCode()}"
        if (cameraKey == lastCameraKey) return
        lastCameraKey = cameraKey
        mapView?.post {
            if (this.map !== map) return@post
            if (target == null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(origin.latitude, origin.longitude), 14.0))
            } else {
                val builder = LatLngBounds.Builder()
                    .include(LatLng(origin.latitude, origin.longitude))
                    .include(LatLng(target.latitude, target.longitude))
                // 取樣即可取得路線大致邊界，避免數千點都進 Bounds 造成 UI 卡頓。
                linePoints.filterIndexed { index, _ -> index % 20 == 0 || index == linePoints.lastIndex }
                    .forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 76)) }
                    .onFailure { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(origin.latitude, origin.longitude), 13.0)) }
            }
        }
    }

    fun detach() {
        style = null
        map = null
        mapView = null
    }
}
