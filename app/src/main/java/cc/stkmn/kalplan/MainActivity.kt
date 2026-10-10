package cc.stkmn.kalplan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import cc.stkmn.kalplan.ui.KalPlanApp

class MainActivity : ComponentActivity() {
    private val openedRequest = mutableStateOf<Pair<String?, String?>>(null to null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openedRequest.value = intent.getStringExtra("request_id") to intent.getStringExtra("request_action")
        setContent { KalPlanApp(openedRequest.value) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedRequest.value = intent.getStringExtra("request_id") to intent.getStringExtra("request_action")
    }
}
