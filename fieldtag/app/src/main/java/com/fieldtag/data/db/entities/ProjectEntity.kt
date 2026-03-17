package com.fieldtag.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProjectStatus { ACTIVE, COMPLETE, ARCHIVED }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "location_name") val locationName: String? = null,
    @ColumnInfo(name = "gps_lat") val gpsLat: Double? = null,
    @ColumnInfo(name = "gps_lng") val gpsLng: Double? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val notes: String? = null,
    @ColumnInfo(name = "export_last_at") val exportLastAt: Long? = null,
)
