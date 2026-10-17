package com.example.testapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.testapp.data.AiBoxGateway
import com.example.testapp.model.GeoPoint
import com.example.testapp.model.UiComponent
import com.example.testapp.model.UiDescription
import com.example.testapp.model.UiJsonParser
import com.example.testapp.model.UserAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MicState { Idle, Listening, Processing }

const val INITIAL_DESTINATION = ""

/** 主畫面狀態;A2 可在此接上真正的 WebSocket gateway 與行駛模式 */
class CockpitController(
    private val gateway: AiBoxGateway,
    private val scope: CoroutineScope,
) {
    var ui by mutableStateOf<UiDescription?>(null)
        private set
    var micState by mutableStateOf(MicState.Idle)
        private set
    var destination by mutableStateOf<String?>(null)
        private set
    var destinationPoint by mutableStateOf<GeoPoint?>(null)
        private set

    var suggestedPlaces by mutableStateOf<List<Pair<String, GeoPoint>>>(emptyList())
        private set

    private fun candidateLocation(card: UiComponent.CandidateCard): GeoPoint? {
        fun number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
            when (val v = card.attributes[key]) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            }
        }
        val lat = number("latitude", "lat") ?: return null
        val lon = number("longitude", "lon", "lng") ?: return null
        return GeoPoint(lat, lon).takeIf { it.isValid() }
    }

    private var taskId: String? = null
    private var version = -1
    private var processingTimeout: Job? = null

    /** 錄音送出之後才顯示 AI 建議;啟動、收合、確認之後 D 推來的狀態只記錄版本,不顯示 */
    private var awaitingResult = false

    init {
        scope.launch { gateway.messages.collect(::onMessage) }
    }

    fun onMicPressed() {
        micState = MicState.Listening
        gateway.startVoiceInput()
    }

    fun onMicReleased() {
        micState = MicState.Processing
        awaitingResult = true
        gateway.stopVoiceInput()
        processingTimeout?.cancel()
        processingTimeout = scope.launch {
            // D 轉 BC 的逾時是 30 秒,首次辨識還要載入模型,不能比它短
            delay(30_000)
            micState = MicState.Idle
            // 逾時仍沒有結果就不再等待;面板已顯示時要繼續接收觸控操作的回應
            if (ui == null) awaitingResult = false
        }
    }

    fun onAction(action: UserAction) {
        if (action is UserAction.Confirm) {
            val selected = ui?.components
                ?.filterIsInstance<UiComponent.CandidateCard>()
                ?.firstOrNull { it.candidateId == action.candidateId }
            destination = selected?.name ?: action.candidateId
            destinationPoint = selected?.attributes?.let { values ->
                fun number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
                    val v = values[key]
                    when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    }
                }
                val latitude = number("latitude", "lat")
                val longitude = number("longitude", "lon", "lng")
                if (latitude != null && longitude != null) {
                    GeoPoint(latitude, longitude).takeIf { it.isValid() }
                } else null
            }
            suggestedPlaces = emptyList()
            ui = null
            awaitingResult = false
            taskId = null
            version = -1
        }
        gateway.sendAction(action)
    }

    /** 可在無 AI Box 情況下手動輸入目的地；清除既有座標，重新進行地點解析。 */
    fun navigateTo(name: String) {
        if (name.isBlank()) return
        destination = name.trim()
        destinationPoint = null
    }

    fun dismissPanel() {
        ui = null
        suggestedPlaces = emptyList()
        awaitingResult = false
    }

    private fun onMessage(json: String) {
        val envelope = UiJsonParser.parseEnvelope(json) ?: return
        if (!envelope.type.equals("ui_update", ignoreCase = true)) return
        // 同一任務中版本較舊(或重複)的訊息一律丟棄
        if (envelope.taskId == taskId && envelope.version <= version) return
        taskId = envelope.taskId
        version = envelope.version
        if (!awaitingResult) return
        ui = UiJsonParser.parseUiDescription(envelope.payload)
        suggestedPlaces = ui?.components.orEmpty()
            .filterIsInstance<UiComponent.CandidateCard>()
            .mapNotNull { card -> candidateLocation(card)?.let { (card.name ?: "推薦地點") to it } }
            .distinctBy { it.second }
        processingTimeout?.cancel()
        if (micState == MicState.Processing) micState = MicState.Idle
    }
}
