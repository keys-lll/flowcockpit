package com.example.testapp.navigation

import com.example.testapp.model.DEMO_ORIGIN
import com.example.testapp.model.GeoPoint

/** 與舊 AI Box 上傳流程共用目前起點；未授權 GPS 時為清楚標示的展示座標。 */
object NavigationLocation {
    @Volatile var current: GeoPoint = DEMO_ORIGIN
}
