package com.example.opsisfacerecognition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.opsisfacerecognition.core.AppNavigation
import com.example.opsisfacerecognition.ui.layout.ProvideAppLayout
import com.example.opsisfacerecognition.ui.theme.OpsisFaceRecognitionTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpsisFaceRecognitionTheme {
                // 1. Υπολογίζουμε το μέγεθος της οθόνης
                val windowSizeClass = calculateWindowSizeClass(this)

                // 2. Ενεργοποιούμε το Responsive Layout μας
                ProvideAppLayout(windowSizeClass = windowSizeClass) {

                    // 3. Καλούμε το Navigation μέσα στο Layout
                    AppNavigation()
                }
            }
        }
    }
}
