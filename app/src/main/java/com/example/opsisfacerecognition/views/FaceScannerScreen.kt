package com.example.opsisfacerecognition.views

import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.biometrics.startFaceDetection
import com.example.opsisfacerecognition.core.components.OvalOverlay
import com.example.opsisfacerecognition.core.config.FaceScannerConfig
import com.example.opsisfacerecognition.core.states.FaceUiState
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScannerScreen(
    navController: NavController,
    viewModel: FaceRecognizerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    var ovalCenter by remember { mutableStateOf<Offset?>(null) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFaceDetected = uiState is FaceUiState.Success

    // Different messages, colors for each state
    val (message, color) = when (uiState) {
        FaceUiState.Idle -> "Initializing camera..." to MaterialTheme.colorScheme.onSurface
        FaceUiState.Scanning -> "Position your face inside the oval" to MaterialTheme.colorScheme.onSurface
        FaceUiState.MultipleFacesDetected -> "Multiple faces detected. Use only one." to MaterialTheme.colorScheme.error
        FaceUiState.Success -> "Face detected! Hold still..." to MaterialTheme.colorScheme.primaryContainer
        is FaceUiState.Error -> (uiState as FaceUiState.Error).message to MaterialTheme.colorScheme.error
    }

    // We get the screen size so our scanner is responsive
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Face Enrollment",
                        fontFamily = displayFontFamily,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthDp = maxWidth
            val screenHeightDp = maxHeight

            val topBarHeight = innerPadding.calculateTopPadding()

            // Dynamic calculations based on the available UI space
            val ovalWidthDp = screenWidthDp * FaceScannerConfig.OVAL_WIDTH_PERCENT
            val ovalHeightDp = ovalWidthDp * FaceScannerConfig.OVAL_ASPECT_RATIO
            val topMarginDp = (screenHeightDp * FaceScannerConfig.TOP_MARGIN_PERCENT) + topBarHeight

            // Camera Controller state
            val cameraController = remember {
                LifecycleCameraController(context).apply {
                    cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                }
            }

            // Trigger detection once the center is calculated by the Overlay
            LaunchedEffect(ovalCenter) {
                ovalCenter?.let { center ->
                    startFaceDetection(
                        context = context,
                        density = density,
                        cameraController = cameraController,
                        lifecycleOwner = lifecycleOwner,
                        ovalCenter = center,
                        ovalWidthDp = ovalWidthDp,
                        ovalHeightDp = ovalHeightDp,
                        onFacesDetected = { faces ->
                            viewModel.onFacesDetected(faces)
                        }
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // Camera
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller = cameraController
                        }
                    }
                )
                OvalOverlay(
                    modifier = Modifier.fillMaxSize(),
                    isFaceDetected = isFaceDetected,
                    ovalWidth = ovalWidthDp,
                    ovalHeight = ovalHeightDp,
                    topMargin = topMarginDp,
                    onCenterCalculated = { center -> ovalCenter = center }
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topMarginDp + ovalHeightDp + 32.dp)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = message,
                        fontFamily = displayFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    // Progress bar its not connected to our facenet model
                    // Just a loading progress indicator
                    val showProgress = uiState is FaceUiState.Success
                    if (showProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}