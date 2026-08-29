package io.github.augustinavicius.nutrition.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.augustinavicius.nutrition.R
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onFoodResolved: (foodId: Long) -> Unit,
    onNeedsFood: (barcode: String, name: String?, brand: String?) -> Unit,
    onClose: () -> Unit,
    captureOnly: Boolean = false,
    onBarcodeCaptured: (barcode: String) -> Unit = {},
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }

    // In capture mode the caller only wants the digits, so hand them straight back rather
    // than looking the product up. Guarded because the analyser can deliver another frame
    // before the screen is popped.
    var captured by remember { mutableStateOf(false) }
    val handleBarcode: (String) -> Unit = { barcode ->
        if (captureOnly) {
            if (!captured) {
                captured = true
                onBarcodeCaptured(barcode)
            }
        } else {
            viewModel.onBarcode(barcode)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        when (val current = state) {
            is ScanState.Resolved -> {
                viewModel.resume()
                onFoodResolved(current.foodId)
            }
            is ScanState.NeedsFood -> {
                viewModel.resume()
                onNeedsFood(current.barcode, current.name, current.brand)
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (captureOnly) "Scan the barcode" else "Scan a barcode") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                hasPermission -> CameraLayer(
                    onBarcode = handleBarcode,
                    enabled = state is ScanState.Scanning && !captured,
                    onCameraUnavailable = { cameraError = it },
                )

                permissionRequested -> PermissionDenied(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onOpenSettings = { context.openAppSettings() },
                )

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (hasPermission) {
                ScanOverlay(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = state,
                    hint = if (captureOnly) {
                        "Point the camera at the barcode to fill it in"
                    } else {
                        "Point the camera at a product barcode"
                    },
                    cameraError = cameraError,
                    onRetry = viewModel::resume,
                    onManualEntry = { showManualEntry = true },
                )
            }
        }
    }

    if (showManualEntry) {
        ManualBarcodeDialog(
            onDismiss = { showManualEntry = false },
            onSubmit = { code ->
                showManualEntry = false
                handleBarcode(code)
            },
        )
    }
}

@Composable
private fun CameraLayer(
    onBarcode: (String) -> Unit,
    enabled: Boolean,
    onCameraUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_ITF,
                )
                .build()
        )
    }

    // The analyzer is built once and outlives recomposition, so it reads the latest `enabled`
    // and callback through snapshot state rather than capturing the first values it saw.
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val analyzer = remember { BarcodeAnalyzer(scanner) { if (currentEnabled) currentOnBarcode(it) } }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(enabled) { if (enabled) analyzer.reset() }

    LaunchedEffect(Unit) {
        val provider = runCatching { context.awaitCameraProvider() }.getOrElse {
            onCameraUnavailable("The camera could not be started on this device.")
            return@LaunchedEffect
        }

        // Barcodes are on packaging held away from you, so the rear lens is the right one —
        // but fall back rather than showing a black screen on a front-camera-only device.
        val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
            .firstOrNull { runCatching { provider.hasCamera(it) }.getOrDefault(false) }
        if (selector == null) {
            onCameraUnavailable("This device has no camera the scanner can use.")
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(executor, analyzer) }

        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        }.onFailure {
            onCameraUnavailable("The camera could not be started: ${it.message ?: "unknown error"}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.let { boundCamera ->
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    scope.launch { boundCamera.cameraControl.enableTorch(torchOn) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) {
                Icon(
                    painter = painterResource(
                        if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                    ),
                    contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ScanOverlay(
    modifier: Modifier,
    state: ScanState,
    hint: String,
    cameraError: String?,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1.7f)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
        )

        Spacer(Modifier.height(24.dp))

        when {
            cameraError != null -> Text(
                text = "$cameraError\nYou can still type the barcode in.",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            state is ScanState.Scanning -> Text(
                text = hint,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )

            state is ScanState.Looking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Text("Looking up ${state.barcode}…", color = Color.White)
            }

            state is ScanState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Button(onClick = onRetry) { Text("Try again") }
            }

            else -> Unit
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onManualEntry) {
            Text("Enter the number instead", color = Color.White)
        }
    }
}

@Composable
private fun PermissionDenied(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera access is off",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Scanning barcodes needs the camera. You can still search for foods or add " +
                "them by hand without it.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}

@Composable
private fun ManualBarcodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    val valid = value.length >= 8 && value.all(Char::isDigit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter barcode") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter(Char::isDigit).take(14) },
                label = { Text("Barcode digits") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = valid) { Text("Look up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
