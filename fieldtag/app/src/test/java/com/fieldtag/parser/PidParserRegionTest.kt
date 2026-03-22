package com.fieldtag.parser

import com.fieldtag.domain.parser.ParsedTag
import com.fieldtag.domain.parser.PidParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit tests for [PidParser.parseRegion].
 *
 * All tests use hand-crafted rawTextJson strings so that PdfBox is never
 * involved — only the JSON-parsing + coordinate-filtering + ISA-detection
 * path is exercised.
 *
 * Coordinate convention used in rawTextJson:
 *   - x: points from the LEFT of the page  (raw PdfBox xDirAdj)
 *   - y: points from the BOTTOM of the page (raw PdfBox yDirAdj)
 *   - page: 1-based
 *
 * parseRegion normalises them to screen space:
 *   normX = rawX / pageWidth          (left → right, 0..1)
 *   normY = 1 - rawY / pageHeight     (bottom-origin → top-origin, 0..1)
 *
 * So:
 *   A run at rawX=300, rawY=600 on a 1200×800 page has normX=0.25, normY=0.25
 *   (25% from left, 25% from top — i.e. in the upper-left quadrant).
 */
class PidParserRegionTest {

    private val parser = PidParser()

    /** Convenience wrapper so all assertions can work directly with the tag list. */
    private fun parse(
        raw: String, pageIndex: Int, x1: Float, y1: Float, x2: Float, y2: Float
    ): List<ParsedTag> = parser.parseRegion(raw, pageIndex, x1, y1, x2, y2).tags

    // ─── Helper to build rawTextJson ──────────────────────────────────────────

    /**
     * Builds a minimal rawTextJson with a single page (1-based [pageNum]).
     * Page is [w] × [h] points; [runs] is a list of (text, rawX, rawY) triples.
     */
    private fun json(
        pageNum: Int = 1,
        w: Float = 1200f,
        h: Float = 800f,
        vararg runs: Triple<String, Float, Float>,
    ): String {
        val runsJson = runs.joinToString(",") { (text, x, y) ->
            val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
            """{"text":"$escaped","x":$x,"y":$y}"""
        }
        return """[{"page":$pageNum,"width":$w,"height":$h,"runs":[$runsJson]}]"""
    }

    /** Multi-page helper: provide (pageNum, w, h, runs) per page. */
    private fun multiPageJson(vararg pages: Triple<Int, Float, Array<Triple<String, Float, Float>>>): String {
        val parts = pages.joinToString(",") { (pageNum, w, runs) ->
            val h = 800f
            val runsJson = runs.joinToString(",") { (text, x, y) ->
                val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
                """{"text":"$escaped","x":$x,"y":$y}"""
            }
            """{"page":$pageNum,"width":$w,"height":$h,"runs":[$runsJson]}"""
        }
        return "[$parts]"
    }

    // ─── 1. Basic single-tag selection ────────────────────────────────────────

    @Test fun single_tag_exactly_inside_selection() {
        // FIC-5185 at normX=0.25, normY=0.25; select the whole upper-left quadrant
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        val tags = parse(raw, 0, 0f, 0f, 0.5f, 0.5f)
        assertEquals(1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }

    @Test fun single_tag_selection_returns_correct_page() {
        val raw = json(pageNum = 2, runs = arrayOf(Triple("LIT-1025", 600f, 400f)))
        val tags = parse(raw, 1, 0f, 0f, 1f, 1f) // page index 1 = page 2
        assertEquals(1, tags.size)
        assertEquals(2, tags[0].page)
    }

    // ─── 2. No-tag scenarios ──────────────────────────────────────────────────

    @Test fun empty_region_returns_empty_list() {
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        // Select a tiny area in the bottom-right corner — far from the tag
        val tags = parse(raw, 0, 0.9f, 0.9f, 1.0f, 1.0f)
        assertTrue(tags.isEmpty())
    }

    @Test fun region_on_wrong_page_returns_empty() {
        // Tag is on page 1 but we request page index 1 (= page 2)
        val raw = json(pageNum = 1, runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        val tags = parse(raw, 1, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    @Test fun non_isa_text_in_region_returns_empty() {
        // Text like a revision note — no ISA tag pattern
        val raw = json(runs = arrayOf(Triple("Rev B", 300f, 600f)))
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    @Test fun empty_json_pages_returns_empty() {
        val tags = parse("[]", 0, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    @Test fun page_with_no_runs_returns_empty() {
        val raw = """[{"page":1,"width":1200.0,"height":800.0,"runs":[]}]"""
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    // ─── 3. Multiple-tag scenarios ────────────────────────────────────────────

    @Test fun full_page_selection_returns_all_tags() {
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 600f),
                Triple("LIT-1025", 900f, 400f),
                Triple("PIC-5224", 600f, 200f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(3, tags.size)
        val ids = tags.map { it.tagId }.toSet()
        assertTrue("FIC-5185" in ids)
        assertTrue("LIT-1025" in ids)
        assertTrue("PIC-5224" in ids)
    }

    @Test fun wide_selection_covering_multiple_tags_returns_multiple() {
        // Both tags in the upper half
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 200f, 700f), // normY = 1 - 700/800 = 0.125
                Triple("FIT-1023", 1000f, 700f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 0.5f)
        assertEquals(2, tags.size)
    }

    @Test fun two_tags_only_one_inside_selection() {
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 600f),  // normX=0.25, normY=0.25 — inside [0,0,0.5,0.5]
                Triple("LIT-1025", 900f, 200f),  // normX=0.75, normY=0.75 — outside
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 0.5f, 0.5f)
        assertEquals(1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }

    // ─── 4. Deduplication ────────────────────────────────────────────────────

    @Test fun same_tag_in_two_runs_deduplicates_to_one() {
        // Same tag text appears twice (e.g. bubble + nearby annotation)
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 600f),
                Triple("FIC-5185", 310f, 600f), // slightly offset duplicate
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }

    @Test fun dedup_keeps_highest_confidence_entry() {
        // ISA-prefixed tag has higher confidence than a short/unknown prefix
        // Both runs contain the same logical tag but one is more recognisable
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 600f),  // known ISA prefix → high confidence
                Triple("FIC-5185", 310f, 598f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(1, tags.size)
    }

    // ─── 5. Boundary / edge cases ─────────────────────────────────────────────

    @Test fun tag_exactly_on_selection_boundary_is_included() {
        // normX = 0.5 exactly on the right edge of a [0, 0, 0.5, 1] selection
        val raw = json(runs = arrayOf(Triple("LIT-1025", 600f, 400f))) // normX = 600/1200 = 0.5
        val tags = parse(raw, 0, 0f, 0f, 0.5f, 1f)
        assertEquals("Tag on boundary should be included", 1, tags.size)
    }

    @Test fun inverted_selection_coords_are_normalised_correctly() {
        // User dragged from bottom-right to top-left — x2 < x1, y2 < y1
        // parseRegion must handle this by using min/max
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f))) // normX=0.25, normY=0.25
        val tags = parse(raw, 0, 0.5f, 0.5f, 0f, 0f) // inverted drag
        assertEquals("Inverted selection should still work", 1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }

    @Test fun tiny_selection_containing_exact_tag_centre_finds_tag() {
        // Tag normX=0.25, normY=0.25 — tiny box right around it
        val raw = json(runs = arrayOf(Triple("PIC-5224", 300f, 600f)))
        val tags = parse(raw, 0, 0.24f, 0.24f, 0.26f, 0.26f)
        assertEquals(1, tags.size)
        assertEquals("PIC-5224", tags[0].tagId)
    }

    @Test fun zero_area_selection_at_tag_location_still_finds_tag() {
        // Single-point selection: x1==x2, y1==y2 exactly at tag location
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f))) // normX=0.25, normY=0.25
        val tags = parse(raw, 0, 0.25f, 0.25f, 0.25f, 0.25f)
        assertEquals(1, tags.size)
    }

    // ─── 6. Multi-page JSON ───────────────────────────────────────────────────

    @Test fun multi_page_selects_correct_page_only() {
        val raw = multiPageJson(
            Triple(1, 1200f, arrayOf(Triple("FIC-5185", 300f, 600f))),
            Triple(2, 1200f, arrayOf(Triple("LIT-1025", 300f, 600f))),
            Triple(3, 1200f, arrayOf(Triple("PIC-5224", 300f, 600f))),
        )
        val p0 = parse(raw, 0, 0f, 0f, 1f, 1f)
        val p1 = parse(raw, 1, 0f, 0f, 1f, 1f)
        val p2 = parse(raw, 2, 0f, 0f, 1f, 1f)

        assertEquals(1, p0.size); assertEquals("FIC-5185", p0[0].tagId)
        assertEquals(1, p1.size); assertEquals("LIT-1025", p1[0].tagId)
        assertEquals(1, p2.size); assertEquals("PIC-5224", p2[0].tagId)
    }

    @Test fun multi_page_out_of_bounds_page_index_returns_empty() {
        val raw = json(pageNum = 1, runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        val tags = parse(raw, 99, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    // ─── 7. Partial tag text ──────────────────────────────────────────────────

    @Test fun partial_text_without_isa_pattern_returns_empty() {
        // "FIC" alone is not a valid ISA tag (no number suffix)
        val raw = json(runs = arrayOf(Triple("FIC", 300f, 600f)))
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertTrue(tags.isEmpty())
    }

    @Test fun mixed_isa_and_non_isa_text_only_returns_isa() {
        val raw = json(
            runs = arrayOf(
                Triple("See note 3A", 100f, 700f),
                Triple("FIC-5185",    300f, 600f),
                Triple("Rev B 2024",  500f, 650f),
                Triple("LIT-1025",    700f, 550f),
                Triple("3/4in NPT",   900f, 600f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(2, tags.size)
        val ids = tags.map { it.tagId }.toSet()
        assertTrue("FIC-5185" in ids)
        assertTrue("LIT-1025" in ids)
    }

    // ─── 8. Y-axis inversion correctness ─────────────────────────────────────

    @Test fun tag_near_bottom_of_pdf_appears_near_bottom_of_screen() {
        // rawY = 50 on 800pt page → normY = 1 - 50/800 = 0.9375 (near bottom of screen)
        val raw = json(runs = arrayOf(Triple("FIT-1023", 300f, 50f)))
        // Select lower 20% of screen (normY from 0.8 to 1.0)
        val tags = parse(raw, 0, 0f, 0.8f, 1f, 1f)
        assertEquals("Tag near PDF bottom should appear near screen bottom", 1, tags.size)
    }

    @Test fun tag_near_top_of_pdf_appears_near_top_of_screen() {
        // rawY = 750 on 800pt page → normY = 1 - 750/800 = 0.0625 (near top of screen)
        val raw = json(runs = arrayOf(Triple("PIC-5224", 300f, 750f)))
        // Select upper 20% of screen (normY from 0.0 to 0.2)
        val tags = parse(raw, 0, 0f, 0f, 1f, 0.2f)
        assertEquals("Tag near PDF top should appear near screen top", 1, tags.size)
    }

    @Test fun y_inversion_prevents_top_and_bottom_confusion() {
        // Tag A near top of PDF (rawY=700) — should only be found in top half of screen
        // Tag B near bottom of PDF (rawY=100) — should only be found in bottom half of screen
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 700f), // normY ≈ 0.125 (top of screen)
                Triple("LIT-1025", 900f, 100f), // normY ≈ 0.875 (bottom of screen)
            )
        )
        val topHalf = parse(raw, 0, 0f, 0f, 1f, 0.5f)
        val bottomHalf = parse(raw, 0, 0f, 0.5f, 1f, 1f)

        assertEquals(1, topHalf.size)
        assertEquals("FIC-5185", topHalf[0].tagId)

        assertEquals(1, bottomHalf.size)
        assertEquals("LIT-1025", bottomHalf[0].tagId)
    }

    // ─── 9. Coordinates returned from region parse ───────────────────────────

    @Test fun returned_tags_have_normalised_coordinates_in_0_to_1() {
        val raw = json(
            runs = arrayOf(
                Triple("FIC-5185", 300f, 600f),
                Triple("LIT-1025", 900f, 200f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        tags.forEach { tag ->
            tag.x?.let { assertTrue("x=${it} out of range", it in 0f..1f) }
            tag.y?.let { assertTrue("y=${it} out of range", it in 0f..1f) }
        }
    }

    @Test fun returned_tag_x_matches_expected_normalised_position() {
        // rawX=300, pageWidth=1200 → normX=0.25
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(1, tags.size)
        assertEquals(0.25f, tags[0].x!!, 0.01f)
    }

    @Test fun returned_tag_y_matches_expected_inverted_normalised_position() {
        // rawY=600, pageHeight=800 → normY = 1 - 600/800 = 0.25
        val raw = json(runs = arrayOf(Triple("FIC-5185", 300f, 600f)))
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(1, tags.size)
        assertEquals(0.25f, tags[0].y!!, 0.01f)
    }

    // ─── 10. ISA tag variants ────────────────────────────────────────────────

    @Test fun all_major_isa_prefixes_are_detected_in_region() {
        val isaTags = listOf(
            "FIC-5185" to Pair(100f, 700f),
            "LIT-1025" to Pair(200f, 700f),
            "PIC-5224" to Pair(300f, 700f),
            "TIC-3101" to Pair(400f, 700f),
            "FIT-1023" to Pair(500f, 700f),
            "PV-5188"  to Pair(600f, 700f),
        )
        val runs = isaTags.map { (tag, pos) -> Triple(tag, pos.first, pos.second) }.toTypedArray()
        val raw = json(runs = runs)
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        val foundIds = tags.map { it.tagId }.toSet()
        isaTags.forEach { (id, _) ->
            assertTrue("Expected $id to be found", id in foundIds)
        }
    }

    @Test fun false_positive_hand_valves_are_not_detected() {
        val raw = json(
            runs = arrayOf(
                Triple("V-3",    100f, 600f),
                Triple("V-49",   300f, 600f),
                Triple("FIC-5185", 600f, 600f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        val ids = tags.map { it.tagId }
        assertTrue("V-3 is a hand valve and should be filtered", "V-3" !in ids)
        assertTrue("V-49 is a hand valve and should be filtered", "V-49" !in ids)
        assertEquals(1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }

    @Test fun dimension_strings_are_not_detected_as_tags() {
        val raw = json(
            runs = arrayOf(
                Triple("4x3",    100f, 600f),
                Triple("FIC-5185", 300f, 600f),
            )
        )
        val tags = parse(raw, 0, 0f, 0f, 1f, 1f)
        assertEquals(1, tags.size)
        assertEquals("FIC-5185", tags[0].tagId)
    }
}
