package com.fieldtag.domain.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fieldtag.data.db.dao.MediaDao
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.data.db.entities.MediaSource
import com.fieldtag.data.db.entities.MediaType
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
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    @ApplicationContext private val context: Context,
) {
    fun observeByInstrument(instrumentId: String): Flow<List<MediaEntity>> =
        mediaDao.observeByInstrument(instrumentId)

    fun observeUngrouped(projectId: String): Flow<List<MediaEntity>> =
        mediaDao.observeUngrouped(projectId)

    fun observeUngroupedCount(projectId: String): Flow<Int> =
        mediaDao.observeUngroupedCount(projectId)

    fun observeCountForInstrument(instrumentId: String): Flow<Int> =
        mediaDao.observeCountForInstrument(instrumentId)

    suspend fun getByInstrument(instrumentId: String): List<MediaEntity> =
        mediaDao.getByInstrument(instrumentId)

    suspend fun getUngrouped(projectId: String): List<MediaEntity> =
        mediaDao.getUngrouped(projectId)

    suspend fun getByProject(projectId: String): List<MediaEntity> =
        mediaDao.getByProject(projectId)

    /**
     * Saves a photo bitmap to internal storage, generates a thumbnail,
     * and inserts the Room record. Write order is: file → thumbnail → DB,
     * ensuring crash safety (orphan recovery can detect files without DB records).
     */
    suspend fun savePhoto(
        bitmap: Bitmap,
        projectId: String,
        instrumentId: String?,
        role: MediaRole = MediaRole.DETAIL,
        notes: String? = null,
        gpsLat: Double? = null,
        gpsLng: Double? = null,
        source: MediaSource = MediaSource.LIVE_CAPTURE,
    ): MediaEntity = withContext(Dispatchers.IO) {
        val mediaId = UUID.randomUUID().toString()
        val mediaDir = getMediaDir(projectId)

        // 1. Write full-resolution image to disk
        val imageFile = File(mediaDir, "$mediaId.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.fd.sync() // fsync
        }

        // 2. Write thumbnail to disk
        val thumbFile = File(getThumbDir(projectId), "$mediaId.jpg")
        val thumbBitmap = createThumbnail(bitmap, 400)
        FileOutputStream(thumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.fd.sync() // fsync
        }
        thumbBitmap.recycle()

        // 3. Insert DB record (after both files are safely on disk)
        val entity = MediaEntity(
            id = mediaId,
            instrumentId = instrumentId,
            projectId = projectId,
            type = MediaType.PHOTO,
            role = role,
            filePath = imageFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
            source = source,
            notes = notes,
            sortOrder = mediaDao.countForInstrument(instrumentId ?: ""),
        )
        mediaDao.insert(entity)
        entity
    }

    suspend fun assignToInstrument(mediaId: String, instrumentId: String?) =
        mediaDao.assignToInstrument(mediaId, instrumentId)

    suspend fun updateRole(mediaId: String, role: MediaRole) =
        mediaDao.updateRole(mediaId, role)

    suspend fun updateNotes(mediaId: String, notes: String?) =
        mediaDao.updateNotes(mediaId, notes)

    suspend fun updateSortOrder(mediaId: String, order: Int) =
        mediaDao.updateSortOrder(mediaId, order)

    suspend fun delete(media: MediaEntity) = withContext(Dispatchers.IO) {
        // Delete files from disk first, then remove DB record
        File(media.filePath).delete()
        File(media.thumbnailPath).delete()
        mediaDao.delete(media)
    }

    /**
     * Scans for orphaned media files (files on disk with no DB record).
     * Returns them as provisional MediaEntity objects in the ungrouped bucket.
     */
    suspend fun recoverOrphanedMedia(projectId: String): List<File> = withContext(Dispatchers.IO) {
        val mediaDir = getMediaDir(projectId)
        if (!mediaDir.exists()) return@withContext emptyList()

        val knownPaths = mediaDao.getByProject(projectId).map { it.filePath }.toSet()
        mediaDir.listFiles()
            ?.filter { it.extension == "jpg" && it.absolutePath !in knownPaths }
            ?: emptyList()
    }

    private fun getMediaDir(projectId: String): File {
        val dir = File(context.filesDir, "media/$projectId")
        dir.mkdirs()
        return dir
    }

    private fun getThumbDir(projectId: String): File {
        val dir = File(context.filesDir, "thumbs/$projectId")
        dir.mkdirs()
        return dir
    }

    private fun createThumbnail(source: Bitmap, maxDim: Int): Bitmap {
        val ratio = source.width.toFloat() / source.height.toFloat()
        val (w, h) = if (ratio > 1f) {
            maxDim to (maxDim / ratio).toInt()
        } else {
            (maxDim * ratio).toInt() to maxDim
        }
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
