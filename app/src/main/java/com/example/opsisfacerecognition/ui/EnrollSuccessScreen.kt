package com.example.opsisfacerecognition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.ui.components.StatusAvatar
import com.example.opsisfacerecognition.core.ui.layout.AppScreenContainer
import com.example.opsisfacerecognition.navigation.Routes
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollSuccessScreen(
    navController: NavController,
    title: String,
    description: String,
    viewmodel: FaceRecognizerViewModel = hiltViewModel()

) {
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

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {

                    // Status avatar
                    StatusAvatar(
                        badgeIcon = Icons.Rounded.Verified,
                        badgeTint = MaterialTheme.colorScheme.surface,
                        badgeColor = MaterialTheme.colorScheme.primary
                    )
                }

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

                // Subtitle
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

                Spacer(Modifier.weight(1f))

                // Our action button
                Button(
                    modifier = Modifier.fillMaxWidth(),
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
