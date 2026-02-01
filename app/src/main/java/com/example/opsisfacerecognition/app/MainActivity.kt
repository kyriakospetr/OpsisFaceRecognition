package com.example.opsisfacerecognition.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.opsisfacerecognition.app.ui.theme.OpsisFaceRecognitionTheme
import com.example.opsisfacerecognition.core.layout.ProvideAppLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpsisFaceRecognitionTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                ProvideAppLayout(windowSizeClass = windowSizeClass) {
                    AppNavigation()
                }
            }
        }
    }
}