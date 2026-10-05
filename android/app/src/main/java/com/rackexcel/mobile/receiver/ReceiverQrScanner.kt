package com.rackexcel.mobile.receiver

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Full-screen, offline QR scanner for the desktop receiver's pairing URI.
 * Callers handle the runtime camera permission before showing this dialog.
 */
@Composable
fun ReceiverQrScannerDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScanned by rememberUpdatedState(onScanned)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val delivered = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(FORMAT_QR_CODE)
                .build(),
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var startError by remember { mutableStateOf<String?>(null) }
    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(previewView, lifecycleOwner, hasCameraPermission) {
        val bindingActive = AtomicBoolean(true)
        val view = previewView
        val providerFuture = if (view != null && hasCameraPermission) {
            ProcessCameraProvider.getInstance(context)
        } else {
            null
        }

        if (!hasCameraPermission) {
            startError = "请先授予相机权限后再扫描二维码"
        } else if (view != null && providerFuture != null) {
            providerFuture.addListener(
                {
                    if (!bindingActive.get() || delivered.get()) return@addListener
                    runCatching {
                        val provider = providerFuture.get()
                        val targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                        val preview = Preview.Builder()
                            .setTargetRotation(targetRotation)
                            .build()
                            .also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(targetRotation)
                            .build()
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeQrFrame(
                                scanner = scanner,
                                imageProxy = imageProxy,
                                delivered = delivered,
                                onScanned = latestOnScanned,
                            )
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure {
                        startError = "相机启动未完成，请关闭后重新扫描"
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

        onDispose {
            bindingActive.set(false)
            if (providerFuture?.isDone == true) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    Dialog(
        onDismissRequest = { latestOnDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF071A29)),
        ) {
            if (startError == null) {
                AndroidView(
                    factory = { androidContext ->
                        PreviewView(androidContext).also {
                            it.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A2842)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = Color(0xFF8CD9CF),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = startError.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(onClick = { latestOnDismiss() }) {
                            Text("关闭")
                        }
                    }
                }
            }

            ScannerOverlay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 22.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { latestOnDismiss() }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭扫描",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "连接桌面接收器",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "扫描电脑端显示的配对二维码",
                        color = Color(0xFFC5D4DF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "请将二维码放入取景框内",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "配对信息仅在当前局域网内使用",
                    color = Color(0xFFC5D4DF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val frameHeight = 244.dp
    val trailHeight = 46.dp
    val framePx = with(LocalDensity.current) { frameHeight.toPx() }
    val motionEnabled = rememberSystemMotionEnabled()
    val sweep = if (motionEnabled) {
        val sweepTransition = rememberInfiniteTransition(label = "scanSweep")
        val animatedSweep by sweepTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                // Restart, not Reverse: a real scan line always travels the same direction, and
                // the gradient trail below would point the wrong way on a reversed pass.
                repeatMode = RepeatMode.Restart,
            ),
            label = "scanSweepOffset",
        )
        animatedSweep
    } else {
        // A stable middle guide remains visible when Android's animator duration scale is zero.
        0.5f
    }
    val sweepAlpha = if (motionEnabled) edgeFade(sweep) else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(frameHeight)
            .clip(RoundedCornerShape(22.dp))
            .border(2.dp, Color(0xFF73DDD1), RoundedCornerShape(22.dp)),
    ) {
        // Reading `sweep` inside the offset/graphicsLayer lambdas keeps the per-frame work in the
        // layout and draw phases; recomposing the overlay 60 times a second would fight the camera.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (framePx * sweep).roundToInt()) }
                .fillMaxWidth()
                .height(trailHeight)
                .graphicsLayer { alpha = sweepAlpha }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x8073DDD1), Color(0x2673DDD1), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (framePx * sweep).roundToInt()) }
                .fillMaxWidth()
                .height(if (motionEnabled) 2.dp else 3.dp)
                .graphicsLayer { alpha = sweepAlpha }
                .background(Color(0xFFA5F3EA)),
        )
    }
}

/** Mirrors Android's "Remove animations" setting without requiring a global UI dependency. */
@Composable
private fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

/** Fades the sweep in and out at the frame edges so the Restart wrap has no visible cut. */
private fun edgeFade(sweep: Float): Float = when {
    sweep < 0.12f -> sweep / 0.12f
    sweep > 0.88f -> (1f - sweep) / 0.12f
    else -> 1f
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
private fun analyzeQrFrame(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    delivered: AtomicBoolean,
    onScanned: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || delivered.get()) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val payload = barcodes
                .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue
                ?.trim()
                .orEmpty()
            if (payload.isNotEmpty() && delivered.compareAndSet(false, true)) {
                onScanned(payload)
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
