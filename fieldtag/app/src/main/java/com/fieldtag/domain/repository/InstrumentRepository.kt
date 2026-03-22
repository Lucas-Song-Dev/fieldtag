package com.fieldtag.domain.repository

import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.OverlayShape
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstrumentRepository @Inject constructor(
    private val instrumentDao: InstrumentDao,
) {
    fun observeByProject(projectId: String): Flow<List<InstrumentEntity>> =
        instrumentDao.observeByProject(projectId)

    fun observeByProjectAndStatus(projectId: String, status: FieldStatus): Flow<List<InstrumentEntity>> =
        instrumentDao.observeByProjectAndStatus(projectId, status)

    fun observeByPidDocument(pidDocId: String): Flow<List<InstrumentEntity>> =
        instrumentDao.observeByPidDocument(pidDocId)

    fun observeById(id: String): Flow<InstrumentEntity?> = instrumentDao.observeById(id)

    suspend fun getById(id: String): InstrumentEntity? = instrumentDao.getById(id)

    suspend fun getByProject(projectId: String): List<InstrumentEntity> =
        instrumentDao.getByProject(projectId)

    suspend fun getByPidDocument(pidDocId: String): List<InstrumentEntity> =
        instrumentDao.getByPidDocument(pidDocId)

    suspend fun insertAll(instruments: List<InstrumentEntity>) = instrumentDao.insertAll(instruments)

    suspend fun update(instrument: InstrumentEntity) = instrumentDao.update(instrument)

    suspend fun delete(instrument: InstrumentEntity) = instrumentDao.delete(instrument)

    suspend fun deleteById(id: String) = instrumentDao.deleteById(id)

    suspend fun deleteByPidDocument(pidDocId: String) = instrumentDao.deleteByPidDocument(pidDocId)

    suspend fun markComplete(id: String) {
        instrumentDao.updateStatus(id, FieldStatus.COMPLETE, System.currentTimeMillis())
    }

    suspend fun markCannotLocate(id: String) {
        instrumentDao.updateStatus(id, FieldStatus.CANNOT_LOCATE, null)
    }

    suspend fun markInProgress(id: String) {
        instrumentDao.updateStatus(id, FieldStatus.IN_PROGRESS, null)
    }

    suspend fun resetStatus(id: String) {
        instrumentDao.updateStatus(id, FieldStatus.NOT_STARTED, null)
    }

    suspend fun updateNotes(id: String, notes: String?) = instrumentDao.updateNotes(id, notes)

    suspend fun updatePosition(id: String, x: Float, y: Float) =
        instrumentDao.updatePosition(id, x, y)

    suspend fun updateShape(id: String, shape: OverlayShape?) =
        instrumentDao.updateShape(id, shape?.name)

    suspend fun updateTagId(id: String, tagId: String) =
        instrumentDao.updateTagId(id, tagId)

    fun observeTotalCount(projectId: String): Flow<Int> = instrumentDao.observeTotalCount(projectId)

    fun observeCompleteCount(projectId: String): Flow<Int> = instrumentDao.observeCompleteCount(projectId)

    fun observeInProgressCount(projectId: String): Flow<Int> = instrumentDao.observeInProgressCount(projectId)

    fun observeCannotLocateCount(projectId: String): Flow<Int> = instrumentDao.observeCannotLocateCount(projectId)

    suspend fun countByProject(projectId: String): Int = instrumentDao.countByProject(projectId)

    suspend fun countComplete(projectId: String): Int = instrumentDao.countComplete(projectId)
}
