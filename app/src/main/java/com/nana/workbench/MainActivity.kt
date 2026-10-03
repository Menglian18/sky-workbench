package com.nana.workbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.ui.nav.NanaApp
import com.nana.workbench.ui.theme.NanaTheme
import com.nana.workbench.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeState by themeViewModel.state.collectAsState()

            NanaTheme(
                palette = themeState.palette,
                fontScale = themeState.fontScale,
                followSystemDark = themeState.followSystemDark,
            ) {
                NanaApp()
            }
        }
    }
}
