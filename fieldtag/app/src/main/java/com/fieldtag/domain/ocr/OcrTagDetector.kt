package com.fieldtag.domain.ocr

import android.graphics.Bitmap
import android.util.Log
import com.fieldtag.domain.parser.IsaTagDetector
import com.fieldtag.domain.parser.ParsedTag
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Result of a single OCR scan.
 *
 * @param tags      ISA 5.1 tags auto-detected from [rawLines].
 * @param rawLines  Every non-blank text line recognized by ML Kit, in document order.
 *                  The UI shows these as selectable options so the user can pick a line
 *                  that our ISA filter missed (e.g. "V-62").
 */
data class OcrResult(
    val tags: List<ParsedTag>,
    val rawLines: List<String>,
)

/**
 * On-device OCR wrapper using ML Kit Text Recognition (Latin script).
 *
 * Given a [Bitmap] crop (already rendered at 2× for clarity) and a 1-based [page] number,
 * runs OCR and then feeds every recognized text line through [IsaTagDetector] to find
 * ISA 5.1 instrument tags.  The raw recognized lines are also returned so the UI can let
 * the user pick any line — even ones the ISA filter considers non-standard (e.g. "V-62").
 */
@Singleton
class OcrTagDetector @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs ML Kit OCR on [bitmap] and returns both the parsed ISA tags and every raw
     * recognized line.
     *
     * @param bitmap  The image patch to scan (cropped from the rendered PDF page).
     * @param page    1-based page number — embedded in each returned [ParsedTag].
     */
    suspend fun detect(bitmap: Bitmap, page: Int): OcrResult =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0 /* no extra rotation — bitmap already upright */)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val allLines = visionText.textBlocks
                        .flatMap { block -> block.lines.map { it.text } }
                        .filter { it.isNotBlank() }
                    Log.d("OcrTagDetector", "page=$page raw text: \"${fullText.replace("\n", "|")}\"")
                    Log.d("OcrTagDetector", "page=$page OCR lines: $allLines")
                    // Also try the full concatenated text in case an ISA tag spans multiple lines
                    val candidates = allLines + listOf(fullText.replace("\n", " "))
                    val tags = candidates
                        .flatMap { line -> IsaTagDetector.detectInText(line, page) }
                        .distinctBy { it.tagId }
                    Log.d("OcrTagDetector", "page=$page found tags: ${tags.map { it.tagId }}")
                    cont.resume(OcrResult(tags = tags, rawLines = allLines))
                }
                .addOnFailureListener { e ->
                    Log.e("OcrTagDetector", "ML Kit OCR failed: ${e.message}")
                    cont.resume(OcrResult(tags = emptyList(), rawLines = emptyList()))
                }
        }
}
