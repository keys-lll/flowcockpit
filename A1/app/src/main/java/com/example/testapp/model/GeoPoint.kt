package com.example.testapp.model

/** 地理位置，順序為緯度/經度（GeoJSON 另使用經度/緯度）。 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        latitude.isFinite() && longitude.isFinite()
}

/** 原始版 D 模組所使用的展示起點；絕非裝置 GPS 位置。 */
val DEMO_ORIGIN = GeoPoint(23.718645, 120.573271)
