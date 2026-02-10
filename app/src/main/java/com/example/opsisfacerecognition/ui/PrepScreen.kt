package com.example.opsisfacerecognition.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.ui.dialogs.PermissionDialog
import com.example.opsisfacerecognition.core.ui.layout.AppScreenContainer
import com.example.opsisfacerecognition.core.permissions.rememberCameraPermissionRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepScreen(
    navController: NavController,
    title: String,
    subtitle: String,
    @DrawableRes illustrationRes: Int,
    buttonText: String,
    tip: String,
    onGo: String,
    modifier: Modifier = Modifier,
) {
    val (camera, ui) = rememberCameraPermissionRequester(
        onGranted = { navController.navigate(onGo) }
    )

    if (ui.showRationaleDialog) {
        PermissionDialog(
            title = "Camera permission required",
            message = "Camera access is required to perform face scanning.",
            confirmText = "Allow",
            dismissText = "Cancel",
            onConfirm = {
                ui.dismissRationale()
                camera.request()
            },
            onDismiss = ui.dismissRationale
        )
    }

    if (ui.showSettingsDialog) {
        PermissionDialog(
            title = "Permission permanently denied",
            message = "Camera access has been blocked. Please enable it from app settings.",
            confirmText = "Open settings",
            dismissText = "Cancel",
            onConfirm = {
                ui.dismissSettings()
                camera.openAppSettings()
            },
            onDismiss = ui.dismissSettings
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }  ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        AppScreenContainer(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = title,
                fontFamily = displayFontFamily,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = subtitle,
                fontFamily = bodyFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(0.7f)
            )

            Spacer(Modifier.height(32.dp))

            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(300.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(32.dp))

            PrepRequirementsRow()

            Spacer(Modifier.weight(1f))

            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = { camera.request() }
            ) {
                Text(
                    buttonText,
                    fontFamily = displayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = tip,
                fontFamily = bodyFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.7f)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrepRequirementsRow() {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RequirementItem(icon = Icons.Outlined.Face, label = "Uncovered face")
        RequirementItem(icon = Icons.Outlined.LightMode, label = "Good lighting")
    }
}

@Composable
private fun RequirementItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(22.dp)
                .alpha(0.7f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontFamily = bodyFontFamily,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.alpha(0.7f)
        )
    }
}