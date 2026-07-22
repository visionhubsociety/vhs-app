package app.vercel.visionhubs

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import app.vercel.visionhubs.ui.screen.MainScreen
import app.vercel.visionhubs.ui.theme.AppTheme
import app.vercel.visionhubs.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            val themeMode by viewModel.selectedThemeMode.collectAsState()
            val monetEnabled by viewModel.monetEnabled.collectAsState()

            AppTheme(
                themeMode = themeMode,
                dynamicColor = monetEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}