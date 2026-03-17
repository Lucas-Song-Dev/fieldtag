package com.fieldtag.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.MediaRole
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query(
        """SELECT * FROM media WHERE instrument_id = :instrumentId 
           ORDER BY sort_order ASC, captured_at ASC"""
    )
    fun observeByInstrument(instrumentId: String): Flow<List<MediaEntity>>

    @Query(
        """SELECT * FROM media WHERE instrument_id = :instrumentId 
           ORDER BY sort_order ASC, captured_at ASC"""
    )
    suspend fun getByInstrument(instrumentId: String): List<MediaEntity>

    @Query(
        """SELECT * FROM media WHERE project_id = :projectId AND instrument_id IS NULL
           ORDER BY captured_at ASC"""
    )
    fun observeUngrouped(projectId: String): Flow<List<MediaEntity>>

    @Query(
        """SELECT * FROM media WHERE project_id = :projectId AND instrument_id IS NULL
           ORDER BY captured_at ASC"""
    )
    suspend fun getUngrouped(projectId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE project_id = :projectId ORDER BY captured_at ASC")
    suspend fun getByProject(projectId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE file_path = :filePath")
    suspend fun getByFilePath(filePath: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media SET instrument_id = :instrumentId WHERE id = :id")
    suspend fun assignToInstrument(id: String, instrumentId: String?)

    @Query("UPDATE media SET role = :role WHERE id = :id")
    suspend fun updateRole(id: String, role: MediaRole)

    @Query("UPDATE media SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)

    @Query("UPDATE media SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("SELECT COUNT(*) FROM media WHERE project_id = :projectId AND instrument_id IS NULL")
    fun observeUngroupedCount(projectId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE instrument_id = :instrumentId")
    fun observeCountForInstrument(instrumentId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE instrument_id = :instrumentId")
    suspend fun countForInstrument(instrumentId: String): Int

    @Query("SELECT * FROM media WHERE instrument_id IS NULL AND file_path NOT IN (SELECT file_path FROM media WHERE instrument_id IS NOT NULL)")
    suspend fun getOrphanedMedia(): List<MediaEntity>
}
