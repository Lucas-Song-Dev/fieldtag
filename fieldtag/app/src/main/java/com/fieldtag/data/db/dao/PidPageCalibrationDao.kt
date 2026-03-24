package com.fieldtag.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtag.data.db.entities.PidPageCalibrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PidPageCalibrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PidPageCalibrationEntity)

    @Query("SELECT * FROM pid_page_calibrations WHERE pid_document_id = :pidDocumentId AND page_number = :pageNumber LIMIT 1")
    suspend fun getForPage(pidDocumentId: String, pageNumber: Int): PidPageCalibrationEntity?

    @Query("SELECT * FROM pid_page_calibrations WHERE pid_document_id = :pidDocumentId ORDER BY page_number ASC")
    suspend fun getAllForDocument(pidDocumentId: String): List<PidPageCalibrationEntity>

    @Query("SELECT * FROM pid_page_calibrations WHERE pid_document_id = :pidDocumentId ORDER BY page_number ASC")
    fun observeAllForDocument(pidDocumentId: String): Flow<List<PidPageCalibrationEntity>>
}
