package com.example.opsisfacerecognition.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.opsisfacerecognition.core.states.FaceUiState
import com.example.opsisfacerecognition.navigation.Routes
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollScreen(
    navController: NavController,
    title: String,
    description: String,
    viewmodel: FaceRecognizerViewModel = hiltViewModel()
) {
    // Our state from the viewmodel
    val uiState by viewmodel.uiState.collectAsStateWithLifecycle()

    // FullName input
    var fullName by remember { mutableStateOf("") }

    // If the input is touched
    // We use this so we don't display the required field
    // If the client hasn't touched it yet
    var fullNameTouched by remember { mutableStateOf(false) }

    // Some Checks
    val fullNameError by remember(fullName, fullNameTouched) {
        derivedStateOf {
            if (!fullNameTouched) return@derivedStateOf null
            when {
                fullName.isBlank() -> "Full Name is required"
                fullName.length < 3 -> "Full Name must be at least 3 characters"
                else -> null
            }
        }
    }

    // When state completed go to selected graph
    LaunchedEffect(uiState) {
        if (uiState is FaceUiState.Enroll.Completed) {
            navController.navigate(Routes.ENROLL_SUCCESS) {
                popUpTo(Routes.ENROLL_GRAPH)
                launchSingleTop = true
            }
        }
    }

    // Needed to enable-disable our button
    val isFormValid =
        fullName.isNotBlank() &&
                fullNameError == null &&
                uiState !is FaceUiState.Loading

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

                Spacer(modifier = Modifier.weight(0.28f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    // Avatar
                    StatusAvatar(
                        badgeIcon = Icons.Rounded.Edit,
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

                    Spacer(Modifier.height(26.dp))

                    // Input field
                    OutlinedTextField(
                        value = fullName,
                        // On value changed, we update the state
                        // Also we update the touched state to true because he touched/typed in the input
                        onValueChange = {
                            fullName = it
                            fullNameTouched = true
                        },
                        label = { Text("Full name", fontFamily = bodyFontFamily) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (fullNameError != null)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        // Supporting text is only for errors, so we check if there is an error
                        // If there is we display the error message in the supporting text
                        isError = fullNameError != null,
                        supportingText = {
                            fullNameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // We display the error if there is a fullName Conflict
                if (uiState is FaceUiState.Enroll.FullNameConflict) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "That name is already in use. Please choose a different one.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    enabled = isFormValid,
                    onClick = { viewmodel.enrollUser(fullName) }
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
}
