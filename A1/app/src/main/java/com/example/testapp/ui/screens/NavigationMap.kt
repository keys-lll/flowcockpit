package com.example.testapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
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
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * 真實 OpenStreetMap 路網的地圖畫面（MapLibre + OSRM）。
 * 自動使用 GPS / Network 定位，若無權限或暫時無定位便使用示範座標。
 * 沒有網路時，MapLibre 圖磚只會顯示裝置已快取部分，路線也只顯示先前已成功取得者。
 */
@Composable
fun NavigationMap(
    destination: String?,
    destinationPoint: GeoPoint? = null,
    suggestions: List<Pair<String, GeoPoint>> = emptyList(),
    onNavigateTo: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { RouteRepository(context) }
    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var gpsFix by remember { mutableStateOf<GeoPoint?>(null) }
    var lastFixElapsedMs by remember { mutableStateOf(0L) }
    var locationTick by remember { mutableStateOf(0L) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        permissionGranted = permissions.values.any { it } || hasLocationPermission(context)
        if (!permissionGranted) gpsFix = null
    }
    // App 啟動即取得定位授權；拒絕時不阻擋地圖與 AI 功能。
    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        } else {
            permissionGranted = true
        }
    }
    // 每秒更新 GPS 狀態；失去新定位不再重置已取得的位置。
    LaunchedEffect(Unit) {
        while (true) {
            locationTick = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }
    // 每次心跳重新檢查系統定位服務與權限，避免 GPS 在設定中切換後狀態卡住。
    val locationManager = remember(context) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val gpsAvailable = locationTick >= 0 && runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }.getOrDefault(false)
    val networkAvailable = locationTick >= 0 && runCatching {
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
    val locationAvailable = gpsAvailable || networkAvailable
    val actualPermission = hasLocationPermission(context)
    LaunchedEffect(actualPermission) {
        permissionGranted = actualPermission
        if (!actualPermission) gpsFix = null
    }
    val freshFix = actualPermission && locationAvailable && gpsFix != null &&
        locationTick - lastFixElapsedMs in 0L..15_000L
    // 只在「從未取得真實座標」時用示範起點；訊號遺失保留上次座標，避免跨縣市跳動。
    val origin = gpsFix ?: DEMO_ORIGIN
    val waitingForGps = !freshFix
    LaunchedEffect(origin, freshFix) { NavigationLocation.update(origin.takeIf { freshFix }) }
    DeviceLocationTracker(actualPermission, gpsAvailable, networkAvailable, onFix = { point ->
        gpsFix = point
        lastFixElapsedMs = SystemClock.elapsedRealtime()
    })

    // 地圖標記依最新定位更新；路線只有首度 GPS 定位或明顯移動後才重新規劃。
    var routingOrigin by remember { mutableStateOf<GeoPoint?>(null) }
    var routingFromGps by remember { mutableStateOf(false) }
    var lastRerouteMs by remember { mutableStateOf(0L) }
    LaunchedEffect(origin, freshFix) {
        val hasGps = freshFix
        val distance = FloatArray(1)
        routingOrigin?.let { previous ->
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                origin.latitude, origin.longitude, distance,
            )
        }
        val now = SystemClock.elapsedRealtime()
        if ((hasGps && routingOrigin == null) ||
            (hasGps && distance[0] >= 150f && now - lastRerouteMs >= 30_000L)) {
            routingOrigin = origin
            routingFromGps = true
            lastRerouteMs = now
        }
    }

    var route by remember { mutableStateOf<PlannedRoute?>(null) }
    var resolvedDestination by remember { mutableStateOf<GeoPoint?>(destinationPoint) }
    var routingMessage by remember { mutableStateOf("準備規劃路線") }
    LaunchedEffect(destination, destinationPoint, routingOrigin) {
        route = null
        resolvedDestination = destinationPoint
        if (destination.isNullOrBlank()) {
            routingMessage = "請由 AI 或手動輸入目的地"
            return@LaunchedEffect
        }
        val start = routingOrigin
        if (start == null) {
            routingMessage = "等待 GPS 定位，取得位置後自動規劃路線"
            return@LaunchedEffect
        }
        routingMessage = "正在從目前位置規劃道路路線…"
        when (val result = repository.plan(start, destination, destinationPoint)) {
            is RouteResult.Success -> {
                route = result.route
                resolvedDestination = result.route.destination
                routingMessage = if (result.route.fromCache) "已載入快取路線（離線模式）" else "道路路線已規劃"
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
            hasRealLocation = freshFix,
            destination = resolvedDestination,
            route = route,
            suggestions = suggestions,
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
                    if (waitingForGps) "等待 GPS" else "GPS 已定位",
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

/** 監聽 GPS 與網路定位。定位服務啟閉時由 Compose 自動重新註冊。 */
@Suppress("MissingPermission")
@Composable
private fun DeviceLocationTracker(
    enabled: Boolean,
    gpsAvailable: Boolean,
    networkAvailable: Boolean,
    onFix: (GeoPoint) -> Unit,
) {
    val context = LocalContext.current
    val latestOnFix by androidx.compose.runtime.rememberUpdatedState(onFix)
    DisposableEffect(context, enabled, gpsAvailable, networkAvailable) {
        if (!enabled || (!gpsAvailable && !networkAvailable) || !hasLocationPermission(context)) {
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location: Location ->
            // 忽略超過 15 秒的快取位置，不能把舊資料當作剛取得的 GPS。
            val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
            if (ageMillis in 0L..15_000L) {
                GeoPoint(location.latitude, location.longitude)
                    .takeIf { it.isValid() }
                    ?.let { latestOnFix(it) }
            }
        }
        for ((provider, available) in listOf(
            LocationManager.GPS_PROVIDER to gpsAvailable,
            LocationManager.NETWORK_PROVIDER to networkAvailable,
        )) {
            if (available) {
                runCatching {
                    manager.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                    // 不要求移動距離，靜止時也能收到更新。
                    manager.requestLocationUpdates(provider, 1000L, 0f, listener)
                }
            }
        }
        onDispose { manager.removeUpdates(listener) }
    }
}
