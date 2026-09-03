package com.wasif.khata.feature.widget

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.MainUiState
import com.wasif.khata.MainViewModel
import com.wasif.khata.core.ui.theme.KhataTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickEntryActivity : ComponentActivity() {

    private val viewModel: QuickEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false

        setContent {
            // No splash gate, unlike MainActivity: this window is transparent, so a
            // frame of nothing is invisible rather than a flash of the wrong palette.
            val preferences: MainViewModel = hiltViewModel()
            val main = preferences.state.collectAsStateWithLifecycle().value
            if (main !is MainUiState.Ready) return@setContent

            KhataTheme(spec = main.preferences.themeSpec) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                // Durable rather than a direct finish(), so the sheet can play its
                // exit before the window goes.
                var saved by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            QuickEntryEffect.Saved -> saved = true
                        }
                    }
                }

                QuickEntryScreen(
                    state = state,
                    actions = viewModel,
                    saved = saved,
                    onClosed = ::finish,
                )
            }
        }
    }
}
