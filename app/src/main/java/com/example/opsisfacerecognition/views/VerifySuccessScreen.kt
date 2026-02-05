package com.example.opsisfacerecognition.views

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.components.StatusAvatar
import com.example.opsisfacerecognition.core.layout.AppScreenContainer
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

    AppScreenContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Spacer(modifier = Modifier.weight(0.28f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
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
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = bodyFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // We display the error if there is a fullName Conflict
                if (!verifiedUser?.fullName.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Welcome, ${verifiedUser?.fullName}",
                        fontWeight = FontWeight.Medium,
                        fontFamily = displayFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.72f))

            // Action Button
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp),
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
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
