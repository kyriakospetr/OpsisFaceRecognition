package com.example.opsisfacerecognition.ui.layout

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("WindowSizeClass not provided")
}

@Stable
data class AppInsets(
    val horizontal: androidx.compose.ui.unit.Dp,
    val maxContentWidth: androidx.compose.ui.unit.Dp? = null
)

val LocalAppInsets = staticCompositionLocalOf {
    AppInsets(horizontal = 24.dp, maxContentWidth = null)
}

@Composable
fun ProvideAppLayout(windowSizeClass: WindowSizeClass, content: @Composable () -> Unit) {
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val insets = if (isTablet) {
        AppInsets(horizontal = 32.dp, maxContentWidth = 560.dp)
    } else {
        AppInsets(horizontal = 24.dp, maxContentWidth = null)
    }

    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClass,
        LocalAppInsets provides insets
    ) {
        content()
    }
}
