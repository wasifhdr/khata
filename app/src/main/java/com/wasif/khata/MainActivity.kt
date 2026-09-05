package com.wasif.khata

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.notify.EXTRA_TRANSACTION_ID
import com.wasif.khata.core.permission.AndroidSmsPermissionChecker
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.navigation.KhataNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // shouldShowRequestPermissionRationale only answers for an Activity, and the
    // checker is a singleton with only an application context.
    @Inject lateinit var permissionChecker: AndroidSmsPermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Held until the first DataStore emission. Both the palette and the
        // start destination come from there, and the start destination is the
        // back-stack root -- it cannot be corrected once NavHost has composed.
        splash.setKeepOnScreenCondition { viewModel.state.value is MainUiState.Loading }

        // enableEdgeToEdge() with no arguments paints a translucent scrim behind the
        // navigation bar, which reads as a band of different colour across the foot
        // of every screen. The field is the background of this app, so the bars get
        // nothing of their own and the gesture handle floats on it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Android adds its own scrim behind a transparent navigation bar unless
        // told the app has handled contrast itself.
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        permissionChecker.activity = this
        // Checked here, on the main thread: ContextCompat's permission check needs a
        // real ActivityThread. MainActivity opens on every launch, so this is no less
        // reliable a trigger than the Application would have been.
        viewModel.onPermissionKnown(permissionChecker.isGranted())

        setContent {
            when (val state = viewModel.state.collectAsStateWithLifecycle().value) {
                MainUiState.Loading -> Unit
                is MainUiState.Ready -> KhataTheme(spec = state.preferences.themeSpec) {
                    KhataNavHost(
                        homeView = state.preferences.homeView,
                        sharedPlaceText = sharedPlaceText,
                        sharedImageUri = sharedImageUri,
                        settleTransactionId = intent
                            ?.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
                            ?.takeIf { it > 0L },
                    )
                }
            }
        }
    }

    /**
     * Null unless this launch was a share. Read once, in onCreate: MainActivity is
     * launchMode standard, so a share starts a fresh instance rather than delivering
     * a new intent to a running one.
     */
    private val sharedPlaceText: String?
        get() = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

    /** A picture shared from another app. Read once, for the same reason as above. */
    private val sharedImageUri: String?
        get() = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("image/") == true }
            ?.let { intent ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toString()
            }

    override fun onDestroy() {
        permissionChecker.activity = null
        super.onDestroy()
    }
}
