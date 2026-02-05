package com.example.opsisfacerecognition.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.opsisfacerecognition.core.config.FaceScannerConfig

@Composable
fun OvalOverlay(
    modifier: Modifier = Modifier,
    isFaceDetected: Boolean,
    ovalWidth: Dp,
    ovalHeight: Dp,
    topMargin: Dp,
    onCenterCalculated: (Offset) -> Unit,
) {

    // Determine the border color based on the isFaceDetected
    val ovalColor = if (isFaceDetected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.error
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    // Store the center to avoid redundant callback triggers during recomposition
    val currentCenter = remember { mutableStateOf<Offset?>(null) }

    Canvas(modifier = modifier) {
        // Canvas only draws to pixels not dp
        // Convert dp units to raw pixels based on the current device density
        val widthPx = ovalWidth.toPx()
        val heightPx = ovalHeight.toPx()
        val topPx = topMargin.toPx()

        // Calculate horizontal alignment for centering the oval
        val leftPx = (size.width - widthPx) / 2f

        // Define rectangle for the oval
        val ovalRect = Rect(
            left = leftPx,
            top = topPx,
            right = leftPx + widthPx,
            bottom = topPx + heightPx
        )

        // This offset is for the FaceDetector to know where the face should be.
        val center = Offset(ovalRect.center.x, ovalRect.center.y)

        // Even if the screen is locked in Portrait, we use this check to prevent
        // infinite recomposition loops. We only trigger the callback if the calculated
        // center actually differs from our stored value
        if (currentCenter.value != center) {
            currentCenter.value = center
            onCenterCalculated(center)
        }

        // Create a geometric path in the shape of the oval
        val ovalPath = Path().apply { addOval(ovalRect) }

        // Draw everything except the area inside our oval.
        clipPath(ovalPath, clipOp = ClipOp.Difference) {
            drawRect(surfaceColor)
        }

        // The outline based on the face position
        drawOval(
            color = ovalColor,
            topLeft = ovalRect.topLeft,
            size = ovalRect.size,
            style = Stroke(width = FaceScannerConfig.BORDER_WIDTH_DP.dp.toPx())
        )
    }
}
