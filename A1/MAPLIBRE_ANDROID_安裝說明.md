# FlowCockpit / 旅途知音 – MapLibre Android 導航版

## 這個版本已改什麼

- 保留原本 A1 Android Jetpack Compose AI 座艙、語音與推薦面板。
- 將 `NavigationMap.kt` 的虛擬道路 Canvas 換成 MapLibre Android SDK，使用 OpenFreeMap / OpenStreetMap 真實地圖。
- 真正的道路路線由 OSRM 示範路由服務算出 GeoJSON；藍色線段沿路網繪製，不再使用畫面上的虛構線條。
- 出發點預設是原專案後端的 **雲林示範座標 (23.718645, 120.573271)**，明確標為非 GPS；可點「使用 GPS」授權位置。GPS 與語音搜尋會共用同一個起點。
- 使用「搜尋目的地」可直接輸入地點，無 AI Box 仍能展示路線；點 AI 推薦「確認」也會更新導航。
- `d/server_d.py` 將 BC 取得的 `latitude`、`longitude` 一併傳給 Android，不必僅靠地點名稱猜座標。
- 上次成功計算的同起終點道路路線可從本機快取載入；未有快取時不會用直線冒充可行駛路線。

## 用 Android Studio 建置並安裝

1. 開啟 Android Studio，選 **Open** 並選 `A1/` 資料夾（**不是**根目錄）。
2. 使用 JDK 21，安裝 Android SDK **API 37**，讓 Gradle Sync 下載依賴（需連網）。
3. 選 `app` → **Build > Build APK(s)**，輸出 `A1/app/build/outputs/apk/debug/app-debug.apk`。
4. 將 `app-debug.apk` 安裝在 Android **15 (API 35) 或更新版本**的裝置。APP 固定橫向。
5. 打開後會顯示雲林示範座標地圖，系統會查詢「國立雲林科技大學」的導航路線。右上方可搜尋其他地點、按「使用 GPS」改用裝置定位。
6. 如果要使用完整 AI 語音，`A1/app/src/main/java/com/example/testapp/MainActivity.kt` 的 `AI_BOX_HOST` 需設成 AI Box 的區網 IP（`10.0.2.2:8000` 只適用 Android 模擬器與本機後端）。並啟動 `d`、`bc` 服務。

## 不用 Android Studio：GitHub Actions 自動產出 APK

將修改後的整個專案推送回 GitHub，再進 GitHub 倉庫的 **Actions → Build Android APK (A1 MapLibre) → Run workflow**；成功後在該次執行頁面的 **Artifacts** 下載 `FlowCockpit-MapLibre-debug-APK`，解壓後得到 `app-debug.apk`。

**注意：** 此 ZIP 中沒有已編譯的 APK；若尚未執行 Android Studio 或 GitHub Actions，不應把專案 ZIP 當作可直接安裝的 Android 應用。

## 網路、離線與服務條件

| 功能 | 連網 | 斷網 |
| --- | --- | --- |
| MapLibre / OpenFreeMap 地圖 | 真實地圖 | 僅既有 SDK 圖磚快取；**並未實作整區離線圖資下載** |
| OSRM 路線規劃 | 可查詢實際道路 | 只能顯示同一起終點已快取的路線；**不能離線計算新路線** |
| Nominatim 地點名稱查詢 | 支援中文名稱；結果有可能同名歧義 | 僅快取過的地點；建議由 AI Box 提供精確經緯度 |
| AI Box | 原本音訊、WebSocket 與推薦介面 | 原專案的備援 JSON 行為 |

本版本使用公共免費服務做概念驗證，**不可當作正式道路導航、安全駕駛指引或保證的商用服務**。OSRM 公開路由服務無 SLA、不提供即時塞車及逐轉向語音導航。正式產品應改為自架 OSRM / Valhalla / GraphHopper 並整合離線圖資、地理編碼服務與路由圖；也可選用授權的商用服務。Nominatim 公共端有每秒一筆等政策與 User-Agent 要求，本程式會快取成功查詢並做裝置內節流，但不適合大規模 App 直接呼叫。請尊重 OpenFreeMap 圖磚政策（尤其離線下載限制）與圖資授權。

## 主要修改位置

- `A1/app/build.gradle.kts`：加入 MapLibre SDK。
- `A1/app/src/main/java/com/example/testapp/ui/screens/MapLibreView.kt`：地圖與真實道路線段、起點、終點。
- `A1/app/src/main/java/com/example/testapp/ui/screens/NavigationMap.kt`：UI、GPS、手動輸入與 ETA。
- `A1/app/src/main/java/com/example/testapp/navigation/RouteRepository.kt`：地理編碼、OSRM、快取。
- `A1/app/src/main/java/com/example/testapp/navigation/NavigationLocation.kt`：語音與地圖共用定位。
- `A1/app/src/main/java/com/example/testapp/ui/screens/CockpitController.kt`：AI 選定候選地點的座標。
- `A1/app/src/main/java/com/example/testapp/data/HttpAiBox.kt`：上傳 GPS / 示範座標至既有 D 後端。
- `A1/app/src/main/java/com/example/testapp/model/UiJsonParser.kt`：保留 D 格式中地點座標。
- `d/server_d.py`：補上 BC → D → Android 的座標傳遞。
- `.github/workflows/build-android-apk.yml`：自動產出可下載的 debug APK。

`FlowCockpit_Integrated_Demo/A1` 是倉庫內另一套整合備份，本次修改的是頂層 `A1/`，請以頂層版本開發與編譯。
