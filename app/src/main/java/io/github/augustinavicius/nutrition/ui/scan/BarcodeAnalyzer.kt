package io.github.augustinavicius.nutrition.ui.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Feeds camera frames to ML Kit and reports the first product barcode it sees.
 *
 * Only product symbologies are requested — a QR code on the packaging should not be mistaken
 * for the product's own barcode — and results are debounced by value so a barcode held in
 * frame across many frames fires the callback once.
 */
class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastReported: String? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.usableValue() }?.let { value ->
                    if (value != lastReported) {
                        lastReported = value
                        onBarcode(value)
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        lastReported = null
    }

    private fun Barcode.usableValue(): String? =
        rawValue?.trim()?.takeIf { it.length >= MIN_BARCODE_LENGTH && it.all(Char::isDigit) }

    private companion object {
        /** EAN-8 is the shortest product code the app accepts. */
        const val MIN_BARCODE_LENGTH = 8
    }
}
