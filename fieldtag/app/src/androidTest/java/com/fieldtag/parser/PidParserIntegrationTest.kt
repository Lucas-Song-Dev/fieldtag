package com.fieldtag.parser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldtag.domain.parser.PidParser
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [PidParser] that run against the real P&ID PDF:
 * "22363-EE-SKT-03 r1 EV2 P&ID Audit Package.pdf"
 *
 * These tests verify that the full PdfBox + ISA regex pipeline works end-to-end
 * on a real industrial P&ID and finds the expected instrument tags.
 *
 * Tags confirmed present in the text-extractable pages of this P&ID:
 *   PIC-5224, FIC-5185, FIC-5186, PV-5188, LIT-1025, FIT-1023
 * Note: some drawing pages are raster-only and yield no text; tags on those
 * pages (e.g. LIT-5219) are only visible to an OCR pipeline, not PdfBox text extraction.
 */
@RunWith(AndroidJUnit4::class)
class PidParserIntegrationTest {

    private lateinit var parser: PidParser

    @Before fun setUp() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        parser = PidParser()
    }

    private fun openPdf() =
        InstrumentationRegistry.getInstrumentation().context
            .assets
            .open("22363-EE-SKT-03-PID-Audit-Package.pdf")

    @Test fun parse_real_PID_returns_non_empty_tag_list() {
        val result = openPdf().use { parser.parse(it) }
        assertThat(result.tags).isNotEmpty()
    }

    @Test fun parse_real_PID_finds_PIC_5224() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids).contains("PIC-5224")
    }

    @Test fun parse_real_PID_finds_FIC_5185() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids).contains("FIC-5185")
    }

    @Test fun parse_real_PID_finds_FIC_5186() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids).contains("FIC-5186")
    }

    @Test fun parse_real_PID_finds_LIT_1025() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids).contains("LIT-1025")
    }

    @Test fun parse_real_PID_finds_FIT_1023() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids).contains("FIT-1023")
    }

    @Test fun parse_real_PID_finds_all_known_extractable_tags() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }.toSet()

        // These tags are confirmed present in text-extractable pages of this PDF
        val knownTags = setOf("PIC-5224", "FIC-5185", "FIC-5186", "LIT-1025", "FIT-1023")
        val missing = knownTags - ids

        assertThat(missing).isEmpty()
    }

    @Test fun parse_real_PID_has_non_zero_page_count() {
        val result = openPdf().use { parser.parse(it) }
        assertThat(result.pageCount).isGreaterThan(0)
    }

    @Test fun parse_real_PID_tags_are_sorted_by_page_then_y_position() {
        val result = openPdf().use { parser.parse(it) }
        val tags = result.tags
        for (i in 0 until tags.size - 1) {
            val a = tags[i]
            val b = tags[i + 1]
            if (a.page == b.page && a.y != null && b.y != null) {
                assertThat(a.y).isAtMost(b.y!!)
            } else {
                assertThat(a.page).isAtMost(b.page)
            }
        }
    }

    @Test fun parse_real_PID_tags_have_no_duplicate_tagIds() {
        val result = openPdf().use { parser.parse(it) }
        val ids = result.tags.map { it.tagId }
        assertThat(ids.size).isEqualTo(ids.distinct().size)
    }

    @Test fun parse_real_PID_provides_raw_text_json() {
        val result = openPdf().use { parser.parse(it) }
        assertThat(result.rawTextJson).isNotNull()
        assertThat(result.rawTextJson!!).isNotEmpty()
    }

    @Test fun parse_real_PID_no_false_positive_hand_valves() {
        val result = openPdf().use { parser.parse(it) }
        // V-3, V-4, V-25, V-49 are hand valves that should be filtered
        val ids = result.tags.map { it.tagId }
        assertThat(ids).doesNotContain("V-3")
        assertThat(ids).doesNotContain("V-4")
    }

    // ─── Coordinate range validation ──────────────────────────────────────────

    @Test fun parse_real_PID_all_tag_coordinates_in_unit_range() {
        val result = openPdf().use { parser.parse(it) }
        // All tags that carry position info must have x, y in [0.0, 1.0].
        // A value outside this range means either the y-inversion or the x-normalisation
        // is broken.
        val positioned = result.tags.filter { it.x != null || it.y != null }
        assertThat(positioned).isNotEmpty()
        positioned.forEach { tag ->
            tag.x?.let { x ->
                assertThat(x).isIn(Range.closed(0f, 1f))
            }
            tag.y?.let { y ->
                assertThat(y).isIn(Range.closed(0f, 1f))
            }
        }
    }

    @Test fun parse_real_PID_no_tags_clustered_only_at_y_zero_or_one() {
        val result = openPdf().use { parser.parse(it) }
        val ys = result.tags.mapNotNull { it.y }
        if (ys.isEmpty()) return  // skip if PDF is raster-only (graceful)

        // If y-inversion is working correctly, tags should be spread across the page,
        // not all pinned to 0.0 (top) or 1.0 (bottom).
        val notAtZeroOrOne = ys.count { it > 0.01f && it < 0.99f }
        assertThat(notAtZeroOrOne).isGreaterThan(0)
    }
}
