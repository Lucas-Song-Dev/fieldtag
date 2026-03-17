package com.fieldtag.repository

import app.cash.turbine.test
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.domain.repository.InstrumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [InstrumentRepository].
 */
class InstrumentRepositoryTest {

    private lateinit var dao: InstrumentDao
    private lateinit var repository: InstrumentRepository

    private fun makeInstrument(
        id: String = "i1",
        tagId: String = "LIT-5219",
        status: FieldStatus = FieldStatus.NOT_STARTED,
    ) = InstrumentEntity(
        id = id,
        projectId = "proj-1",
        pidDocumentId = "doc-1",
        pidPageNumber = 1,
        tagId = tagId,
        tagPrefix = tagId.substringBefore("-"),
        tagNumber = tagId.substringAfter("-"),
        fieldStatus = status,
    )

    @Before fun setUp() {
        dao = mockk(relaxed = true)
        repository = InstrumentRepository(dao)
    }

    @Test fun `observeByProject emits list from dao`() = runTest {
        val instruments = listOf(makeInstrument("i1", "LIT-5219"), makeInstrument("i2", "PV-5218"))
        every { dao.observeByProject("proj-1") } returns flowOf(instruments)

        repository.observeByProject("proj-1").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            awaitComplete()
        }
    }

    @Test fun `observeTotalCount emits from dao`() = runTest {
        every { dao.observeTotalCount("proj-1") } returns flowOf(7)

        repository.observeTotalCount("proj-1").test {
            assertEquals(7, awaitItem())
            awaitComplete()
        }
    }

    @Test fun `observeCompleteCount emits from dao`() = runTest {
        every { dao.observeCompleteCount("proj-1") } returns flowOf(3)

        repository.observeCompleteCount("proj-1").test {
            assertEquals(3, awaitItem())
            awaitComplete()
        }
    }

    @Test fun `insertAll calls dao insertAll`() = runTest {
        val list = listOf(makeInstrument("i1"), makeInstrument("i2"))
        repository.insertAll(list)
        coVerify { dao.insertAll(list) }
    }

    @Test fun `markComplete calls dao with COMPLETE status and timestamp`() = runTest {
        repository.markComplete("i1")
        coVerify { dao.updateStatus(eq("i1"), eq(FieldStatus.COMPLETE), any()) }
    }

    @Test fun `markCannotLocate calls dao with CANNOT_LOCATE and null timestamp`() = runTest {
        repository.markCannotLocate("i1")
        coVerify { dao.updateStatus("i1", FieldStatus.CANNOT_LOCATE, null) }
    }

    @Test fun `markInProgress calls dao with IN_PROGRESS`() = runTest {
        repository.markInProgress("i1")
        coVerify { dao.updateStatus("i1", FieldStatus.IN_PROGRESS, null) }
    }

    @Test fun `resetStatus calls dao with NOT_STARTED`() = runTest {
        repository.resetStatus("i1")
        coVerify { dao.updateStatus("i1", FieldStatus.NOT_STARTED, null) }
    }

    @Test fun `updateNotes calls dao`() = runTest {
        repository.updateNotes("i1", "Check valve clearance")
        coVerify { dao.updateNotes("i1", "Check valve clearance") }
    }

    @Test fun `updatePosition calls dao`() = runTest {
        repository.updatePosition("i1", 0.5f, 0.3f)
        coVerify { dao.updatePosition("i1", 0.5f, 0.3f) }
    }

    @Test fun `deleteByPidDocument calls dao`() = runTest {
        repository.deleteByPidDocument("doc-1")
        coVerify { dao.deleteByPidDocument("doc-1") }
    }

    @Test fun `getByProject delegates to dao`() = runTest {
        val list = listOf(makeInstrument())
        coEvery { dao.getByProject("proj-1") } returns list

        val result = repository.getByProject("proj-1")
        assertEquals(1, result.size)
    }

    @Test fun `countByProject delegates to dao`() = runTest {
        coEvery { dao.countByProject("proj-1") } returns 5
        assertEquals(5, repository.countByProject("proj-1"))
    }

    @Test fun `countComplete delegates to dao`() = runTest {
        coEvery { dao.countComplete("proj-1") } returns 3
        assertEquals(3, repository.countComplete("proj-1"))
    }
}
