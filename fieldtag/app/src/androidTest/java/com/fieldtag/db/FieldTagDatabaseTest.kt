package com.fieldtag.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtag.data.db.FieldTagDatabase
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.dao.MediaDao
import com.fieldtag.data.db.dao.PidDocumentDao
import com.fieldtag.data.db.dao.ProjectDao
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.data.db.entities.MediaSource
import com.fieldtag.data.db.entities.MediaType
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.data.db.entities.ProjectStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room database instrumented tests using an in-memory database.
 * Tests cover: CRUD operations, Flow emissions, cascade deletes, foreign key constraints.
 */
@RunWith(AndroidJUnit4::class)
class FieldTagDatabaseTest {

    private lateinit var db: FieldTagDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var pidDao: PidDocumentDao
    private lateinit var instrumentDao: InstrumentDao
    private lateinit var mediaDao: MediaDao

    @Before fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTagDatabase::class.java,
        ).allowMainThreadQueries().build()

        projectDao = db.projectDao()
        pidDao = db.pidDocumentDao()
        instrumentDao = db.instrumentDao()
        mediaDao = db.mediaDao()
    }

    @After fun closeDb() = db.close()

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun insertProject(id: String = "p1", name: String = "Test Project"): ProjectEntity {
        val p = ProjectEntity(id = id, name = name)
        projectDao.insert(p)
        return p
    }

    private suspend fun insertPidDoc(projectId: String = "p1", docId: String = "d1"): PidDocumentEntity {
        val doc = PidDocumentEntity(id = docId, projectId = projectId, filePath = "/tmp/test.pdf", fileName = "test.pdf")
        pidDao.insert(doc)
        return doc
    }

    private suspend fun insertInstrument(
        id: String = "i1",
        projectId: String = "p1",
        docId: String = "d1",
        tagId: String = "LIT-5219",
    ): InstrumentEntity {
        val instr = InstrumentEntity(
            id = id, projectId = projectId, pidDocumentId = docId,
            pidPageNumber = 1, tagId = tagId, tagPrefix = tagId.substringBefore("-"),
            tagNumber = tagId.substringAfter("-"),
        )
        instrumentDao.insert(instr)
        return instr
    }

    private suspend fun insertMedia(
        id: String = "m1",
        projectId: String = "p1",
        instrumentId: String? = "i1",
    ): MediaEntity {
        val media = MediaEntity(
            id = id, projectId = projectId, instrumentId = instrumentId,
            filePath = "/tmp/$id.jpg", thumbnailPath = "/tmp/${id}_thumb.jpg",
        )
        mediaDao.insert(media)
        return media
    }

    // ─── Project CRUD ─────────────────────────────────────────────────────────

    @Test fun insert_and_retrieve_project() = runTest {
        insertProject("p1", "Site Alpha")
        val project = projectDao.getById("p1")
        assertNotNull(project)
        assertEquals("Site Alpha", project!!.name)
        assertEquals(ProjectStatus.ACTIVE, project.status)
    }

    @Test fun update_project_changes_name() = runTest {
        val p = insertProject("p1", "Old Name")
        projectDao.update(p.copy(name = "New Name"))
        assertEquals("New Name", projectDao.getById("p1")!!.name)
    }

    @Test fun delete_project_removes_from_db() = runTest {
        insertProject("p1")
        projectDao.deleteById("p1")
        assertNull(projectDao.getById("p1"))
    }

    @Test fun observeActive_emits_only_non_archived_projects() = runTest {
        insertProject("p1", "Active")
        insertProject("p2", "Archived")
        projectDao.updateStatus("p2", ProjectStatus.ARCHIVED)

        val active = projectDao.observeActive().first()
        assertEquals(1, active.size)
        assertEquals("Active", active[0].name)
    }

    @Test fun updateStatus_changes_project_status() = runTest {
        insertProject("p1")
        projectDao.updateStatus("p1", ProjectStatus.COMPLETE)
        assertEquals(ProjectStatus.COMPLETE, projectDao.getById("p1")!!.status)
    }

    // ─── PidDocument CRUD ─────────────────────────────────────────────────────

    @Test fun insert_and_retrieve_pid_document() = runTest {
        insertProject()
        insertPidDoc("p1", "d1")
        val doc = pidDao.getById("d1")
        assertNotNull(doc)
        assertEquals("test.pdf", doc!!.fileName)
        assertEquals(ParseStatus.PENDING, doc.parseStatus)
    }

    @Test fun updateParseStatus_changes_status() = runTest {
        insertProject(); insertPidDoc()
        pidDao.updateParseStatus("d1", ParseStatus.PROCESSING)
        assertEquals(ParseStatus.PROCESSING, pidDao.getById("d1")!!.parseStatus)
    }

    @Test fun updateAfterParse_sets_all_fields() = runTest {
        insertProject(); insertPidDoc()
        val now = System.currentTimeMillis()
        pidDao.updateAfterParse(
            id = "d1",
            status = ParseStatus.COMPLETE,
            parsedAt = now,
            count = 12,
            pageCount = 3,
            rawTextJson = "{\"page\":1}",
            warnings = null,
        )
        val doc = pidDao.getById("d1")!!
        assertEquals(ParseStatus.COMPLETE, doc.parseStatus)
        assertEquals(12, doc.instrumentCount)
        assertEquals(3, doc.pageCount)
        assertEquals("{\"page\":1}", doc.rawTextJson)
    }

    // ─── Instrument CRUD ──────────────────────────────────────────────────────

    @Test fun insert_and_retrieve_instrument() = runTest {
        insertProject(); insertPidDoc()
        insertInstrument("i1", tagId = "LIT-5219")
        val instr = instrumentDao.getById("i1")
        assertNotNull(instr)
        assertEquals("LIT-5219", instr!!.tagId)
        assertEquals(FieldStatus.NOT_STARTED, instr.fieldStatus)
    }

    @Test fun insertAll_inserts_multiple_instruments() = runTest {
        insertProject(); insertPidDoc()
        val list = listOf("LIT-5219", "PV-5218", "FIT-5221").mapIndexed { i, tag ->
            InstrumentEntity(
                id = "i$i", projectId = "p1", pidDocumentId = "d1",
                pidPageNumber = 1, tagId = tag,
                tagPrefix = tag.substringBefore("-"), tagNumber = tag.substringAfter("-"),
            )
        }
        instrumentDao.insertAll(list)
        assertEquals(3, instrumentDao.countByProject("p1"))
    }

    @Test fun updateStatus_to_COMPLETE_sets_completedAt() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        val now = System.currentTimeMillis()
        instrumentDao.updateStatus("i1", FieldStatus.COMPLETE, now)
        val instr = instrumentDao.getById("i1")!!
        assertEquals(FieldStatus.COMPLETE, instr.fieldStatus)
        assertEquals(now, instr.completedAt)
    }

    @Test fun updateStatus_to_CANNOT_LOCATE_sets_null_completedAt() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        instrumentDao.updateStatus("i1", FieldStatus.CANNOT_LOCATE, null)
        val instr = instrumentDao.getById("i1")!!
        assertEquals(FieldStatus.CANNOT_LOCATE, instr.fieldStatus)
        assertNull(instr.completedAt)
    }

    @Test fun countComplete_returns_correct_count() = runTest {
        insertProject(); insertPidDoc()
        insertInstrument("i1"); insertInstrument("i2", tagId = "PV-5218"); insertInstrument("i3", tagId = "FIT-5221")
        instrumentDao.updateStatus("i1", FieldStatus.COMPLETE, System.currentTimeMillis())
        instrumentDao.updateStatus("i2", FieldStatus.COMPLETE, System.currentTimeMillis())
        assertEquals(2, instrumentDao.countComplete("p1"))
        assertEquals(3, instrumentDao.countByProject("p1"))
    }

    @Test fun observeByProject_emits_instruments_in_sort_order() = runTest {
        insertProject(); insertPidDoc()
        val instruments = listOf("LIT-5219", "PV-5218", "FIT-5221").mapIndexed { i, tag ->
            InstrumentEntity(
                id = "i$i", projectId = "p1", pidDocumentId = "d1",
                pidPageNumber = 1, tagId = tag,
                tagPrefix = tag.substringBefore("-"), tagNumber = tag.substringAfter("-"),
                sortOrder = i,
            )
        }
        instrumentDao.insertAll(instruments)

        val emitted = instrumentDao.observeByProject("p1").first()
        assertEquals(3, emitted.size)
        assertEquals("LIT-5219", emitted[0].tagId)
    }

    // ─── Cascade delete tests ─────────────────────────────────────────────────

    @Test fun deleting_project_cascades_to_instruments() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        assertEquals(1, instrumentDao.countByProject("p1"))

        projectDao.deleteById("p1")

        assertEquals(0, instrumentDao.countByProject("p1"))
    }

    @Test fun deleting_project_cascades_to_media() = runTest {
        insertProject(); insertPidDoc(); insertInstrument(); insertMedia()
        val beforeDelete = mediaDao.getByProject("p1")
        assertEquals(1, beforeDelete.size)

        projectDao.deleteById("p1")

        val afterDelete = mediaDao.getByProject("p1")
        assertTrue(afterDelete.isEmpty())
    }

    @Test fun deleting_pid_document_cascades_to_instruments() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        pidDao.delete(pidDao.getById("d1")!!)
        assertEquals(0, instrumentDao.countByProject("p1"))
    }

    // ─── Media CRUD ───────────────────────────────────────────────────────────

    @Test fun insert_and_retrieve_media() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        insertMedia("m1", instrumentId = "i1")
        val media = mediaDao.getById("m1")
        assertNotNull(media)
        assertEquals("i1", media!!.instrumentId)
    }

    @Test fun ungrouped_media_has_null_instrumentId() = runTest {
        insertProject()
        insertMedia("m1", instrumentId = null)
        val ungrouped = mediaDao.getUngrouped("p1")
        assertEquals(1, ungrouped.size)
        assertNull(ungrouped[0].instrumentId)
    }

    @Test fun observeUngroupedCount_emits_correct_count() = runTest {
        insertProject()
        insertMedia("m1", instrumentId = null)
        insertMedia("m2", instrumentId = null)

        val count = mediaDao.observeUngroupedCount("p1").first()
        assertEquals(2, count)
    }

    @Test fun assignToInstrument_moves_media() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        insertMedia("m1", instrumentId = null)
        mediaDao.assignToInstrument("m1", "i1")
        assertEquals("i1", mediaDao.getById("m1")!!.instrumentId)
    }

    @Test fun updateRole_changes_media_role() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        insertMedia("m1")
        mediaDao.updateRole("m1", MediaRole.NAMEPLATE)
        assertEquals(MediaRole.NAMEPLATE, mediaDao.getById("m1")!!.role)
    }

    @Test fun countForInstrument_returns_correct_count() = runTest {
        insertProject(); insertPidDoc(); insertInstrument()
        insertMedia("m1"); insertMedia("m2")
        assertEquals(2, mediaDao.countForInstrument("i1"))
    }
}
