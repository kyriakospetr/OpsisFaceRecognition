package com.example.opsisfacerecognition.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.opsisfacerecognition.app.ui.theme.bodyFontFamily
import com.example.opsisfacerecognition.app.ui.theme.displayFontFamily
import com.example.opsisfacerecognition.core.biometrics.FaceAnalyzer
import com.example.opsisfacerecognition.core.ui.components.CameraPreviewOnly
import com.example.opsisfacerecognition.core.ui.components.CameraPreviewWithAnalysis
import com.example.opsisfacerecognition.core.ui.components.OvalOverlay
import com.example.opsisfacerecognition.core.config.FaceScannerConfig
import com.example.opsisfacerecognition.core.ui.layout.AppScreenContainer
import com.example.opsisfacerecognition.core.states.FaceFlowMode
import com.example.opsisfacerecognition.core.states.FaceUiState
import com.example.opsisfacerecognition.navigation.Routes
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import com.example.opsisfacerecognition.core.states.FaceUiState.Detection

private const val STATUS_ANIMATION_MS = 220

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    navController: NavController,
    title: String,
    subTitle: String,
    popUpToRoute: String,
    backRoute: String,
    mode: FaceFlowMode,
    viewModel: FaceRecognizerViewModel = hiltViewModel()
) {

    // Ui state from our view model
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val handleBack = {
        val popped = navController.popBackStack(backRoute, false)
        if (!popped) {
            navController.navigate(backRoute) {
                launchSingleTop = true
            }
        }
    }

    // Based on the mode, we navigate by FaceUiState
    LaunchedEffect(uiState) {
        when (mode) {
            FaceFlowMode.ENROLL -> {
                if (uiState is FaceUiState.Enroll.CaptureProcessed) {
                    navController.navigate(Routes.ENROLL_PROCESSED) {
                        popUpTo(popUpToRoute)
                        launchSingleTop = true
                    }
                } else if (uiState is FaceUiState.Error) {
                    navController.navigate(Routes.ENROLL_FAILED) {
                        popUpTo(popUpToRoute)
                        launchSingleTop = true
                    }
                }
            }
            FaceFlowMode.VERIFY -> {
                when (uiState) {
                    is FaceUiState.Verify.Verified -> {
                        navController.navigate(Routes.VERIFY_SUCCESS) {
                            popUpTo(popUpToRoute)
                            launchSingleTop = true
                        }
                    }

                    is FaceUiState.Verify.VerificationFailed -> {
                        navController.navigate(Routes.VERIFY_FAILED) {
                            popUpTo(popUpToRoute)
                            launchSingleTop = true
                        }
                    }

                    is FaceUiState.Error -> {
                        navController.navigate(Routes.VERIFY_FAILED) {
                            popUpTo(popUpToRoute)
                            launchSingleTop = true
                        }
                    }

                    else -> {}
                }
            }
        }
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        AppScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Section
            Column {
                Text(
                    text = title,
                    fontFamily = displayFontFamily,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subTitle,
                    fontFamily = bodyFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(0.7f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Camera Section
            FaceScannerCameraZone(
                uiState = uiState,
                onDetectionFeedback = { feedback -> viewModel.onDetectionFeedback(feedback) },
                onImagesCaptured = { bitmaps -> viewModel.onImagesCaptured(bitmaps, mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FaceScannerCameraZone(
    uiState: FaceUiState,
    onDetectionFeedback: (Detection) -> Unit,
    onImagesCaptured: (List<Bitmap>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var ovalCenter by remember { mutableStateOf<Offset?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // Get the status by our uiState
    val status = getScannerStatus(uiState)

    // We use this variable to determine the border of our oval shape
    val isFaceDetected = uiState is Detection.FaceDetected || uiState is Detection.HoldStill || uiState is Detection.PerformLiveness
    val isProcessing =
        uiState is FaceUiState.Loading ||
        uiState is FaceUiState.Enroll.CaptureProcessed ||
        uiState is FaceUiState.Verify.Verified ||
        uiState is FaceUiState.Verify.VerificationFailed
    val fadeAlpha by animateFloatAsState(
        targetValue = if (isProcessing) 0.65f else 0f,
        animationSpec = tween(durationMillis = STATUS_ANIMATION_MS),
        label = "cameraFade"
    )

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // So we can get the screen's max width, height
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidthDp = maxWidth
            val screenHeightDp = maxHeight

            // We make the shape of our oval based on our screen and a fixed variable
            // How much % of the screen will the oval take and the margin from top
            // So its responsive for all devices
            val ovalWidthDp = screenWidthDp * FaceScannerConfig.OVAL_WIDTH_PERCENT
            val ovalHeightDp = ovalWidthDp * FaceScannerConfig.OVAL_ASPECT_RATIO
            val topMarginDp = screenHeightDp * FaceScannerConfig.TOP_MARGIN_PERCENT

            // We calculate the oval bottom so our status banner and progress bar can go below
            val ovalBottomDp = topMarginDp + ovalHeightDp

            // We need to convert our dp to pixels for our analyzer
            val ovalWidthPx = with(density) { ovalWidthDp.toPx() }
            val ovalHeightPx = with(density) { ovalHeightDp.toPx() }

            // Rendering
            Box(Modifier
                .fillMaxSize()
                .onSizeChanged { previewSize = it }
            ) {
                // Analyzer
                val faceAnalyzer = remember(ovalCenter, previewSize, ovalWidthPx, ovalHeightPx) {
                    val center = ovalCenter ?: return@remember null
                    if (previewSize.width == 0 || previewSize.height == 0) return@remember null

                    FaceAnalyzer(
                        ovalCenter = center,
                        ovalRadiusX = ovalWidthPx / 2f,
                        ovalRadiusY = ovalHeightPx / 2f,
                        screenWidth = previewSize.width.toFloat(),
                        screenHeight = previewSize.height.toFloat(),
                        onDetectionFeedback = onDetectionFeedback,
                        onImagesCaptured = onImagesCaptured
                    )
                }

                // If analyzer is initialized show the camera preview with the analyzer
                // Else show the camera preview only until our analyzer is set up
                if (faceAnalyzer != null) {
                    CameraPreviewWithAnalysis(Modifier.fillMaxSize(), analyzer = faceAnalyzer)
                } else {
                    CameraPreviewOnly(Modifier.fillMaxSize())
                }

                // Fade overlay during processing
                if (fadeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = fadeAlpha))
                    )
                }

                // Our oval
                OvalOverlay(
                    modifier = Modifier.fillMaxSize(),
                    isFaceDetected = isFaceDetected,
                    ovalWidth = ovalWidthDp,
                    ovalHeight = ovalHeightDp,
                    topMargin = topMarginDp,
                    onCenterCalculated = { ovalCenter = it }
                )

                // Our Status Banner and the ScanningProgressBar
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ovalBottomDp)
                        .padding(top = 32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    StatusBanner(
                        text = status.message,
                        tone = status.tone
                    )

                    AnimatedVisibility(
                        visible = isFaceDetected
                    ) {
                        ScanningProgressBar()
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningProgressBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(0.7f)
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    )
}

@Composable
fun StatusBanner(
    text: String,
    tone: MessageTone,
    modifier: Modifier = Modifier
) {
    // We determine the background, content, icon based on MessageTone
    val targetBackgroundColor = when (tone) {
        MessageTone.Neutral -> MaterialTheme.colorScheme.surface
        MessageTone.Success -> MaterialTheme.colorScheme.primaryContainer
        MessageTone.Error -> MaterialTheme.colorScheme.errorContainer
    }

    val targetContentColor = when (tone) {
        MessageTone.Neutral -> MaterialTheme.colorScheme.onSurface
        MessageTone.Success -> MaterialTheme.colorScheme.onPrimary
        MessageTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = STATUS_ANIMATION_MS),
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = STATUS_ANIMATION_MS),
        label = "txtColor"
    )

    val icon = when (tone) {
        MessageTone.Neutral -> Icons.Outlined.Info
        MessageTone.Success -> Icons.Outlined.CheckCircle
        MessageTone.Error -> Icons.Outlined.Warning
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// UI Helpers
data class ScannerStatus(val message: String, val tone: MessageTone)
enum class MessageTone {
    Neutral, Success, Error
}
private fun getScannerStatus(uiState: FaceUiState): ScannerStatus =
    when (uiState) {
        FaceUiState.Idle -> ScannerStatus("Initializing camera...", MessageTone.Neutral)
        FaceUiState.Loading -> ScannerStatus("Processing...", MessageTone.Neutral)
        Detection.FaceDetected -> ScannerStatus("Face detected! Hold still...", MessageTone.Success)
        Detection.NoFace -> ScannerStatus("Position your face inside the oval.", MessageTone.Neutral)
        Detection.MultipleFaces -> ScannerStatus("Multiple faces detected. Use only one.", MessageTone.Error)
        Detection.CenterFace -> ScannerStatus("Center your face inside the oval.", MessageTone.Neutral)
        Detection.LookStraight -> ScannerStatus("Look straight at the camera.", MessageTone.Neutral)
        Detection.MoveCloser -> ScannerStatus("Move a bit closer to the camera.", MessageTone.Neutral)
        Detection.HoldStill -> ScannerStatus("Hold still.", MessageTone.Neutral)
        Detection.ImproveFocus -> ScannerStatus("Image is blurry. Improve focus and try again.", MessageTone.Error)
        Detection.PerformLiveness -> ScannerStatus("Liveness check: blink naturally.", MessageTone.Neutral)
        Detection.LivenessFailed -> ScannerStatus("Liveness check failed. Please try again.", MessageTone.Error)
        FaceUiState.Enroll.CaptureProcessed -> ScannerStatus("Capture complete. Preparing enrollment...", MessageTone.Neutral)
        FaceUiState.Enroll.FullNameConflict -> ScannerStatus("This name is already in use.", MessageTone.Error)
        FaceUiState.Enroll.Completed -> ScannerStatus("Enrollment completed.", MessageTone.Success)
        is FaceUiState.Verify.Verified -> ScannerStatus("Verified", MessageTone.Success)
        FaceUiState.Verify.VerificationFailed -> ScannerStatus("Not verified", MessageTone.Error)
        is FaceUiState.Error -> ScannerStatus(uiState.message, MessageTone.Error)
    }
