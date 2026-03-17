package com.fieldtag.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldStatus { NOT_STARTED, IN_PROGRESS, COMPLETE, CANNOT_LOCATE }

@Entity(
    tableName = "instruments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PidDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["pid_document_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("project_id"), Index("pid_document_id")]
)
data class InstrumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "pid_document_id") val pidDocumentId: String,
    @ColumnInfo(name = "pid_page_number") val pidPageNumber: Int,
    @ColumnInfo(name = "tag_id") val tagId: String,
    @ColumnInfo(name = "tag_prefix") val tagPrefix: String,
    @ColumnInfo(name = "tag_number") val tagNumber: String,
    @ColumnInfo(name = "instrument_type") val instrumentType: String? = null,
    @ColumnInfo(name = "pid_x") val pidX: Float? = null,
    @ColumnInfo(name = "pid_y") val pidY: Float? = null,
    @ColumnInfo(name = "field_status") val fieldStatus: FieldStatus = FieldStatus.NOT_STARTED,
    val notes: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
