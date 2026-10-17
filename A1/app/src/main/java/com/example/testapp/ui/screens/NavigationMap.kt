package com.example.testapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testapp.model.DEMO_ORIGIN
import com.example.testapp.model.GeoPoint
import com.example.testapp.navigation.NavigationLocation
import com.example.testapp.navigation.PlannedRoute
import com.example.testapp.navigation.RouteRepository
import com.example.testapp.navigation.RouteResult
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * 真實 OpenStreetMap 路網的地圖畫面（MapLibre + OSRM）。
 * 預設起點是示範模式，使用者可以按「使用 GPS」切到裝置定位。
 * 沒有網路時，MapLibre 圖磚只會顯示裝置已快取部分，路線也只顯示先前已成功取得者。
 */
@Composable
fun NavigationMap(
    destination: String?,
    destinationPoint: GeoPoint? = null,
    onNavigateTo: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { RouteRepository(context) }
    var gpsRequested by remember { mutableStateOf(false) }
    var gpsFix by remember { mutableStateOf<GeoPoint?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        gpsRequested = permissions.values.any { it }
        if (!gpsRequested) gpsFix = null
    }

    // 示範定位不應冒充真實位置。若 GPS 尚未定位，仍顯示標示清楚的示範起點。
    val origin = if (gpsRequested) gpsFix ?: DEMO_ORIGIN else DEMO_ORIGIN
    val usingDemoOrigin = !gpsRequested || gpsFix == null
    LaunchedEffect(origin) { NavigationLocation.current = origin }
    DeviceLocationTracker(gpsRequested, onFix = { gpsFix = it })

    var route by remember { mutableStateOf<PlannedRoute?>(null) }
    var resolvedDestination by remember { mutableStateOf<GeoPoint?>(destinationPoint) }
    var routingMessage by remember { mutableStateOf("準備規劃路線") }
    LaunchedEffect(destination, destinationPoint, origin) {
        route = null
        resolvedDestination = destinationPoint
        if (destination.isNullOrBlank()) {
            routingMessage = "請由 AI 或手動輸入目的地"
            return@LaunchedEffect
        }
        routingMessage = "正在查詢真實道路路線…"
        when (val result = repository.plan(origin, destination, destinationPoint)) {
            is RouteResult.Success -> {
                route = result.route
                resolvedDestination = result.route.destination
                routingMessage = if (result.route.fromCache) "離線快取路線 · 非即時路況" else "道路路線已規劃 · 非即時路況"
            }
            is RouteResult.Error -> {
                routingMessage = result.message
                resolvedDestination = result.destination
            }
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchValue by remember { mutableStateOf("") }

    Box(modifier) {
        MapLibreView(
            origin = origin,
            destination = resolvedDestination,
            route = route,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("前往 ${destination ?: "未選擇目的地"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(routingMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (usingDemoOrigin) "起點：雲林示範座標（非 GPS）" else "起點：裝置 GPS 定位",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = {
                    if (gpsRequested) {
                        gpsRequested = false
                        gpsFix = null
                    } else if (hasLocationPermission(context)) {
                        gpsRequested = true
                    } else {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ))
                    }
                }) { Text(if (gpsRequested) "示範定位" else "使用 GPS") }
                TextButton(onClick = { showSearch = !showSearch }) {
                    Text(if (showSearch) "關閉搜尋" else "搜尋目的地")
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = searchValue,
                        onValueChange = { searchValue = it },
                        label = { Text("輸入台灣地點名稱") },
                        singleLine = true,
                        modifier = Modifier.width(246.dp),
                    )
                    Button(
                        enabled = searchValue.isNotBlank(),
                        onClick = {
                            onNavigateTo(searchValue.trim())
                            showSearch = false
                        },
                    ) { Text("規劃路線") }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (route != null) {
                    val eta = LocalTime.now().plusSeconds(route!!.durationSeconds.toLong())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    Stat(eta, "預計抵達")
                    Stat("%.1f km".format(java.util.Locale.TAIWAN, route!!.distanceMeters / 1000), "道路距離")
                    Stat("${ceil(route!!.durationSeconds / 60).toInt()} 分", "預估車程")
                } else {
                    Stat("—", "預計抵達")
                    Stat("—", "道路距離")
                    Stat("—", "預估車程")
                }
            }
        }
        Text(
            "© OpenStreetMap contributors · OpenFreeMap · OSRM 示範路由 | 僅供展示",
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** 持續取得系統定位。需使用者主動按鍵同意；沒有定位時明確顯示示範起點。 */
@Suppress("MissingPermission")
@Composable
private fun DeviceLocationTracker(enabled: Boolean, onFix: (GeoPoint) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        if (!enabled || !hasLocationPermission(context)) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { position ->
            GeoPoint(position.latitude, position.longitude)
                .takeIf { it.isValid() }
                ?.let(onFix)
        }
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                    manager.requestLocationUpdates(provider, 5000L, 100f, listener)
                }
            }
        }
        onDispose { manager.removeUpdates(listener) }
    }
}
