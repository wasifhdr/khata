package com.wasif.khata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.navigation.KhataNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Held until the first DataStore emission. Both the palette and the
        // start destination come from there, and the start destination is the
        // back-stack root -- it cannot be corrected once NavHost has composed.
        splash.setKeepOnScreenCondition { viewModel.state.value is MainUiState.Loading }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            when (val state = viewModel.state.collectAsStateWithLifecycle().value) {
                MainUiState.Loading -> Unit
                is MainUiState.Ready -> KhataTheme(spec = state.preferences.themeSpec) {
                    KhataNavHost(homeView = state.preferences.homeView)
                }
            }
        }
    }
}
