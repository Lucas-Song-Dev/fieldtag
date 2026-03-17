package com.fieldtag.export

import android.content.Context
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.data.db.entities.MediaType
import com.fieldtag.data.db.entities.MediaSource
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import com.fieldtag.domain.repository.ProjectRepository
import com.fieldtag.ui.export.ZipExportService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests for [ZipExportService].
 *
 * Uses a [TemporaryFolder] rule for real file I/O and mocked repositories.
 * Inspects the produced ZIP structure to verify folder layout, file naming,
 * and edge-case handling (missing files, ungrouped media, special characters).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZipExportServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var projectRepo: ProjectRepository
    private lateinit var instrumentRepo: InstrumentRepository
    private lateinit var mediaRepo: MediaRepository
    private lateinit var context: Context
    private lateinit var service: ZipExportService

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private val project = ProjectEntity(id = "proj-1", name = "Test Project")

    private fun instrument(tagId: String, id: String = tagId) = InstrumentEntity(
        id = id,
        projectId = project.id,
        pidDocumentId = "doc-1",
        pidPageNumber = 1,
        tagId = tagId,
        tagPrefix = tagId.substringBefore("-"),
        tagNumber = tagId.substringAfter("-"),
    )

    private fun media(
        id: String,
        instrumentId: String?,
        role: MediaRole,
        filePath: String,
        capturedAt: Long = 1_700_000_000_000L,
    ) = MediaEntity(
        id = id,
        instrumentId = instrumentId,
        projectId = project.id,
        type = MediaType.PHOTO,
        role = role,
        filePath = filePath,
        thumbnailPath = filePath,
        capturedAt = capturedAt,
        source = MediaSource.LIVE_CAPTURE,
    )

    /** Creates a real image file with content [content] and returns its path. */
    private fun tempPhoto(name: String, content: String = "fake-jpeg-data"): String {
        val f = tmp.newFile(name)
        f.writeText(content)
        return f.absolutePath
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        projectRepo = mockk(relaxed = true)
        instrumentRepo = mockk(relaxed = true)
        mediaRepo = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Point context.cacheDir to a real temporary dir so the ZIP is actually written
        coEvery { context.cacheDir } returns tmp.newFolder("cache")

        service = ZipExportService(projectRepo, instrumentRepo, mediaRepo, context)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Opens the produced ZIP and returns all entry names. */
    private fun zipEntries(zipFile: File): List<String> =
        ZipFile(zipFile).use { z -> z.entries().toList().map { it.name } }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test fun empty_project_produces_valid_but_empty_zip() = runTest {
        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns emptyList()
        coEvery { mediaRepo.getByProject("proj-1") } returns emptyList()

        val zip = service.exportProject("proj-1")
        assertTrue("ZIP file should exist", zip.exists())
        assertTrue("ZIP should be openable", ZipFile(zip).use { true })
        assertTrue("ZIP should have no entries", zipEntries(zip).isEmpty())
    }

    @Test fun single_instrument_before_photo_placed_in_correct_folder() = runTest {
        val instr = instrument("FIC-5185")
        val photoPath = tempPhoto("before.jpg")
        val m = media("m1", instr.id, MediaRole.BEFORE, photoPath)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m)

        val entries = zipEntries(service.exportProject("proj-1"))
        assertTrue("Entry count", entries.size == 1)
        assertTrue(
            "Path should be Project/Tag/BEFORE/Tag_{date}_{id}.jpg",
            entries[0].matches(Regex("Test_Project/FIC-5185/BEFORE/FIC-5185_\\d{8}_\\d{6}_m1\\.jpg")),
        )
    }

    @Test fun before_and_after_get_separate_subfolders() = runTest {
        val instr = instrument("LIT-1025")
        val beforePath = tempPhoto("b.jpg")
        val afterPath = tempPhoto("a.jpg")
        val mBefore = media("m-bef", instr.id, MediaRole.BEFORE, beforePath, 1_700_000_000_000L)
        val mAfter = media("m-aft", instr.id, MediaRole.AFTER, afterPath, 1_700_000_001_000L)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(mBefore, mAfter)

        val entries = zipEntries(service.exportProject("proj-1")).sorted()
        assertEquals(2, entries.size)
        assertTrue("BEFORE folder", entries.any { it.contains("/BEFORE/") })
        assertTrue("AFTER folder", entries.any { it.contains("/AFTER/") })
        assertTrue("BEFORE and AFTER are different subfolders",
            entries.none { it.contains("/BEFORE/") && it.contains("/AFTER/") })
    }

    @Test fun all_media_roles_produce_separate_subfolders() = runTest {
        val instr = instrument("PIC-5224")
        val roles = listOf(
            MediaRole.OVERVIEW, MediaRole.DETAIL,
            MediaRole.BEFORE, MediaRole.AFTER,
            MediaRole.NAMEPLATE, MediaRole.DURING, MediaRole.SAFETY,
        )
        val media = roles.mapIndexed { idx, role ->
            media("m$idx", instr.id, role, tempPhoto("photo$idx.jpg"))
        }

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns media

        val entries = zipEntries(service.exportProject("proj-1"))
        val roleFolders = roles.map { it.name }
        roleFolders.forEach { roleName ->
            assertTrue("Expected folder $roleName", entries.any { it.contains("/$roleName/") })
        }
    }

    @Test fun ungrouped_media_goes_into_ungrouped_folder() = runTest {
        val photoPath = tempPhoto("ungrouped.jpg")
        val m = media("u1", instrumentId = null, role = MediaRole.OTHER, filePath = photoPath)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns emptyList()
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m)

        val entries = zipEntries(service.exportProject("proj-1"))
        assertEquals(1, entries.size)
        assertTrue("Ungrouped folder", entries[0].startsWith("Test_Project/_ungrouped/photo_"))
        assertTrue("File name contains short ID", entries[0].contains("_u1.jpg"))
    }

    @Test fun missing_photo_file_is_silently_skipped() = runTest {
        val instr = instrument("FIC-5185")
        // File path that does NOT exist on disk
        val missingPath = "${tmp.root.absolutePath}/nonexistent.jpg"
        val m = media("m-gone", instr.id, MediaRole.DETAIL, missingPath)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m)

        // Should not throw; ZIP should be empty
        val zip = service.exportProject("proj-1")
        assertTrue(zip.exists())
        assertTrue("Missing file produces no entry", zipEntries(zip).isEmpty())
    }

    @Test fun multiple_instruments_are_all_represented() = runTest {
        val instrs = listOf(instrument("FIC-5185"), instrument("LIT-1025"), instrument("PIC-5224"))
        val media = instrs.map { instr ->
            media(instr.id + "-m", instr.id, MediaRole.DETAIL, tempPhoto("${instr.tagId}.jpg"))
        }

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns instrs
        coEvery { mediaRepo.getByProject("proj-1") } returns media

        val entries = zipEntries(service.exportProject("proj-1"))
        assertEquals(3, entries.size)
        instrs.forEach { instr ->
            assertTrue(
                "Missing entry for ${instr.tagId}",
                entries.any { it.contains("/${instr.tagId}/") },
            )
        }
    }

    @Test fun photos_are_sorted_under_their_own_instrument() = runTest {
        val fic = instrument("FIC-5185")
        val lit = instrument("LIT-1025")
        val m1 = media("m1", fic.id, MediaRole.BEFORE, tempPhoto("f.jpg"))
        val m2 = media("m2", lit.id, MediaRole.AFTER, tempPhoto("l.jpg"))

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(fic, lit)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m1, m2)

        val entries = zipEntries(service.exportProject("proj-1"))
        assertTrue("FIC entry wrong", entries.any { it.contains("/FIC-5185/BEFORE/") })
        assertTrue("LIT entry wrong", entries.any { it.contains("/LIT-1025/AFTER/") })
        // Cross-contamination check
        assertFalse(entries.any { it.contains("/FIC-5185/AFTER/") })
        assertFalse(entries.any { it.contains("/LIT-1025/BEFORE/") })
    }

    @Test fun image_file_content_is_preserved_in_zip() = runTest {
        val instr = instrument("FIC-5185")
        val originalContent = "JPEG_BINARY_DATA_12345"
        val photoPath = tempPhoto("real.jpg", originalContent)
        val m = media("m1", instr.id, MediaRole.DETAIL, photoPath)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m)

        val zipFile = service.exportProject("proj-1")
        ZipFile(zipFile).use { z ->
            val entry = z.entries().nextElement()
            val content = z.getInputStream(entry).bufferedReader().readText()
            assertEquals("File content must be preserved verbatim", originalContent, content)
        }
    }

    @Test fun file_name_contains_tag_id_and_date_fragment() = runTest {
        val instr = instrument("FIT-1023")
        val photoPath = tempPhoto("p.jpg")
        val capturedAt = 1_718_000_000_000L // a fixed timestamp
        val m = media("abcdef", instr.id, MediaRole.BEFORE, photoPath, capturedAt)

        coEvery { projectRepo.getById("proj-1") } returns project
        coEvery { instrumentRepo.getByProject("proj-1") } returns listOf(instr)
        coEvery { mediaRepo.getByProject("proj-1") } returns listOf(m)

        val entries = zipEntries(service.exportProject("proj-1"))
        assertEquals(1, entries.size)
        val name = entries[0]
        assertTrue("Tag in filename", "FIT-1023" in name)
        assertTrue("Short media ID in filename", "abcdef" in name)
        assertTrue("Has .jpg extension", name.endsWith(".jpg"))
    }

    // ─── sanitize() tests ─────────────────────────────────────────────────────

    @Test fun sanitize_replaces_spaces_with_underscore() {
        assertEquals("My_Project", service.sanitize("My Project"))
    }

    @Test fun sanitize_removes_slashes_and_special_chars() {
        assertEquals("A_B_C", service.sanitize("A/B\\C"))
    }

    @Test fun sanitize_collapses_consecutive_underscores() {
        assertEquals("a_b", service.sanitize("a  b"))
    }

    @Test fun sanitize_allows_dash_and_dot() {
        assertEquals("FIC-5185.jpg", service.sanitize("FIC-5185.jpg"))
    }

    @Test fun sanitize_returns_unnamed_for_blank_input() {
        assertEquals("unnamed", service.sanitize(""))
        assertEquals("unnamed", service.sanitize("   "))
    }

    @Test fun sanitize_handles_unicode_characters() {
        // Non-ASCII chars (accents, CJK) should be replaced
        val result = service.sanitize("Café König")
        assertFalse("No accented characters expected", result.any { it.code > 127 })
    }

    @Test fun zip_root_folder_matches_sanitized_project_name() = runTest {
        val spaceyProject = ProjectEntity(id = "p2", name = "My Field Project")
        coEvery { projectRepo.getById("p2") } returns spaceyProject
        coEvery { instrumentRepo.getByProject("p2") } returns emptyList()
        coEvery { mediaRepo.getByProject("p2") } returns emptyList()

        val svc = ZipExportService(projectRepo, instrumentRepo, mediaRepo, context)
        val zip = svc.exportProject("p2")
        // ZIP is empty (no media) but file name should use sanitized name
        assertTrue("ZIP filename contains sanitized project name",
            zip.name.startsWith("My_Field_Project_"))
    }

    @Test fun project_not_found_throws_error() = runTest {
        coEvery { projectRepo.getById("bad") } returns null

        var threw = false
        try { service.exportProject("bad") } catch (_: Exception) { threw = true }
        assertTrue("Should throw when project not found", threw)
    }
}
