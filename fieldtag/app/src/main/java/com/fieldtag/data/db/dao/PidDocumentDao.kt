package com.fieldtag.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.PidDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PidDocumentDao {

    @Query("SELECT * FROM pid_documents WHERE project_id = :projectId ORDER BY created_at ASC")
    fun observeByProject(projectId: String): Flow<List<PidDocumentEntity>>

    @Query("SELECT * FROM pid_documents WHERE project_id = :projectId ORDER BY created_at ASC")
    suspend fun getByProject(projectId: String): List<PidDocumentEntity>

    @Query("SELECT * FROM pid_documents WHERE id = :id")
    suspend fun getById(id: String): PidDocumentEntity?

    @Query("SELECT * FROM pid_documents WHERE id = :id")
    fun observeById(id: String): Flow<PidDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: PidDocumentEntity)

    @Update
    suspend fun update(document: PidDocumentEntity)

    @Delete
    suspend fun delete(document: PidDocumentEntity)

    @Query("UPDATE pid_documents SET parse_status = :status WHERE id = :id")
    suspend fun updateParseStatus(id: String, status: ParseStatus)

    @Query(
        """UPDATE pid_documents 
           SET parse_status = :status, parsed_at = :parsedAt, instrument_count = :count,
               page_count = :pageCount, raw_text_json = :rawTextJson, parse_warnings = :warnings
           WHERE id = :id"""
    )
    suspend fun updateAfterParse(
        id: String,
        status: ParseStatus,
        parsedAt: Long,
        count: Int,
        pageCount: Int,
        rawTextJson: String?,
        warnings: String?,
    )
}
