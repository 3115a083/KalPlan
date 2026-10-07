package cc.stkmn.kalplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cc.stkmn.kalplan.ui.KalPlanApp
import cc.stkmn.kalplan.ui.theme.KalPlanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KalPlanTheme {
                KalPlanApp()
            }
        }
    }
}
