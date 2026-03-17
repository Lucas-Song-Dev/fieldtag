package com.fieldtag.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import com.fieldtag.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExportService @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val instrumentRepository: InstrumentRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PAGE_WIDTH = 595   // A4 points
        private const val PAGE_HEIGHT = 842  // A4 points
        private const val MARGIN = 40f
        private const val PHOTO_MAX_DIM = 1200
        private const val THUMB_MAX_DIM = 200
        private const val THUMB_CELL_SIZE = 180f
        private const val THUMB_COLS = 3
    }

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        isAntiAlias = true
    }
    private val subtitlePaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 14f
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        isAntiAlias = true
    }
    private val captionPaint = Paint().apply {
        color = Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }
    private val dividerPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
    }
    private val statusPaints = mapOf(
        FieldStatus.COMPLETE to Paint().apply { color = Color.parseColor("#2E7D32"); textSize = 11f; isAntiAlias = true },
        FieldStatus.IN_PROGRESS to Paint().apply { color = Color.parseColor("#F9A825"); textSize = 11f; isAntiAlias = true },
        FieldStatus.NOT_STARTED to Paint().apply { color = Color.GRAY; textSize = 11f; isAntiAlias = true },
        FieldStatus.CANNOT_LOCATE to Paint().apply { color = Color.RED; textSize = 11f; isAntiAlias = true },
    )

    suspend fun exportProject(projectId: String): File = withContext(Dispatchers.IO) {
        val project = projectRepository.getById(projectId) ?: error("Project not found")
        val instruments = instrumentRepository.getByProject(projectId)
        val allMedia = mediaRepository.getByProject(projectId)
        val ungrouped = allMedia.filter { it.instrumentId == null }

        val pdfDocument = PdfDocument()
        var pageNumber = 1

        // Cover page
        addCoverPage(pdfDocument, pageNumber++, project.name, instruments, allMedia)

        // Per-instrument pages
        for (instrument in instruments) {
            val instrumentMedia = allMedia.filter { it.instrumentId == instrument.id }
            addInstrumentPage(pdfDocument, pageNumber++, instrument, instrumentMedia)
        }

        // Ungrouped section
        if (ungrouped.isNotEmpty()) {
            addUngroupedPage(pdfDocument, pageNumber, ungrouped)
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(exportDir, "${project.name.replace(" ", "_")}_$dateStr.pdf")

        FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        outputFile
    }

    private fun addCoverPage(
        doc: PdfDocument,
        pageNum: Int,
        projectName: String,
        instruments: List<InstrumentEntity>,
        allMedia: List<MediaEntity>,
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        val canvas = page.canvas
        var y = MARGIN + 60f

        canvas.drawText("FieldTag Export Report", MARGIN, y, titlePaint); y += 40f
        canvas.drawText(projectName, MARGIN, y, subtitlePaint); y += 30f

        val dateStr = SimpleDateFormat("MMMM d, yyyy HH:mm", Locale.US).format(Date())
        canvas.drawText("Generated: $dateStr", MARGIN, y, captionPaint); y += 40f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint); y += 20f

        val total = instruments.size
        val complete = instruments.count { it.fieldStatus == FieldStatus.COMPLETE }
        val cannotLocate = instruments.count { it.fieldStatus == FieldStatus.CANNOT_LOCATE }
        val notStarted = instruments.count { it.fieldStatus == FieldStatus.NOT_STARTED }

        canvas.drawText("Total instruments: $total", MARGIN, y, bodyPaint); y += 20f
        canvas.drawText("Complete: $complete", MARGIN, y, statusPaints[FieldStatus.COMPLETE]!!); y += 20f
        canvas.drawText("Cannot locate: $cannotLocate", MARGIN, y, statusPaints[FieldStatus.CANNOT_LOCATE]!!); y += 20f
        canvas.drawText("Not started: $notStarted", MARGIN, y, statusPaints[FieldStatus.NOT_STARTED]!!); y += 20f
        canvas.drawText("Total photos/videos: ${allMedia.size}", MARGIN, y, bodyPaint)

        doc.finishPage(page)
    }

    private fun addInstrumentPage(
        doc: PdfDocument,
        pageNum: Int,
        instrument: InstrumentEntity,
        media: List<MediaEntity>,
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        val canvas = page.canvas
        var y = MARGIN + 30f

        canvas.drawText(instrument.tagId, MARGIN, y, titlePaint); y += 25f

        val typeLine = instrument.instrumentType ?: "Instrument"
        canvas.drawText(typeLine, MARGIN, y, subtitlePaint); y += 20f

        val statusLabel = when (instrument.fieldStatus) {
            FieldStatus.COMPLETE -> "COMPLETE"
            FieldStatus.IN_PROGRESS -> "IN PROGRESS"
            FieldStatus.CANNOT_LOCATE -> "CANNOT LOCATE"
            FieldStatus.NOT_STARTED -> "NOT STARTED"
        }
        canvas.drawText("Status: $statusLabel", MARGIN, y, statusPaints[instrument.fieldStatus]!!); y += 20f

        if (!instrument.notes.isNullOrBlank()) {
            canvas.drawText("Notes: ${instrument.notes}", MARGIN, y, bodyPaint); y += 20f
        }

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint); y += 20f

        // Photo grid
        if (media.isEmpty()) {
            canvas.drawText("No photos captured.", MARGIN, y, captionPaint)
        } else {
            var col = 0
            var rowY = y
            for (mediaItem in media) {
                val x = MARGIN + col * (THUMB_CELL_SIZE + 10f)
                val bitmap = loadScaledBitmap(mediaItem.thumbnailPath, THUMB_MAX_DIM)
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x, rowY, null)
                    canvas.drawText(mediaItem.role.name, x, rowY + THUMB_CELL_SIZE + 12f, captionPaint)
                    bitmap.recycle()
                }
                col++
                if (col >= THUMB_COLS) {
                    col = 0
                    rowY += THUMB_CELL_SIZE + 30f
                    if (rowY > PAGE_HEIGHT - MARGIN) break
                }
            }
        }

        doc.finishPage(page)
    }

    private fun addUngroupedPage(doc: PdfDocument, pageNum: Int, ungrouped: List<MediaEntity>) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        val canvas = page.canvas
        var y = MARGIN + 30f

        canvas.drawText("Ungrouped Media (${ungrouped.size} items)", MARGIN, y, titlePaint); y += 30f
        canvas.drawText("Media not assigned to any instrument:", MARGIN, y, captionPaint); y += 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint); y += 20f

        var col = 0
        for (mediaItem in ungrouped) {
            val x = MARGIN + col * (THUMB_CELL_SIZE + 10f)
            val bitmap = loadScaledBitmap(mediaItem.thumbnailPath, THUMB_MAX_DIM)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, x, y, null)
                bitmap.recycle()
            }
            col++
            if (col >= THUMB_COLS) {
                col = 0
                y += THUMB_CELL_SIZE + 20f
                if (y > PAGE_HEIGHT - MARGIN) break
            }
        }

        doc.finishPage(page)
    }

    private fun loadScaledBitmap(path: String, maxDim: Int): Bitmap? {
        if (path.isBlank()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val scale = maxOf(opts.outWidth, opts.outHeight) / maxDim
            opts.inSampleSize = if (scale > 1) scale else 1
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) { null }
    }
}
