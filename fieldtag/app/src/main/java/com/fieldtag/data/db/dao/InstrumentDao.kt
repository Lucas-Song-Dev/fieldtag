package com.fieldtag.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentDao {

    @Query(
        """SELECT * FROM instruments WHERE project_id = :projectId 
           ORDER BY sort_order ASC, pid_page_number ASC, pid_y ASC"""
    )
    fun observeByProject(projectId: String): Flow<List<InstrumentEntity>>

    @Query(
        """SELECT * FROM instruments WHERE project_id = :projectId 
           ORDER BY sort_order ASC, pid_page_number ASC, pid_y ASC"""
    )
    suspend fun getByProject(projectId: String): List<InstrumentEntity>

    @Query(
        """SELECT * FROM instruments WHERE project_id = :projectId AND field_status = :status
           ORDER BY sort_order ASC"""
    )
    fun observeByProjectAndStatus(projectId: String, status: FieldStatus): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE pid_document_id = :pidDocId ORDER BY sort_order ASC")
    fun observeByPidDocument(pidDocId: String): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE pid_document_id = :pidDocId ORDER BY sort_order ASC")
    suspend fun getByPidDocument(pidDocId: String): List<InstrumentEntity>

    @Query("SELECT * FROM instruments WHERE id = :id")
    fun observeById(id: String): Flow<InstrumentEntity?>

    @Query("SELECT * FROM instruments WHERE id = :id")
    suspend fun getById(id: String): InstrumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instrument: InstrumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instruments: List<InstrumentEntity>)

    @Update
    suspend fun update(instrument: InstrumentEntity)

    @Delete
    suspend fun delete(instrument: InstrumentEntity)

    @Query("DELETE FROM instruments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM instruments WHERE pid_document_id = :pidDocId")
    suspend fun deleteByPidDocument(pidDocId: String)

    @Query("UPDATE instruments SET field_status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: FieldStatus, completedAt: Long?)

    @Query("UPDATE instruments SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)

    @Query("UPDATE instruments SET pid_x = :x, pid_y = :y WHERE id = :id")
    suspend fun updatePosition(id: String, x: Float, y: Float)

    @Query("UPDATE instruments SET overlay_shape = :shape WHERE id = :id")
    suspend fun updateShape(id: String, shape: String?)

    @Query("UPDATE instruments SET tag_id = :tagId WHERE id = :id")
    suspend fun updateTagId(id: String, tagId: String)

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId")
    fun observeTotalCount(projectId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId AND field_status = 'COMPLETE'")
    fun observeCompleteCount(projectId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId AND field_status = 'IN_PROGRESS'")
    fun observeInProgressCount(projectId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId AND field_status = 'CANNOT_LOCATE'")
    fun observeCannotLocateCount(projectId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId")
    suspend fun countByProject(projectId: String): Int

    @Query("SELECT COUNT(*) FROM instruments WHERE project_id = :projectId AND field_status = 'COMPLETE'")
    suspend fun countComplete(projectId: String): Int
}
