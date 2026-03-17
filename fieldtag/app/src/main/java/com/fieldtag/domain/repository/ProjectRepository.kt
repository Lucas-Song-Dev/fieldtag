package com.fieldtag.domain.repository

import com.fieldtag.data.db.dao.ProjectDao
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.data.db.entities.ProjectStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
) {
    fun observeAll(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    fun observeActive(): Flow<List<ProjectEntity>> = projectDao.observeActive()

    fun observeById(id: String): Flow<ProjectEntity?> = projectDao.observeById(id)

    suspend fun getById(id: String): ProjectEntity? = projectDao.getById(id)

    suspend fun createProject(
        name: String,
        notes: String? = null,
        locationName: String? = null,
        gpsLat: Double? = null,
        gpsLng: Double? = null,
    ): ProjectEntity {
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            notes = notes,
            locationName = locationName,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
        )
        projectDao.insert(project)
        return project
    }

    suspend fun updateProject(project: ProjectEntity) = projectDao.update(project)

    suspend fun archiveProject(id: String) = projectDao.updateStatus(id, ProjectStatus.ARCHIVED)

    suspend fun completeProject(id: String) = projectDao.updateStatus(id, ProjectStatus.COMPLETE)

    suspend fun deleteProject(id: String) = projectDao.deleteById(id)

    suspend fun recordExport(id: String) = projectDao.updateExportTimestamp(id, System.currentTimeMillis())
}
