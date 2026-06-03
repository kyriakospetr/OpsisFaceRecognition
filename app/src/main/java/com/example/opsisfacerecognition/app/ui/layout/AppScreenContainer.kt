package com.example.opsisfacerecognition.app.ui.layout

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
    val maxW = insets.maxContentWidth
    val maxH = insets.maxContentHeight

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (maxH != null) Alignment.Center else Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .let { base ->
                    if (maxH != null) base.fillMaxHeight().heightIn(max = maxH)
                    else base.fillMaxHeight()
                }
                .let { base ->
                    if (maxW != null) base.widthIn(max = maxW).fillMaxWidth()
                    else base.fillMaxWidth()
                }
                .padding(horizontal = insets.horizontal),
            content = content
        )
    }
}
