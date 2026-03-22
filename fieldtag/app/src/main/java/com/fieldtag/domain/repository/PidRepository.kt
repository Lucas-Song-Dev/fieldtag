package com.fieldtag.domain.repository

import android.content.Context
import android.net.Uri
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.dao.PidDocumentDao
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.OverlayShape
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.domain.parser.IsaTagDetector
import com.fieldtag.domain.parser.PidParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PidRepository @Inject constructor(
    private val pidDocumentDao: PidDocumentDao,
    private val instrumentDao: InstrumentDao,
    private val pidParser: PidParser,
    @ApplicationContext private val context: Context,
) {
    fun observeByProject(projectId: String): Flow<List<PidDocumentEntity>> =
        pidDocumentDao.observeByProject(projectId)

    fun observeById(id: String): Flow<PidDocumentEntity?> = pidDocumentDao.observeById(id)

    suspend fun getByProject(projectId: String): List<PidDocumentEntity> =
        pidDocumentDao.getByProject(projectId)

    suspend fun getById(id: String): PidDocumentEntity? = pidDocumentDao.getById(id)

    /**
     * Imports a P&ID PDF from a SAF Uri into internal storage, creates the PidDocument record,
     * and begins parsing asynchronously. Returns the PidDocumentEntity immediately so the caller
     * can observe progress via [observeById].
     */
    suspend fun importPdf(projectId: String, uri: Uri, fileName: String): PidDocumentEntity =
        withContext(Dispatchers.IO) {
            val docId = UUID.randomUUID().toString()
            val pdfDir = File(context.filesDir, "pids/$projectId")
            pdfDir.mkdirs()
            val destFile = File(pdfDir, "$docId.pdf")

            // Copy from SAF Uri to internal storage
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            } ?: error("Cannot open URI: $uri")

            val entity = PidDocumentEntity(
                id = docId,
                projectId = projectId,
                filePath = destFile.absolutePath,
                fileName = fileName,
                parseStatus = ParseStatus.PENDING,
            )
            pidDocumentDao.insert(entity)
            entity
        }

    /**
     * Runs the full parsing pipeline for an already-imported PidDocument.
     * Updates the document's parse_status and creates Instrument records.
     */
    suspend fun parsePidDocument(pidDocumentId: String, projectId: String) =
        withContext(Dispatchers.IO) {
            val doc = pidDocumentDao.getById(pidDocumentId)
                ?: error("PidDocument not found: $pidDocumentId")

            pidDocumentDao.updateParseStatus(pidDocumentId, ParseStatus.PROCESSING)

            try {
                val result = File(doc.filePath).inputStream().use { stream ->
                    pidParser.parse(stream)
                }

                // Delete old instruments from this document before inserting fresh ones
                instrumentDao.deleteByPidDocument(pidDocumentId)

                val instruments = result.tags.mapIndexed { index, tag ->
                    InstrumentEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        pidDocumentId = pidDocumentId,
                        pidPageNumber = tag.page,
                        tagId = tag.tagId,
                        tagPrefix = tag.prefix,
                        tagNumber = tag.number,
                        instrumentType = IsaTagDetector.instrumentTypeForPrefix(tag.prefix),
                        pidX = tag.x,
                        pidY = tag.y,
                        sortOrder = index,
                    )
                }

                instrumentDao.insertAll(instruments)

                val status = if (result.warnings.isNotEmpty()) ParseStatus.NEEDS_REVIEW else ParseStatus.COMPLETE
                val warningsJson = if (result.warnings.isNotEmpty()) {
                    org.json.JSONArray(result.warnings).toString()
                } else null

                pidDocumentDao.updateAfterParse(
                    id = pidDocumentId,
                    status = status,
                    parsedAt = System.currentTimeMillis(),
                    count = instruments.size,
                    pageCount = result.pageCount,
                    rawTextJson = result.rawTextJson,
                    warnings = warningsJson,
                )
            } catch (e: Exception) {
                pidDocumentDao.updateParseStatus(pidDocumentId, ParseStatus.FAILED)
                throw e
            }
        }

    /**
     * Extracts the raw text layer from the PDF and stores it in [PidDocumentEntity.rawTextJson]
     * without creating any [InstrumentEntity] records.
     *
     * Used by the new grid-based tagging workflow: the text is available for region-based
     * detection ([PidParser.parseRegion]) whenever the user draws a selection on the diagram.
     */
    suspend fun extractTextOnly(pidDocumentId: String) =
        withContext(Dispatchers.IO) {
            val doc = pidDocumentDao.getById(pidDocumentId)
                ?: error("PidDocument not found: $pidDocumentId")

            pidDocumentDao.updateParseStatus(pidDocumentId, ParseStatus.PROCESSING)

            try {
                val result = File(doc.filePath).inputStream().use { stream ->
                    pidParser.parse(stream)
                }
                // Store raw text for region parsing, but do NOT create any instruments.
                pidDocumentDao.updateAfterParse(
                    id = pidDocumentId,
                    status = ParseStatus.COMPLETE,
                    parsedAt = System.currentTimeMillis(),
                    count = 0,
                    pageCount = result.pageCount,
                    rawTextJson = result.rawTextJson,
                    warnings = null,
                )
            } catch (e: Exception) {
                pidDocumentDao.updateParseStatus(pidDocumentId, ParseStatus.FAILED)
                throw e
            }
        }

    suspend fun updateCalibration(
        pidDocumentId: String,
        width: Float,
        height: Float,
        shape: OverlayShape,
    ) = pidDocumentDao.updateCalibration(pidDocumentId, width, height, shape.name)

    suspend fun reParse(pidDocumentId: String, projectId: String) =
        parsePidDocument(pidDocumentId, projectId)

    suspend fun delete(pidDocumentId: String) {
        val doc = pidDocumentDao.getById(pidDocumentId) ?: return
        withContext(Dispatchers.IO) {
            File(doc.filePath).delete()
        }
        pidDocumentDao.delete(doc)
    }
}
