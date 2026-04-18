package com.example.opsisfacerecognition.app.ui.layout

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("WindowSizeClass not provided")
}

@Stable
data class AppInsets(
    val horizontal: Dp,
    val maxContentWidth: Dp? = null,
    val maxContentHeight: Dp? = null,
)

val LocalAppInsets = staticCompositionLocalOf {
    AppInsets(horizontal = 24.dp)
}

@Composable
fun ProvideAppLayout(windowSizeClass: WindowSizeClass, content: @Composable () -> Unit) {
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val insets = if (isTablet) {
        AppInsets(
            horizontal = 32.dp,
            maxContentWidth = 560.dp,
            maxContentHeight = 840.dp,
        )
    } else {
        AppInsets(horizontal = 24.dp)
    }

    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClass,
        LocalAppInsets provides insets
    ) {
        content()
    }
}
