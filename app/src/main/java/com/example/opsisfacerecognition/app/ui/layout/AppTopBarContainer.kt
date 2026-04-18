package com.example.opsisfacerecognition.app.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val insets = LocalAppInsets.current
    val maxW = insets.maxContentWidth

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .let { base ->
                    if (maxW != null) base.widthIn(max = maxW).fillMaxWidth()
                    else base.fillMaxWidth()
                }
                .padding(horizontal = insets.horizontal)
        ) {
            content()
        }
    }
}
