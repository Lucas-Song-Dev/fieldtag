package com.fieldtag.domain.parser

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Parses a P&ID PDF using PdfBox Android to extract instrument tags.
 *
 * Pipeline:
 * 1. Open PDF with PdfBox
 * 2. Extract text with bounding boxes per page using PositionAwarePDFTextStripper
 * 3. Detect ISA tags in each text run via IsaTagDetector
 * 4. Deduplicate by tagId, keeping highest-confidence entry
 * 5. Sort by page then y position (top to bottom)
 * 6. Detect low-density pages (likely scanned/raster)
 * 7. Return ParseResult
 */
class PidParser {

    companion object {
        private const val LOW_DENSITY_THRESHOLD = 10 // tokens per page below this = likely scanned
        private const val INTER_TAG_DEDUP_RADIUS = 0.05f // normalised coordinate proximity for dedup
    }

    fun parse(inputStream: InputStream): ParseResult {
        val document = PDDocument.load(inputStream)
        return try {
            parseDocument(document)
        } finally {
            document.close()
        }
    }

    private fun parseDocument(document: PDDocument): ParseResult {
        val pageCount = document.numberOfPages
        val allTextRuns = mutableListOf<TextRun>()
        val warnings = mutableListOf<String>()
        val rawTextPages = JSONArray()

        for (pageIndex in 0 until pageCount) {
            val pageNumber = pageIndex + 1
            val stripper = PositionAwarePDFTextStripper()
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            stripper.getText(document) // trigger extraction

            val pageRuns = stripper.getTextRuns()
            val pageTokenCount = pageRuns.size

            if (pageTokenCount < LOW_DENSITY_THRESHOLD) {
                warnings.add("Page $pageNumber has only $pageTokenCount text tokens — may be a scanned/raster image without selectable text.")
            }

            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val cropBox = page.cropBox ?: mediaBox
            val rotation = page.rotation
            // Effective visual dimensions that PdfRenderer uses (swap if rotated 90/270)
            val effectiveWidth  = if (rotation == 90 || rotation == 270) cropBox.height else cropBox.width
            val effectiveHeight = if (rotation == 90 || rotation == 270) cropBox.width  else cropBox.height
            val originX = if (rotation == 90 || rotation == 270) cropBox.lowerLeftY else cropBox.lowerLeftX
            val originY = if (rotation == 90 || rotation == 270) cropBox.lowerLeftX else cropBox.lowerLeftY
            val pageWidth  = effectiveWidth
            val pageHeight = effectiveHeight

            Log.d("PidParser", "Page $pageNumber:" +
                " mediaBox=${mediaBox.width}x${mediaBox.height}" +
                " cropBox=${cropBox.width}x${cropBox.height}" +
                " lowerLeft=(${cropBox.lowerLeftX},${cropBox.lowerLeftY})" +
                " rotation=$rotation" +
                " effective=${effectiveWidth}x${effectiveHeight}" +
                " origin=($originX,$originY)"
            )

            // Log sample runs for coordinate debugging
            if (pageRuns.isNotEmpty()) {
                val xs = pageRuns.map { it.x }
                val ys = pageRuns.map { it.y }
                Log.d("PidParser", "  runs=${pageRuns.size}  xRange=[${xs.min()},${xs.max()}]  yRange=[${ys.min()},${ys.max()}]")
                pageRuns.filter { IsaTagDetector.detectInText(it.text, pageNumber).isNotEmpty() }
                    .take(3)
                    .forEach { run ->
                        val nx = if (pageWidth  > 0f) ((run.x - originX) / pageWidth ).coerceIn(0f, 1f) else 0f
                        val ny = if (pageHeight > 0f) (1f - (run.y - originY) / pageHeight).coerceIn(0f, 1f) else 0f
                        Log.d("PidParser", "  TAG '${run.text}' raw=(${run.x},${run.y}) norm=($nx,$ny)")
                    }
            }

            // Store raw text + effective page geometry for re-parsing
            val pageJson = JSONObject().apply {
                put("page", pageNumber)
                put("width", pageWidth)
                put("height", pageHeight)
                put("originX", originX)
                put("originY", originY)
                val runsArray = JSONArray()
                pageRuns.forEach { run ->
                    runsArray.put(JSONObject().apply {
                        put("text", run.text)
                        put("x", run.x)
                        put("y", run.y)
                    })
                }
                put("runs", runsArray)
            }
            rawTextPages.put(pageJson)

            pageRuns.forEach { run ->
                val detectedTags = IsaTagDetector.detectWithPosition(
                    text = run.text,
                    page = pageNumber,
                    x = run.x,
                    y = run.y,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    originX = originX,
                    originY = originY,
                )
                allTextRuns.addAll(
                    detectedTags.map { tag ->
                        TextRun(text = run.text, x = run.x, y = run.y, parsedTag = tag)
                    }
                )
            }
        }

        val allTags = allTextRuns.mapNotNull { it.parsedTag }
        val deduplicated = deduplicateTags(allTags)
        val sorted = deduplicated.sortedWith(compareBy({ it.page }, { it.y ?: Float.MAX_VALUE }))

        // ── Calibration log ───────────────────────────────────────────────────
        // Log all found tags grouped by page so we know which page/position to
        // navigate to for calibration tapping.
        Log.d("CALIB", "=== ALL PARSED TAGS (${sorted.size} total) ===")
        sorted.groupBy { it.page }.forEach { (page, tags) ->
            tags.forEach { t ->
                Log.d("CALIB", "  p${page} ${t.tagId}" +
                    " normX=${"%.4f".format(t.x ?: -1f)}" +
                    " normY=${"%.4f".format(t.y ?: -1f)}")
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        return ParseResult(
            tags = sorted,
            pageCount = pageCount,
            rawTextJson = rawTextPages.toString(),
            warnings = warnings,
        )
    }

    /**
     * Result of a [parseRegion] call.
     *
     * @param tags        Detected ISA tags (may be empty).
     * @param suggestions Nearby raw text strings, useful when [tags] is empty so the caller
     *                    can surface them to the user for manual tag-ID entry.
     */
    data class RegionResult(
        val tags: List<ParsedTag>,
        val suggestions: List<String>,
    )

    /**
     * Parses a rectangular region of a specific page using the pre-extracted [rawTextJson].
     *
     * This avoids re-running PdfBox: the raw text runs (with their PDF-space coordinates)
     * were stored during the initial [parse] call and are re-used here for fast, lightweight
     * region detection.
     *
     * @param rawTextJson  The JSON string stored in [PidDocumentEntity.rawTextJson].
     * @param pageIndex    0-based page index to search.
     * @param x1f          Left boundary of the selection rectangle, normalised to [0, 1].
     * @param y1f          Top boundary (screen-space: 0 = top of page).
     * @param x2f          Right boundary, normalised to [0, 1].
     * @param y2f          Bottom boundary, normalised to [0, 1].
     * @return             [RegionResult] with detected tags and (when empty) nearby text suggestions.
     */
    fun parseRegion(
        rawTextJson: String,
        pageIndex: Int,
        x1f: Float,
        y1f: Float,
        x2f: Float,
        y2f: Float,
    ): RegionResult {
        val minX = minOf(x1f, x2f)
        val maxX = maxOf(x1f, x2f)
        val minY = minOf(y1f, y2f)
        val maxY = maxOf(y1f, y2f)
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        Log.d("CALIB", "parseRegion page=${pageIndex + 1}" +
            " tapCenter=(${"%.4f".format(centerX)}, ${"%.4f".format(centerY)})" +
            " box=[(${"%.4f".format(minX)}, ${"%.4f".format(minY)})" +
            " → (${"%.4f".format(maxX)}, ${"%.4f".format(maxY)})]"
        )
        val targetPage = pageIndex + 1 // rawTextJson uses 1-based page numbers

        val pages = JSONArray(rawTextJson)
        for (i in 0 until pages.length()) {
            val pageObj = pages.getJSONObject(i)
            if (pageObj.getInt("page") != targetPage) continue

            val pageWidth  = pageObj.getDouble("width").toFloat()
            val pageHeight = pageObj.getDouble("height").toFloat()
            val originX    = pageObj.optDouble("originX", 0.0).toFloat()
            val originY    = pageObj.optDouble("originY", 0.0).toFloat()
            val runs = pageObj.getJSONArray("runs")
            val found = mutableListOf<ParsedTag>()

            data class RunDist(val text: String, val nx: Float, val ny: Float, val dist: Float)

            // Pre-compute normalized positions for all runs on this page
            val allRunDists = (0 until runs.length()).map { j ->
                val run = runs.getJSONObject(j)
                val rawX = run.getDouble("x").toFloat()
                val rawY = run.getDouble("y").toFloat()
                val nx = if (pageWidth  > 0f) ((rawX - originX) / pageWidth ).coerceIn(0f, 1f) else 0f
                val ny = if (pageHeight > 0f) (1f - (rawY - originY) / pageHeight).coerceIn(0f, 1f) else 0f
                val d = kotlin.math.hypot((nx - centerX).toDouble(), (ny - centerY).toDouble()).toFloat()
                RunDist(run.getString("text"), nx, ny, d)
            }

            for (rd in allRunDists) {
                if (rd.nx in minX..maxX && rd.ny in minY..maxY) {
                    val j = allRunDists.indexOf(rd)
                    val run = runs.getJSONObject(j)
                    val rawX = run.getDouble("x").toFloat()
                    val rawY = run.getDouble("y").toFloat()
                    val hits = IsaTagDetector.detectWithPosition(
                        text = rd.text,
                        page = targetPage,
                        x = rawX,
                        y = rawY,
                        pageWidth  = pageWidth,
                        pageHeight = pageHeight,
                        originX    = originX,
                        originY    = originY,
                    )
                    found.addAll(hits)
                }
            }

            val tags = deduplicateTags(found)

            // Always collect the 5 closest text runs for suggestions / debugging
            val closest = allRunDists.sortedBy { it.dist }.take(5)

            if (tags.isEmpty()) {
                Log.d("CALIB", "  No tag hit. 5 closest text runs to tap:")
                closest.forEach { r ->
                    Log.d("CALIB", "    dist=${"%.4f".format(r.dist)}" +
                        " at (${"%.4f".format(r.nx)}, ${"%.4f".format(r.ny)})" +
                        " text='${r.text.take(40)}'")
                }
            } else {
                tags.forEach { t ->
                    Log.d("CALIB", "  HIT: ${t.tagId}" +
                        " normX=${"%.4f".format(t.x ?: -1f)}" +
                        " normY=${"%.4f".format(t.y ?: -1f)}")
                }
            }

            return RegionResult(
                tags = tags,
                suggestions = closest.map { it.text }.filter { it.isNotBlank() },
            )
        }
        return RegionResult(tags = emptyList(), suggestions = emptyList())
    }

    /**
     * Deduplicates tags by tagId. When the same tagId appears multiple times
     * (e.g. both as a bubble label and a nearby annotation), keeps the entry
     * with the highest confidence score.
     */
    private fun deduplicateTags(tags: List<ParsedTag>): List<ParsedTag> {
        val byId = mutableMapOf<String, ParsedTag>()
        for (tag in tags) {
            val existing = byId[tag.tagId]
            if (existing == null || tag.confidence > existing.confidence) {
                byId[tag.tagId] = tag
            }
        }
        return byId.values.toList()
    }

    /** Internal data class for a raw text run from PdfBox */
    private data class TextRun(
        val text: String,
        val x: Float,
        val y: Float,
        val parsedTag: ParsedTag? = null,
    )

    /**
     * Custom PDFTextStripper that captures individual text positions
     * so we can associate bounding boxes with each text run.
     */
    private class PositionAwarePDFTextStripper : PDFTextStripper() {
        private val runs = mutableListOf<TextRun>()

        init {
            sortByPosition = true
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.isNotBlank()) {
                val firstPos = textPositions.firstOrNull()
                if (firstPos != null) {
                    // Use the horizontal center of the text run so the dot in DiagramViewerScreen
                    // is drawn on top of the tag text rather than at the left edge.
                    val lastPos = textPositions.last()
                    val textCenterX = (firstPos.xDirAdj + lastPos.xDirAdj + lastPos.widthDirAdj) / 2f
                    runs.add(
                        TextRun(
                            text = text.trim(),
                            x = textCenterX,
                            y = firstPos.yDirAdj,
                        )
                    )
                }
            }
            super.writeString(text, textPositions)
        }

        fun getTextRuns(): List<TextRun> = runs.toList()
    }
}
