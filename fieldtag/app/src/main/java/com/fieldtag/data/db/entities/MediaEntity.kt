package com.fieldtag.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType { PHOTO, VIDEO }

enum class MediaRole { OVERVIEW, DETAIL, NAMEPLATE, BEFORE, DURING, AFTER, SAFETY, OTHER }

enum class MediaSource { LIVE_CAPTURE, BATCH_IMPORT }

@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["instrument_id"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("project_id"), Index("instrument_id")]
)
data class MediaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "instrument_id") val instrumentId: String? = null,
    @ColumnInfo(name = "project_id") val projectId: String,
    val type: MediaType = MediaType.PHOTO,
    val role: MediaRole = MediaRole.DETAIL,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Int? = null,
    @ColumnInfo(name = "captured_at") val capturedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "exif_missing") val exifMissing: Boolean = false,
    @ColumnInfo(name = "gps_lat") val gpsLat: Double? = null,
    @ColumnInfo(name = "gps_lng") val gpsLng: Double? = null,
    val source: MediaSource = MediaSource.LIVE_CAPTURE,
    val notes: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
