package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun PhotoRepositionDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Transform states
    var userScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var rotationAngle by remember { mutableStateOf(0f) }

    // Viewport dimensions in px (recorded via onSizeChanged)
    var viewportWidthPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }

    // Load Bitmap asynchronously
    LaunchedEffect(imageUri) {
        isLoading = true
        loadError = null
        withContext(Dispatchers.IO) {
            val bmp = loadAndOrientBitmap(context, imageUri)
            withContext(Dispatchers.Main) {
                if (bmp != null) {
                    loadedBitmap = bmp
                    isLoading = false
                } else {
                    loadError = "Gagal memuat gambar yang dipilih."
                    isLoading = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Atur Posisi Foto",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Geser & cubit untuk menyesuaikan bingkai",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Crop Viewport
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E))
                        .pointerInput(loadedBitmap) {
                            if (loadedBitmap != null) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    userScale = (userScale * zoom).coerceIn(0.2f, 5.0f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Memproses foto...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    } else if (loadError != null) {
                        Text(
                            text = loadError ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (loadedBitmap != null) {
                        val bmp = loadedBitmap!!

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            viewportWidthPx = size.width
                            viewportHeightPx = size.height

                            val circleRadius = (min(size.width, size.height) / 2f) - 16.dp.toPx()
                            val circleCenter = Offset(size.width / 2f, size.height / 2f)

                            // Base scale to fit inside the circle
                            val bmpDim = if (rotationAngle % 180f == 0f) {
                                max(bmp.width, bmp.height).toFloat()
                            } else {
                                max(bmp.height, bmp.width).toFloat()
                            }
                            val baseScale = (circleRadius * 2f) / bmpDim
                            val effectiveScale = baseScale * userScale

                            // 1. Draw transformed bitmap
                            val drawMatrix = android.graphics.Matrix().apply {
                                postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                                postRotate(rotationAngle)
                                postScale(effectiveScale, effectiveScale)
                                postTranslate(circleCenter.x + offsetX, circleCenter.y + offsetY)
                            }

                            val paint = Paint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                                isDither = true
                            }

                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.concat(drawMatrix)
                            drawContext.canvas.nativeCanvas.drawBitmap(bmp, 0f, 0f, paint)
                            drawContext.canvas.nativeCanvas.restore()

                            // 2. Dimmed Mask Overlay with Circular Clear Hole (EvenOdd Path)
                            val maskPath = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addOval(
                                    Rect(
                                        circleCenter.x - circleRadius,
                                        circleCenter.y - circleRadius,
                                        circleCenter.x + circleRadius,
                                        circleCenter.y + circleRadius
                                    )
                                )
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(maskPath, color = Color.Black.copy(alpha = 0.68f))

                            // 3. Circular Guide Border
                            drawCircle(
                                color = Color.White,
                                radius = circleRadius,
                                center = circleCenter,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 4. Subtle Rule of Thirds Guide Grid
                            val gridColor = Color.White.copy(alpha = 0.25f)
                            val gridStroke = Stroke(width = 1.dp.toPx())
                            val oneThird = circleRadius * 2f / 3f
                            val startX = circleCenter.x - circleRadius
                            val startY = circleCenter.y - circleRadius

                            // Clip lines inside circle visually
                            drawLine(
                                color = gridColor,
                                start = Offset(startX + oneThird, circleCenter.y - circleRadius * 0.7f),
                                end = Offset(startX + oneThird, circleCenter.y + circleRadius * 0.7f),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(startX + oneThird * 2f, circleCenter.y - circleRadius * 0.7f),
                                end = Offset(startX + oneThird * 2f, circleCenter.y + circleRadius * 0.7f),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(circleCenter.x - circleRadius * 0.7f, startY + oneThird),
                                end = Offset(circleCenter.x + circleRadius * 0.7f, startY + oneThird),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(circleCenter.x - circleRadius * 0.7f, startY + oneThird * 2f),
                                end = Offset(circleCenter.x + circleRadius * 0.7f, startY + oneThird * 2f),
                                strokeWidth = gridStroke.width
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Zoom Control Slider & Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { userScale = (userScale - 0.15f).coerceIn(0.2f, 5.0f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Perkecil",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = userScale,
                        onValueChange = { userScale = it },
                        valueRange = 0.2f..4.0f,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { userScale = (userScale + 0.15f).coerceIn(0.2f, 5.0f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Perbesar",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Action Presets: Fit (Muat Semua), Fill (Penuhi), Putar, Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fit button (Muat Penuh tanpa terpotong)
                    OutlinedButton(
                        onClick = {
                            userScale = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Muat Utuh", fontSize = 11.sp, maxLines = 1)
                    }

                    // Fill button (Penuhi Lingkaran)
                    OutlinedButton(
                        onClick = {
                            val bmp = loadedBitmap
                            if (bmp != null) {
                                val minSide = min(bmp.width, bmp.height).toFloat()
                                val maxSide = max(bmp.width, bmp.height).toFloat()
                                userScale = (maxSide / minSide).coerceIn(1f, 4f)
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Penuhi", fontSize = 11.sp, maxLines = 1)
                    }

                    // Rotate button (Putar 90°)
                    OutlinedButton(
                        onClick = {
                            rotationAngle = (rotationAngle + 90f) % 360f
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Putar", fontSize = 11.sp, maxLines = 1)
                    }

                    // Reset button
                    IconButton(
                        onClick = {
                            userScale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            rotationAngle = 0f
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Confirmation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            val bmp = loadedBitmap
                            if (bmp != null && viewportWidthPx > 0 && viewportHeightPx > 0) {
                                val cropped = generateCroppedBitmap(
                                    bmp = bmp,
                                    userScale = userScale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotationAngle = rotationAngle,
                                    viewportW = viewportWidthPx,
                                    viewportH = viewportHeightPx
                                )
                                onConfirm(cropped)
                            }
                        },
                        enabled = loadedBitmap != null && !isLoading,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gunakan Foto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Loads a Bitmap from the given Uri, scaling down if too large and applying EXIF orientation.
 */
private fun loadAndOrientBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver

        // Decode bounds only first
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        // Scale down to max 1280px for performance & memory safety
        val maxSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (maxSide / (sampleSize * 2) >= 1280) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Check EXIF orientation
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }
                if (!matrix.isIdentity) {
                    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (transformed != bitmap) {
                        bitmap.recycle()
                        bitmap = transformed
                    }
                }
            }
        } catch (e: Exception) {
            // EXIF read failure is non-fatal
        }

        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Creates a high-resolution 512x512 Bitmap reflecting the user's zoom, translation, and rotation.
 */
private fun generateCroppedBitmap(
    bmp: Bitmap,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationAngle: Float,
    viewportW: Float,
    viewportH: Float,
    targetSize: Int = 512
): Bitmap {
    val circleRadius = (min(viewportW, viewportH) / 2f) - 16f * (viewportW / 280f)
    val bmpDim = if (rotationAngle % 180f == 0f) {
        max(bmp.width, bmp.height).toFloat()
    } else {
        max(bmp.height, bmp.width).toFloat()
    }
    val baseScale = (circleRadius * 2f) / bmpDim
    val effectiveScale = baseScale * userScale

    // Ratio between output target size (512) and the circular crop diameter
    val scaleToOutput = targetSize.toFloat() / (circleRadius * 2f)

    val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(outputBitmap)

    val matrix = android.graphics.Matrix().apply {
        // Center the bitmap at coordinate (0, 0)
        postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        // Rotate
        postRotate(rotationAngle)
        // Scale to match viewport scale, then scale up to target output resolution
        postScale(effectiveScale * scaleToOutput, effectiveScale * scaleToOutput)
        // Translate to the center of output bitmap (256, 256) plus scaled offset
        postTranslate(targetSize / 2f + offsetX * scaleToOutput, targetSize / 2f + offsetY * scaleToOutput)
    }

    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    canvas.drawBitmap(bmp, matrix, paint)
    return outputBitmap
}
