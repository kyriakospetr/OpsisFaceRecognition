package com.example.opsisfacerecognition.views

import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.biometrics.startFaceDetection
import com.example.opsisfacerecognition.core.components.OvalOverlay
import com.example.opsisfacerecognition.core.config.FaceScannerConfig
import com.example.opsisfacerecognition.core.layout.AppScreenContainer
import com.example.opsisfacerecognition.core.states.FaceDetectionUiState
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

    // State variables to track the UI layout for face detection logic
    var ovalCenter by remember { mutableStateOf<Offset?>(null) }

    // We need to extract the size of our preview to pass to our face detector
    // To determine if the face is inside the oval
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // To stop the analyzer getting set up at every recomposition
    var analyzerSet by remember { mutableStateOf(false) }

    // State from the viewmodel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // It's used to mark the outline of our oval
    val isFaceDetected = uiState is FaceDetectionUiState.Success

    // Determining the message and tone based on the face detection state
    val (message, messageTone) = when (uiState) {
        FaceDetectionUiState.Idle -> "Initializing camera..." to MessageTone.Neutral
        FaceDetectionUiState.Scanning -> "Position your face inside the oval" to MessageTone.Neutral
        FaceDetectionUiState.MultipleFacesDetected -> "Multiple faces detected. Use only one." to MessageTone.Error
        FaceDetectionUiState.Success -> "Face detected! Hold still..." to MessageTone.Success
        is FaceDetectionUiState.Error -> (uiState as FaceDetectionUiState.Error).message to MessageTone.Error
    }

    // Camera Controller State
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        // BoxWithConstraints allows us to calculate oval size based on the available screen space
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val screenWidthDp = maxWidth
            val screenHeightDp = maxHeight

            // Calculating the guide oval dimensions
            val ovalWidthDp = screenWidthDp * FaceScannerConfig.OVAL_WIDTH_PERCENT
            val ovalHeightDp = ovalWidthDp * FaceScannerConfig.OVAL_ASPECT_RATIO
            val topMarginDp = (screenHeightDp * FaceScannerConfig.TOP_MARGIN_PERCENT)

            // Start analyzer once we have oval center and preview size (UI READY)
            LaunchedEffect(ovalCenter, previewSize) {
                val center = ovalCenter ?: return@LaunchedEffect
                if (previewSize.width == 0 || previewSize.height == 0) return@LaunchedEffect
                if (analyzerSet) return@LaunchedEffect

                // Start our face detector
                startFaceDetection(
                    context = context,
                    density = density,
                    cameraController = cameraController,
                    lifecycleOwner = lifecycleOwner,
                    ovalCenter = center,
                    ovalWidthDp = ovalWidthDp,
                    ovalHeightDp = ovalHeightDp,
                    previewWidthPx = previewSize.width.toFloat(),
                    previewHeightPx = previewSize.height.toFloat(),
                    onFacesDetected = { faces -> viewModel.onFacesDetected(faces) }
                )
                analyzerSet = true
            }

            AppScreenContainer(
                modifier = Modifier.fillMaxSize()
            ) {
                // Title
                Text(
                    text = "Face Enrollment",
                    fontFamily = displayFontFamily,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Align your face with the guide. We’ll capture when it’s stable.",
                    fontFamily = bodyFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(0.7f)
                )

                Spacer(Modifier.height(16.dp))

                // Camera
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { previewSize = it },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller = cameraController
                                }
                            }
                        )

                        // Our Oval
                        OvalOverlay(
                            modifier = Modifier.fillMaxSize(),
                            isFaceDetected = isFaceDetected,
                            ovalWidth = ovalWidthDp,
                            ovalHeight = ovalHeightDp,
                            topMargin = topMarginDp,
                            onCenterCalculated = { center -> ovalCenter = center }
                        )

                        // Message status
                        StatusBanner(
                            text = message,
                            tone = messageTone,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .padding(horizontal = 16.dp)
                        )

                        // Progress Bar
                        // It's not connected to a real progress
                        if (uiState is FaceDetectionUiState.Success) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .fillMaxWidth(0.7f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class MessageTone { Neutral, Success, Error }

@Composable
private fun StatusBanner(
    text: String,
    tone: MessageTone,
    modifier: Modifier = Modifier
) {
    // Display status message with different icons, text and colors
    val background = when (tone) {
        MessageTone.Neutral -> MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
        MessageTone.Success -> MaterialTheme.colorScheme.primaryContainer
        MessageTone.Error -> MaterialTheme.colorScheme.errorContainer
    }

    val foreground = when (tone) {
        MessageTone.Neutral -> MaterialTheme.colorScheme.onSurface
        MessageTone.Success -> MaterialTheme.colorScheme.surfaceContainer
        MessageTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    val icon = when (tone) {
        MessageTone.Neutral -> Icons.Outlined.Info
        MessageTone.Success -> Icons.Outlined.CheckCircle
        MessageTone.Error -> Icons.Outlined.ErrorOutline
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = background,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontFamily = bodyFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = foreground
            )
        }
    }
}
