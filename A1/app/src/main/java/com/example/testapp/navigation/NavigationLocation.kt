package com.example.testapp.navigation

import com.example.testapp.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 提供給 AI 語音搜尋使用的裝置即時位置；未取得定位時保持 null。 */
object NavigationLocation {
    private val _state = MutableStateFlow<GeoPoint?>(null)
    val state: StateFlow<GeoPoint?> = _state
    val current: GeoPoint? get() = _state.value

    fun update(point: GeoPoint?) {
        _state.value = point
    }
}
