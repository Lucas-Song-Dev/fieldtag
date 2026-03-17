package com.fieldtag.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.data.db.entities.ProjectStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE status != 'ARCHIVED' ORDER BY created_at DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ProjectStatus)

    @Query("UPDATE projects SET export_last_at = :timestamp WHERE id = :id")
    suspend fun updateExportTimestamp(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM projects WHERE status = 'ACTIVE'")
    suspend fun countActive(): Int
}
