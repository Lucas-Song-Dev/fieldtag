package com.fieldtag.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ParseStatus { PENDING, PROCESSING, COMPLETE, FAILED, NEEDS_REVIEW }

@Entity(
    tableName = "pid_documents",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("project_id")]
)
data class PidDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "page_count") val pageCount: Int = 0,
    @ColumnInfo(name = "parse_status") val parseStatus: ParseStatus = ParseStatus.PENDING,
    @ColumnInfo(name = "parsed_at") val parsedAt: Long? = null,
    @ColumnInfo(name = "instrument_count") val instrumentCount: Int = 0,
    @ColumnInfo(name = "raw_text_json") val rawTextJson: String? = null,
    @ColumnInfo(name = "parse_warnings") val parseWarnings: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
