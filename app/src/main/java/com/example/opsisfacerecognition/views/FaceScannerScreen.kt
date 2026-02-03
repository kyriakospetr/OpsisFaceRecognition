package com.example.opsisfacerecognition.views

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
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
import com.example.opsisfacerecognition.core.components.CameraPreviewOnly
import com.example.opsisfacerecognition.core.components.CameraPreviewWithAnalysis
import com.example.opsisfacerecognition.core.components.OvalOverlay
import com.example.opsisfacerecognition.core.config.FaceScannerConfig
import com.example.opsisfacerecognition.core.layout.AppScreenContainer
import com.example.opsisfacerecognition.core.states.FaceDetectionUiState
import com.example.opsisfacerecognition.viewmodel.FaceRecognizerViewModel
import com.google.mlkit.vision.face.Face
import androidx.compose.animation.AnimatedVisibility

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScannerScreen(
    navController: NavController,
    viewModel: FaceRecognizerViewModel = hiltViewModel()
) {

    // Ui state from our view model
    // It's used to determine if faces are successfully detected
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                    text = "Face Enrollment",
                    fontFamily = displayFontFamily,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Align your face with the guide. We’ll capture when it’s stable.",
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
                onFacesDetected = viewModel::onFacesDetected,
                onEnrollmentImagesCaptured = viewModel::onEnrollmentImagesCaptured,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FaceScannerCameraZone(
    uiState: FaceDetectionUiState,
    onFacesDetected: (List<Face>) -> Unit,
    onEnrollmentImagesCaptured: (List<Bitmap>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var ovalCenter by remember { mutableStateOf<Offset?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // Get the status by our uiState
    val status = getScannerStatus(uiState)

    // We use this variable to determine the border of our oval shape
    val isFaceDetected = uiState is FaceDetectionUiState.Success

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
                        onFacesDetected = onFacesDetected,
                        onEnrollmentImagesCaptured = onEnrollmentImagesCaptured
                    )
                }

                // If analyzer is initialized show the camera preview with the analyzer
                // Else show the camera preview only until our analyzer is set up
                if (faceAnalyzer != null) {
                    CameraPreviewWithAnalysis(Modifier.fillMaxSize(), analyzer = faceAnalyzer)
                } else {
                    CameraPreviewOnly(Modifier.fillMaxSize())
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

    val backgroundColor by animateColorAsState(targetBackgroundColor, label = "bgColor")
    val contentColor by animateColorAsState(targetContentColor, label = "txtColor")

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
private fun getScannerStatus(uiState: FaceDetectionUiState): ScannerStatus {
    return when (uiState) {
        FaceDetectionUiState.Idle -> ScannerStatus("Initializing camera...", MessageTone.Neutral)
        FaceDetectionUiState.Scanning -> ScannerStatus("Position your face inside the oval", MessageTone.Neutral)
        FaceDetectionUiState.MultipleFacesDetected -> ScannerStatus("Multiple faces detected. Use only one.", MessageTone.Error)
        FaceDetectionUiState.Success -> ScannerStatus("Face detected! Hold still...", MessageTone.Success)
        is FaceDetectionUiState.Error -> ScannerStatus(uiState.message, MessageTone.Error)
    }
}