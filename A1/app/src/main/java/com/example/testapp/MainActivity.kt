package com.example.testapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.testapp.data.HttpAiBox
import com.example.testapp.ui.screens.CockpitController
import com.example.testapp.ui.screens.CockpitScreen
import com.example.testapp.ui.theme.TestAPPTheme

/** Android 模擬器預設位址；實體裝置可在 App 內改為筆電區網 IP。 */
private const val DEFAULT_AI_BOX_HOST = "10.0.2.2:8000"
private const val AI_BOX_PREFS = "ai_box_connection"
private const val AI_BOX_HOST_KEY = "host"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences(AI_BOX_PREFS, MODE_PRIVATE)
        setContent {
            TestAPPTheme {
                val scope = rememberCoroutineScope()
                val host = remember { preferences.getString(AI_BOX_HOST_KEY, DEFAULT_AI_BOX_HOST) ?: DEFAULT_AI_BOX_HOST }
                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {}
                LaunchedEffect(Unit) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }

                val controller = remember {
                    CockpitController(HttpAiBox(applicationContext, scope, host), scope)
                }
                Surface(Modifier.fillMaxSize()) {
                    CockpitScreen(
                        controller = controller,
                        modifier = Modifier.safeDrawingPadding(),
                        aiBoxHost = host,
                        onAiBoxHostChange = { newHost ->
                            preferences.edit().putString(AI_BOX_HOST_KEY, newHost).apply()
                            recreate() // 取消舊連線及協程，使用新位址重新連接
                        },
                    )
                }
            }
        }
    }
}
