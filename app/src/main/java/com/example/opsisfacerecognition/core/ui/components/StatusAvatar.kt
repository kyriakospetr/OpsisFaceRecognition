package com.example.opsisfacerecognition.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.opsisfacerecognition.R

@Composable
fun StatusAvatar(
    badgeIcon: ImageVector,
    badgeTint: Color,
    badgeColor: Color
) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.face),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ),
            contentScale = ContentScale.Crop,
            alpha = 0.9f
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(color = badgeColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = badgeTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
