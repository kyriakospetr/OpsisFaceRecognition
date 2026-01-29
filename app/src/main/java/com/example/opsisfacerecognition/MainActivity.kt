package com.example.opsisfacerecognition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.opsisfacerecognition.core.AppNavigation
import com.example.opsisfacerecognition.ui.theme.OpsisFaceRecognitionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpsisFaceRecognitionTheme {
                AppNavigation()
            }
        }
    }
}
