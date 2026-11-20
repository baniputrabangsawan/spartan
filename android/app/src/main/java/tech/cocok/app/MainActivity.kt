package tech.cocok.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import tech.cocok.app.ui.CocokApp
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.CocokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CocokTheme {
                Surface(color = CocokColors.Latar, modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                    ) {
                        CocokApp()
                    }
                }
            }
        }
    }
}
