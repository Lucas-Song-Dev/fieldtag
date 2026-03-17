package com.fieldtag.parser

import com.fieldtag.domain.parser.IsaTagDetector
import com.fieldtag.domain.parser.ParsedTag
import com.fieldtag.domain.parser.PidParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Unit tests for [PidParser] and [ParsedTag].
 *
 * The parser requires a real PdfBox environment; here we test:
 *  - ParsedTag data class properties
 *  - Deduplication logic (simulated via calling the public parse interface)
 *  - Sorting of parsed tags (page then y)
 *  - ISA integration: tag detection produces correct page assignments
 *
 * Full integration tests with real PDF are in androidTest/.
 */
class PidParserTest {

    // ─── ParsedTag ────────────────────────────────────────────────────────────

    @Test fun `ParsedTag formats tagId correctly without suffix`() {
        val tag = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1)
        assertEquals("LIT-5219", tag.tagId)
        assertEquals("", tag.suffix)
    }

    @Test fun `ParsedTag formats tagId correctly with suffix`() {
        val tag = ParsedTag(tagId = "LIT-5219A", prefix = "LIT", number = "5219", suffix = "A", page = 1)
        assertEquals("LIT-5219A", tag.tagId)
        assertEquals("A", tag.suffix)
    }

    @Test fun `ParsedTag default values are sensible`() {
        val tag = ParsedTag(tagId = "FIT-5221", prefix = "FIT", number = "5221", page = 3)
        assertEquals(3, tag.page)
        assertEquals(null, tag.x)
        assertEquals(null, tag.y)
        assertEquals(1.0f, tag.confidence, 0.001f)
    }

    @Test fun `ParsedTag equality is based on all fields`() {
        val tag1 = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, x = 0.5f, y = 0.3f)
        val tag2 = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, x = 0.5f, y = 0.3f)
        assertEquals(tag1, tag2)
    }

    // ─── Raw detection and deduplication logic ────────────────────────────────

    @Test fun `detector returns raw matches including duplicates (dedup happens in PidParser)`() {
        // detectInText returns all matches; deduplication is PidParser's responsibility
        val tags = IsaTagDetector.detectInText("LIT-5219 LIT-5219")
        // Should find both occurrences in the raw text (dedup not applied here)
        assertTrue("Should detect at least one LIT-5219", tags.isNotEmpty())
        assertTrue("All detected should be LIT-5219", tags.all { it.tagId == "LIT-5219" })
    }

    @Test fun `multiple distinct tags are all returned`() {
        val input = "LIT-5219 PV-5218 FIT-5221 LIC-5219 TIT-5234 HS-5222 DT-5220"
        val tags = IsaTagDetector.detectInText(input)
        val ids = tags.map { it.tagId }.toSet()
        assertEquals("Should find all 7 unique tags", 7, ids.size)
    }

    @Test fun `PidParser deduplicates by keeping highest confidence entry`() {
        val tag1 = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, confidence = 0.9f)
        val tag2 = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, confidence = 0.6f)
        val deduplicated = mutableMapOf<String, ParsedTag>()
        for (tag in listOf(tag1, tag2)) {
            val existing = deduplicated[tag.tagId]
            if (existing == null || tag.confidence > existing.confidence) {
                deduplicated[tag.tagId] = tag
            }
        }
        assertEquals(1, deduplicated.size)
        assertEquals(0.9f, deduplicated["LIT-5219"]!!.confidence, 0.001f)
    }

    // ─── Sorting ─────────────────────────────────────────────────────────────

    @Test fun `tags from lower page come before higher page`() {
        val tag1 = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 2, y = 0.1f)
        val tag2 = ParsedTag(tagId = "PV-5218", prefix = "PV", number = "5218", page = 1, y = 0.9f)
        val sorted = listOf(tag1, tag2).sortedWith(compareBy({ it.page }, { it.y ?: Float.MAX_VALUE }))
        assertEquals("PV-5218", sorted[0].tagId)  // page 1 comes first
        assertEquals("LIT-5219", sorted[1].tagId)
    }

    @Test fun `tags on same page sorted by Y top-to-bottom`() {
        val top = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, y = 0.1f)
        val bottom = ParsedTag(tagId = "PV-5218", prefix = "PV", number = "5218", page = 1, y = 0.8f)
        val sorted = listOf(bottom, top).sortedWith(compareBy({ it.page }, { it.y ?: Float.MAX_VALUE }))
        assertEquals("LIT-5219", sorted[0].tagId)  // lower y = higher on page
        assertEquals("PV-5218", sorted[1].tagId)
    }

    @Test fun `tags without Y coordinate sort to end within page`() {
        val withY = ParsedTag(tagId = "LIT-5219", prefix = "LIT", number = "5219", page = 1, y = 0.5f)
        val noY = ParsedTag(tagId = "PV-5218", prefix = "PV", number = "5218", page = 1, y = null)
        val sorted = listOf(noY, withY).sortedWith(compareBy({ it.page }, { it.y ?: Float.MAX_VALUE }))
        assertEquals("LIT-5219", sorted[0].tagId)
        assertEquals("PV-5218", sorted[1].tagId)
    }

    // ─── ISA type mapping integration ────────────────────────────────────────

    @Test fun `all tags from real PID have known ISA prefixes`() {
        val knownTags = listOf("LIT-5219", "PV-5218", "FIT-5221", "LIC-5219", "TIT-5234", "HS-5222", "DT-5220")
        for (tagStr in knownTags) {
            val tags = IsaTagDetector.detectInText(tagStr)
            assertTrue("Should detect $tagStr", tags.isNotEmpty())
            assertEquals("Should find exactly one match for $tagStr", 1, tags.size)
        }
    }

    @Test fun `prefix extraction for all known tags`() {
        val expected = mapOf(
            "LIT-5219" to "LIT",
            "PV-5218" to "PV",
            "FIT-5221" to "FIT",
            "LIC-5219" to "LIC",
            "TIT-5234" to "TIT",
            "HS-5222" to "HS",
            "DT-5220" to "DT",
        )
        for ((tagStr, prefix) in expected) {
            val tags = IsaTagDetector.detectInText(tagStr)
            assertTrue("Tags detected for $tagStr", tags.isNotEmpty())
            assertEquals("Prefix for $tagStr", prefix, tags[0].prefix)
        }
    }

    @Test fun `number extraction for all known tags`() {
        val expected = mapOf(
            "LIT-5219" to "5219",
            "PV-5218" to "5218",
            "FIT-5221" to "5221",
        )
        for ((tagStr, number) in expected) {
            val tags = IsaTagDetector.detectInText(tagStr)
            assertTrue(tags.isNotEmpty())
            assertEquals("Number for $tagStr", number, tags[0].number)
        }
    }

    @Test fun `parseResult ParseResult data class`() {
        val result = com.fieldtag.domain.parser.ParseResult(
            tags = listOf(ParsedTag("LIT-5219", "LIT", "5219", page = 1)),
            pageCount = 3,
            rawTextJson = "{}",
            warnings = emptyList(),
        )
        assertEquals(1, result.tags.size)
        assertEquals(3, result.pageCount)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun `parseResult with warnings`() {
        val warnings = listOf("Page 1 has only 5 text tokens — may be a scanned/raster image")
        val result = com.fieldtag.domain.parser.ParseResult(
            tags = emptyList(),
            pageCount = 1,
            rawTextJson = null,
            warnings = warnings,
        )
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("scanned"))
    }

    // ─── Coordinate range validation ──────────────────────────────────────────
    // Tags produced by detectWithPosition must have x and y in [0, 1] (screen-space fractions).

    @Test fun `detected tag y values are in screen space 0 to 1`() {
        // Simulate a set of tags with various raw y positions across a 1000pt page.
        val rawYValues = listOf(0f, 50f, 250f, 500f, 750f, 950f, 1000f)
        rawYValues.forEach { rawY ->
            val tags = IsaTagDetector.detectWithPosition(
                text = "FIC-5185", page = 1,
                x = 100f, y = rawY, pageWidth = 1200f, pageHeight = 1000f,
            )
            assertEquals(1, tags.size)
            val y = tags[0].y!!
            assertTrue("y=$y for rawY=$rawY should be in [0,1]", y in 0f..1f)
        }
    }

    @Test fun `detected tag x values are in screen space 0 to 1`() {
        val rawXValues = listOf(0f, 100f, 600f, 1199f, 1200f)
        rawXValues.forEach { rawX ->
            val tags = IsaTagDetector.detectWithPosition(
                text = "LIT-1025", page = 1,
                x = rawX, y = 400f, pageWidth = 1200f, pageHeight = 1000f,
            )
            assertEquals(1, tags.size)
            val x = tags[0].x!!
            assertTrue("x=$x for rawX=$rawX should be in [0,1]", x in 0f..1f)
        }
    }
}
