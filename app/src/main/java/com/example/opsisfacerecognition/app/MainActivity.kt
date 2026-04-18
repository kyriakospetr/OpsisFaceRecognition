package com.example.opsisfacerecognition.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.opsisfacerecognition.app.ui.theme.OpsisFaceRecognitionTheme
import com.example.opsisfacerecognition.app.ui.layout.ProvideAppLayout
import com.example.opsisfacerecognition.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
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
