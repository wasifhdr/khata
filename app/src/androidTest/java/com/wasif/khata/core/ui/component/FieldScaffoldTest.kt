package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.core.ui.theme.ThemeSpec
import org.junit.Rule
import org.junit.Test

class FieldScaffoldTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun contentRendersAboveTheField() {
        compose.setContent {
            KhataTheme {
                FieldScaffold(Modifier.fillMaxSize()) { Text("above the mesh") }
            }
        }

        compose.onNodeWithText("above the mesh").assertIsDisplayed()
    }

    @Test
    fun contentStillRendersAtZeroIntensityWhenTheMeshIsGone() {
        // At Off the pools are skipped. A scaffold that dropped its content on
        // that path would take the whole screen with it -- and the grain still
        // draws there, so this also covers the shader compiling on the Off path.
        compose.setContent {
            KhataTheme(spec = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)) {
                FieldScaffold(Modifier.fillMaxSize()) { Text("still here") }
            }
        }

        compose.onNodeWithText("still here").assertIsDisplayed()
    }
}
