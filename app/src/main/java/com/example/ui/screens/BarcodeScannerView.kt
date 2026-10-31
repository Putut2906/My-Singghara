package com.example.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    title: String = "Pindai Barcode 1D",
    hintText: String = "Posisikan Barcode Garis Barang di Dalam Kotak",
    isQrSquare: Boolean = false
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (cameraPermissionState.status.isGranted) {
                BarcodeScannerContent(
                    onBarcodeScanned = onBarcodeScanned,
                    onClose = onDismissRequest,
                    title = title,
                    hintText = hintText,
                    isQrSquare = isQrSquare
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .background(Color.Black),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Izin Kamera Diperlukan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Aplikasi membutuhkan akses kamera untuk memindai barcode 1D produk kasir secara cepat dan otomatis.",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Izinkan Kamera")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismissRequest) {
                        Text("Batal", color = Color.White)
                    }
                }
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun BarcodeScannerContent(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    title: String = "Pindai Barcode 1D",
    hintText: String = "Posisikan Barcode Garis Barang di Dalam Kotak",
    isQrSquare: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Get ProcessCameraProvider once
    LaunchedEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProviderState.value = cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Bind camera when provider or lensFacing changes
    val cameraProvider = cameraProviderState.value
    LaunchedEffect(cameraProvider, lensFacing) {
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                    onBarcodeScanned(barcode)
                })

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                cameraControl = camera.cameraControl
                
                // Apply torch state to the new cameraControl
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    cameraControl?.enableTorch(isFlashOn)
                } else {
                    cameraControl?.enableTorch(false)
                }
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Camera binding failed", e)
            }
        }
    }

    // Separate effect to handle Torch toggle without rebinding the whole camera
    LaunchedEffect(isFlashOn, cameraControl, lensFacing) {
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            try {
                cameraControl?.enableTorch(isFlashOn)
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Failed to set torch", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { }
        )

        // Visual Viewfinder / Overlap
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Dimensions of scanning window
            val windowWidth = if (isQrSquare) (canvasWidth * 0.72f).coerceAtMost(280.dp.toPx()) else canvasWidth * 0.85f
            val windowHeight = if (isQrSquare) windowWidth else 160.dp.toPx()
            val left = (canvasWidth - windowWidth) / 2f
            val top = (canvasHeight - windowHeight) / 2.2f

            // Semi-transparent black background outside scanning window
            drawRect(
                color = Color.Black.copy(alpha = 0.65f),
                size = size
            )

            // Transparent rectangular scanning window
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(windowWidth, windowHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Viewfinder border (Green)
            drawRoundRect(
                color = Color(0xFF2E7D32),
                topLeft = Offset(left, top),
                size = Size(windowWidth, windowHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Red central linear laser scanner line
            val laserY = top + (windowHeight / 2f)
            drawLine(
                color = Color.Red,
                start = Offset(left + 16.dp.toPx(), laserY),
                end = Offset(left + windowWidth - 16.dp.toPx(), laserY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Overlay Instructions & Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Scanner",
                        tint = Color.White
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                // Placeholder for balance
                Box(modifier = Modifier.size(48.dp))
            }

            // Central scan window helper text
            Text(
                text = hintText,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Bottom toolbar controls: Torch and Camera Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Torch toggle (Only clickable/visible if we are on back camera)
                val showTorch = lensFacing == CameraSelector.LENS_FACING_BACK
                IconButton(
                    onClick = {
                        if (showTorch) {
                            isFlashOn = !isFlashOn
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (showTorch) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f), 
                            CircleShape
                        ),
                    enabled = showTorch
                ) {
                    Icon(
                        imageVector = if (isFlashOn && showTorch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (isFlashOn && showTorch) Color.Yellow else if (showTorch) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Camera direction toggle (front/back camera)
                IconButton(
                    onClick = {
                        val targetLensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                        
                        // Check if device supports target camera
                        val hasTargetCamera = try {
                            val selector = CameraSelector.Builder().requireLensFacing(targetLensFacing).build()
                            cameraProvider?.hasCamera(selector) == true
                        } catch (e: Exception) {
                            true // fallback to true to let them switch if we can't determine
                        }

                        if (hasTargetCamera) {
                            lensFacing = targetLensFacing
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                isFlashOn = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Balik Kamera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
class BarcodeAnalyzer(
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)
    private var isCooldown = false
    private var lastScannedTime: Long = 0

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val currentTime = System.currentTimeMillis()
            if (isCooldown && currentTime - lastScannedTime < 1800) { // 1.8s scan cooldown to avoid rapid fire scan
                imageProxy.close()
                return
            }
            isCooldown = false

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && rawValue.isNotEmpty()) {
                            isCooldown = true
                            lastScannedTime = currentTime
                            onBarcodeScanned(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("BarcodeAnalyzer", "Scan failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun KasirRemoteBarcodeScannerDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = LocalContext.current
    var lastScannedCode by remember { mutableStateOf("") }
    var scannedCount by remember { mutableIntStateOf(0) }
    var manualBarcode by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (cameraPermissionState.status.isGranted) {
                Box(modifier = Modifier.fillMaxSize()) {
                    BarcodeScannerContent(
                        onBarcodeScanned = { barcode ->
                            if (barcode.isNotBlank()) {
                                lastScannedCode = barcode
                                scannedCount++
                                onBarcodeScanned(barcode)
                                try {
                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator?.vibrate(70)
                                    }
                                } catch (e: Exception) {}
                            }
                        },
                        onClose = onDismissRequest
                    )

                    // TOP OVERLAY BADGE (STATUS KASIR PC)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF00A884),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "HP SCANNER KASIR PC (LIVE)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (scannedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E))
                            ) {
                                Text(
                                    text = "Terkirim: $scannedCount item ($lastScannedCode)",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // BOTTOM BAR: MANUAL INPUT & DONE
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualBarcode,
                                    onValueChange = { manualBarcode = it },
                                    placeholder = { Text("Ketik kode barcode manual...", color = Color.Gray, fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00A884),
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (manualBarcode.isNotBlank()) {
                                            lastScannedCode = manualBarcode.trim()
                                            scannedCount++
                                            onBarcodeScanned(manualBarcode.trim())
                                            manualBarcode = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Kirim")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onDismissRequest,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Selesai Memindai ($scannedCount Terkirim)")
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .background(Color.Black),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Izin Kamera Diperlukan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Aplikasi membutuhkan akses kamera untuk menggunakan smartphone sebagai alat scanner kasir PC nirkabel / USB debugging.",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                    ) {
                        Text("Izinkan Kamera")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismissRequest) {
                        Text("Batal", color = Color.White)
                    }
                }
            }
        }
    }
}
