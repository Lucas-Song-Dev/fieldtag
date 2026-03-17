package com.fieldtag.repository

import app.cash.turbine.test
import com.fieldtag.data.db.dao.ProjectDao
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.data.db.entities.ProjectStatus
import com.fieldtag.domain.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProjectRepository] using MockK.
 */
class ProjectRepositoryTest {

    private lateinit var dao: ProjectDao
    private lateinit var repository: ProjectRepository

    @Before fun setUp() {
        dao = mockk(relaxed = true)
        repository = ProjectRepository(dao)
    }

    @Test fun `observeActive returns flow from dao`() = runTest {
        val projects = listOf(
            ProjectEntity(id = "1", name = "Project Alpha"),
            ProjectEntity(id = "2", name = "Project Beta"),
        )
        every { dao.observeActive() } returns flowOf(projects)

        repository.observeActive().test {
            val emitted = awaitItem()
            assertEquals(2, emitted.size)
            assertEquals("Project Alpha", emitted[0].name)
            awaitComplete()
        }
    }

    @Test fun `observeById returns flow from dao`() = runTest {
        val project = ProjectEntity(id = "abc", name = "My Site")
        every { dao.observeById("abc") } returns flowOf(project)

        repository.observeById("abc").test {
            val emitted = awaitItem()
            assertNotNull(emitted)
            assertEquals("My Site", emitted!!.name)
            awaitComplete()
        }
    }

    @Test fun `observeById emits null when not found`() = runTest {
        every { dao.observeById("missing") } returns flowOf(null)

        repository.observeById("missing").test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test fun `createProject calls dao insert with correct data`() = runTest {
        val insertSlot = slot<ProjectEntity>()
        coEvery { dao.insert(capture(insertSlot)) } returns Unit

        val project = repository.createProject("Test Site", notes = "Some notes")

        assertEquals("Test Site", project.name)
        assertEquals("Some notes", project.notes)
        assertEquals(ProjectStatus.ACTIVE, project.status)
        assertNotNull(project.id)
        coVerify(exactly = 1) { dao.insert(any()) }
        assertEquals(insertSlot.captured.name, "Test Site")
    }

    @Test fun `createProject generates unique IDs`() = runTest {
        coEvery { dao.insert(any()) } returns Unit

        val p1 = repository.createProject("Site A")
        val p2 = repository.createProject("Site B")

        assertTrue("IDs should be unique", p1.id != p2.id)
    }

    @Test fun `createProject with null notes passes null to dao`() = runTest {
        val insertSlot = slot<ProjectEntity>()
        coEvery { dao.insert(capture(insertSlot)) } returns Unit

        repository.createProject("Site", notes = null)

        assertNull(insertSlot.captured.notes)
    }

    @Test fun `archiveProject calls dao with ARCHIVED status`() = runTest {
        repository.archiveProject("proj-1")
        coVerify { dao.updateStatus("proj-1", ProjectStatus.ARCHIVED) }
    }

    @Test fun `completeProject calls dao with COMPLETE status`() = runTest {
        repository.completeProject("proj-1")
        coVerify { dao.updateStatus("proj-1", ProjectStatus.COMPLETE) }
    }

    @Test fun `deleteProject calls dao deleteById`() = runTest {
        repository.deleteProject("proj-2")
        coVerify { dao.deleteById("proj-2") }
    }

    @Test fun `recordExport calls dao updateExportTimestamp`() = runTest {
        repository.recordExport("proj-3")
        coVerify { dao.updateExportTimestamp(eq("proj-3"), any()) }
    }

    @Test fun `getById delegates to dao`() = runTest {
        val project = ProjectEntity(id = "x", name = "Site X")
        coEvery { dao.getById("x") } returns project

        val result = repository.getById("x")
        assertEquals("Site X", result?.name)
    }

    @Test fun `getById returns null when not found`() = runTest {
        coEvery { dao.getById("unknown") } returns null
        assertNull(repository.getById("unknown"))
    }

    @Test fun `updateProject delegates to dao`() = runTest {
        val project = ProjectEntity(id = "z", name = "Updated")
        repository.updateProject(project)
        coVerify { dao.update(project) }
    }
}
