package com.example.opsisfacerecognition.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.ui.components.StatusAvatar
import com.example.opsisfacerecognition.core.ui.layout.AppScreenContainer
import com.example.opsisfacerecognition.navigation.Routes
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySuccess(
    navController: NavController,
    title: String,
    description: String,
    viewmodel: FaceRecognizerViewModel = hiltViewModel()
) {
    val verifiedUser by viewmodel.pendingUser.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        AppScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(24.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar
                    StatusAvatar(
                        badgeIcon = Icons.Rounded.Verified,
                        badgeTint = MaterialTheme.colorScheme.surface,
                        badgeColor = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(28.dp))

                    // Title
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = displayFontFamily,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Description
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = bodyFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    if (!verifiedUser?.fullName.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "You are ${verifiedUser?.fullName}!",
                            fontWeight = FontWeight.Medium,
                            fontFamily = displayFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Button
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                ) {
                    Text(
                        text = "Continue",
                        fontFamily = displayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
