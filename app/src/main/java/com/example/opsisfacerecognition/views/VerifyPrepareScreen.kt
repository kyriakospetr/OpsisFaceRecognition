package com.example.opsisfacerecognition.views

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.opsisfacerecognition.R
import com.example.opsisfacerecognition.ui.components.FacePrepContent

@Composable
fun VerifyIdentityPrepScreen(navController: NavController) {
    FacePrepContent(
        title = "Verify Your Identity",
        subtitle = "Please ensure your face is clearly visible for the validation process.",
        illustrationRes = R.drawable.face_verify,
        buttonText = "START VERIFICATION",
        tip = "To verify your identity, you must first complete your initial face enrollment.",
        onPrimaryClick = { TODO("NAVIGATE TO VERIFY SCAN") },
        onBack = {navController.popBackStack()}
    )
}