package com.wasif.khata.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lowConfidenceIsAnnouncedAndNotOnlyColoured() {
        // Colour is never the sole signal. A low-confidence row carries a ring
        // and a word; the ring needs a content description so the word is not
        // the only non-visual channel either.
        compose.setContent {
            KhataTheme {
                CategoryDot(token = "category_green", lowConfidence = true)
            }
        }
        compose.onNodeWithContentDescription("Low confidence").assertIsDisplayed()
    }

    @Test
    fun anUnknownTokenStillRenders() {
        // A category token can arrive from seeded data that predates a palette
        // change. It must degrade to a neutral dot, never crash a 3,000-row list.
        compose.setContent {
            KhataTheme {
                CategoryDot(token = "category_does_not_exist")
            }
        }
        compose.onNodeWithContentDescription("Uncategorised").assertIsDisplayed()
    }

    @Test
    fun contextHeaderShowsBothLines() {
        compose.setContent {
            KhataTheme {
                ContextHeader(heading = "August 2026", subline = "142 entries · 6 days left")
            }
        }
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("142 entries · 6 days left").assertIsDisplayed()
    }
}
