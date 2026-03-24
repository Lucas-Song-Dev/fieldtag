package com.fieldtag.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "pid_page_calibrations",
    primaryKeys = ["pid_document_id", "page_number"],
    foreignKeys = [
        ForeignKey(
            entity = PidDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["pid_document_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("pid_document_id")],
)
data class PidPageCalibrationEntity(
    @ColumnInfo(name = "pid_document_id") val pidDocumentId: String,
    /** 1-based page number (matches InstrumentEntity.pidPageNumber). */
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "calibration_width") val calibrationWidth: Float,
    @ColumnInfo(name = "calibration_height") val calibrationHeight: Float,
    @ColumnInfo(name = "calibration_shape") val calibrationShape: OverlayShape = OverlayShape.RECTANGLE,
)
