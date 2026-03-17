package com.fieldtag.ui.export

import android.content.Context
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import com.fieldtag.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a ZIP archive for a project.
 *
 * Output structure:
 *
 * ```
 * {ProjectName}_{timestamp}.zip
 * └── {ProjectName}/
 *     ├── {TagId}/
 *     │   ├── BEFORE/
 *     │   │   └── {TagId}_{yyyyMMdd_HHmmss}_{shortId}.jpg
 *     │   ├── AFTER/
 *     │   │   └── {TagId}_{yyyyMMdd_HHmmss}_{shortId}.jpg
 *     │   └── {OTHER_ROLE}/
 *     │       └── {TagId}_{yyyyMMdd_HHmmss}_{shortId}.jpg
 *     └── _ungrouped/
 *         └── photo_{yyyyMMdd_HHmmss}_{shortId}.jpg
 * ```
 *
 * Photos that no longer exist on disk are silently skipped (not an error).
 */
@Singleton
class ZipExportService @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val instrumentRepository: InstrumentRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) {

    suspend fun exportProject(projectId: String): File = withContext(Dispatchers.IO) {
        val project = projectRepository.getById(projectId)
            ?: error("Project $projectId not found")

        val instruments = instrumentRepository.getByProject(projectId)
            .sortedBy { it.tagId }
        val allMedia = mediaRepository.getByProject(projectId)

        val mediaByInstrumentId = allMedia.groupBy { it.instrumentId }
        val ungrouped = allMedia.filter { it.instrumentId == null }

        val safeName = sanitize(project.name)
        val stampStr = DateFmt.stamp()

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, "${safeName}_${stampStr}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->

            // ── Per-instrument folders ────────────────────────────────────
            for (instrument in instruments) {
                val instrumentMedia = mediaByInstrumentId[instrument.id] ?: continue
                val tagFolder = "$safeName/${sanitize(instrument.tagId)}"

                // Each role gets its own subfolder inside the tag folder
                val byRole = instrumentMedia.groupBy { it.role }
                for ((role, mediaList) in byRole) {
                    val roleFolder = "$tagFolder/${role.name}"
                    for (media in mediaList) {
                        val captureDate = DateFmt.file(media.capturedAt)
                        val shortId = media.id.take(6)
                        val entryName = "$roleFolder/${sanitize(instrument.tagId)}_${captureDate}_$shortId.jpg"
                        addFileToZip(zos, File(media.filePath), entryName, media.capturedAt)
                    }
                }
            }

            // ── Ungrouped media ────────────────────────────────────────────
            if (ungrouped.isNotEmpty()) {
                val ungroupedFolder = "$safeName/_ungrouped"
                for (media in ungrouped) {
                    val captureDate = DateFmt.file(media.capturedAt)
                    val shortId = media.id.take(6)
                    val entryName = "$ungroupedFolder/photo_${captureDate}_$shortId.jpg"
                    addFileToZip(zos, File(media.filePath), entryName, media.capturedAt)
                }
            }
        }

        zipFile
    }

    /**
     * Adds a single file to the ZIP stream. Silently skips missing / unreadable files so a
     * single deleted photo does not abort the entire export.
     */
    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryPath: String,
        timestamp: Long,
    ) {
        if (!file.exists() || !file.canRead()) return
        val entry = ZipEntry(entryPath).apply { time = timestamp }
        zos.putNextEntry(entry)
        try {
            file.inputStream().buffered().use { it.copyTo(zos) }
        } finally {
            zos.closeEntry()
        }
    }

    /**
     * Replaces characters that are illegal or awkward in ZIP entry paths / file names.
     * Keeps letters, digits, dots, dashes, and underscores.
     */
    internal fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "unnamed" }

    private object DateFmt {
        private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val file = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        fun stamp(): String = synchronized(stamp) { stamp.format(Date()) }
        fun file(ms: Long): String = synchronized(file) { file.format(Date(ms)) }
    }
}
