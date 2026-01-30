package com.example.opsisfacerecognition.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val insets = LocalAppInsets.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = insets.horizontal)
                .let { base ->
                    val maxW = insets.maxContentWidth
                    if (maxW != null) base.widthIn(max = maxW) else base.fillMaxWidth()
                },
            content = content
        )
    }
}

