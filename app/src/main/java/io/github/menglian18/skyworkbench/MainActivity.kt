package io.github.menglian18.skyworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.ui.nav.SkyApp
import io.github.menglian18.skyworkbench.ui.theme.SkyTheme
import io.github.menglian18.skyworkbench.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeState by themeViewModel.state.collectAsState()

            SkyTheme(
                palette = themeState.palette,
                fontScale = themeState.fontScale,
                followSystemDark = themeState.followSystemDark,
            ) {
                SkyApp()
            }
        }
    }
}
