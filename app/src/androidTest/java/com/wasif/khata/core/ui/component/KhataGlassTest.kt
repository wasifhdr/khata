package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.wasif.khata.core.ui.theme.ThemeSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KhataGlassTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glassRendersItsContent() {
        compose.setContent {
            KhataTheme {
                val haze = remember { HazeState() }
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    KhataGlass(hazeState = haze) { Text("Wallet") }
                }
            }
        }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }

    @Test
    fun glassStillRendersContentWhenTheFieldIsOff() {
        // At Off the blur path is skipped entirely for a solid fill. The
        // content must survive that branch.
        compose.setContent {
            KhataTheme(spec = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)) {
                val haze = remember { HazeState() }
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    KhataGlass(hazeState = haze) { Text("Wallet") }
                }
            }
        }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }
}
