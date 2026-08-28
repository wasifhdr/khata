package com.wasif.khata.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalCategoryColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

@Composable
fun KhataTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalMotion provides Motion(),
        LocalCategoryColors provides KhataPalette.categories,
    ) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = KhataTypography,
            content = content,
        )
    }
}
