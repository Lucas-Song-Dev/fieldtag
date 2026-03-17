package com.fieldtag.parser

import com.fieldtag.domain.parser.IsaTagDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IsaTagDetector].
 *
 * Tests cover:
 *  - All ISA function prefix categories (flow, level, pressure, temperature, etc.)
 *  - Tags with hyphens, spaces, and no separator
 *  - Tags from the real 22363-EE-SKT-03 P&ID (LIT-5219, PV-5218, FIT-5221, etc.)
 *  - False positive rejection (pipe specs, hand valves, drawing refs, equipment codes)
 *  - Suffix letter handling (LIT-5219A)
 *  - Edge cases (empty string, whitespace, numbers-only)
 *  - Confidence scoring for known vs unknown prefixes
 *  - instrumentTypeForPrefix lookup
 */
class IsaTagDetectorTest {

    // ─── Valid ISA tags ─────────────────────────────────────────────────────────

    @Test fun `detects LIT with hyphen separator`() {
        val tags = IsaTagDetector.detectInText("LIT-5219")
        assertEquals(1, tags.size)
        assertEquals("LIT-5219", tags[0].tagId)
        assertEquals("LIT", tags[0].prefix)
        assertEquals("5219", tags[0].number)
        assertEquals("", tags[0].suffix)
    }

    @Test fun `detects PV with hyphen separator`() {
        val tags = IsaTagDetector.detectInText("PV-5218")
        assertEquals(1, tags.size)
        assertEquals("PV-5218", tags[0].tagId)
        assertEquals("PV", tags[0].prefix)
    }

    @Test fun `detects FIT with hyphen separator`() {
        val tags = IsaTagDetector.detectInText("FIT-5221")
        assertEquals(1, tags.size)
        assertEquals("FIT-5221", tags[0].tagId)
    }

    @Test fun `detects LIC from real PID`() {
        val tags = IsaTagDetector.detectInText("LIC-5219")
        assertEquals(1, tags.size)
        assertEquals("LIC-5219", tags[0].tagId)
    }

    @Test fun `detects TIT from real PID`() {
        val tags = IsaTagDetector.detectInText("TIT-5234")
        assertEquals(1, tags.size)
        assertEquals("TIT-5234", tags[0].tagId)
    }

    @Test fun `detects HS from real PID`() {
        val tags = IsaTagDetector.detectInText("HS-5222")
        assertEquals(1, tags.size)
        assertEquals("HS-5222", tags[0].tagId)
    }

    @Test fun `detects DT from real PID`() {
        val tags = IsaTagDetector.detectInText("DT-5220")
        assertEquals(1, tags.size)
        assertEquals("DT-5220", tags[0].tagId)
    }

    @Test fun `detects tag with space separator`() {
        val tags = IsaTagDetector.detectInText("LIT 5219")
        assertEquals(1, tags.size)
        assertEquals("LIT-5219", tags[0].tagId)
    }

    @Test fun `detects tag without separator`() {
        val tags = IsaTagDetector.detectInText("FIT5221")
        assertEquals(1, tags.size)
        assertEquals("FIT-5221", tags[0].tagId)
    }

    @Test fun `detects tag with suffix letter`() {
        val tags = IsaTagDetector.detectInText("LIT-5219A")
        assertEquals(1, tags.size)
        assertEquals("LIT-5219A", tags[0].tagId)
        assertEquals("A", tags[0].suffix)
    }

    @Test fun `detects tag with suffix B`() {
        val tags = IsaTagDetector.detectInText("FIT-5221B")
        assertEquals(1, tags.size)
        assertEquals("FIT-5221B", tags[0].tagId)
    }

    @Test fun `detects flow indicator FI`() {
        val tags = IsaTagDetector.detectInText("FI-1001")
        assertTrue(tags.isNotEmpty())
        assertEquals("FI-1001", tags[0].tagId)
    }

    @Test fun `detects flow controller FIC`() {
        val tags = IsaTagDetector.detectInText("FIC-1234")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects level indicator LI`() {
        val tags = IsaTagDetector.detectInText("LI-2001")
        assertTrue(tags.isNotEmpty())
        assertEquals("LI-2001", tags[0].tagId)
    }

    @Test fun `detects pressure indicator PI`() {
        val tags = IsaTagDetector.detectInText("PI-3001")
        assertTrue(tags.isNotEmpty())
        assertEquals("PI-3001", tags[0].tagId)
    }

    @Test fun `detects pressure transmitter PIT`() {
        val tags = IsaTagDetector.detectInText("PIT-3002")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects temperature indicator TI`() {
        val tags = IsaTagDetector.detectInText("TI-4001")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects temperature element TE`() {
        val tags = IsaTagDetector.detectInText("TE-4002")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects analyser indicator AI`() {
        val tags = IsaTagDetector.detectInText("AI-5001")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects pressure safety valve PSV`() {
        val tags = IsaTagDetector.detectInText("PSV-3100")
        assertTrue(tags.isNotEmpty())
    }

    @Test fun `detects multiple tags in a line`() {
        val tags = IsaTagDetector.detectInText("LIT-5219 PV-5218 FIT-5221")
        assertTrue(tags.size >= 3)
        val ids = tags.map { it.tagId }.toSet()
        assertTrue("LIT-5219" in ids)
        assertTrue("PV-5218" in ids)
        assertTrue("FIT-5221" in ids)
    }

    @Test fun `detects tags mixed with pipe labels`() {
        val tags = IsaTagDetector.detectInText("4-BL-245-GBN-15 LIT-5219 signal")
        val ids = tags.map { it.tagId }
        assertTrue("LIT-5219" in ids)
    }

    // ─── False positive rejection ────────────────────────────────────────────

    @Test fun `rejects hand valve V-25`() {
        val tags = IsaTagDetector.detectInText("V-25")
        assertTrue("V-25 should be rejected as hand valve", tags.none { it.tagId == "V-25" })
    }

    @Test fun `rejects hand valve V-49`() {
        val tags = IsaTagDetector.detectInText("V-49")
        assertTrue(tags.none { it.tagId == "V-49" })
    }

    @Test fun `rejects pipe spec 4x3`() {
        val tags = IsaTagDetector.detectInText("4x3")
        assertTrue(tags.isEmpty())
    }

    @Test fun `rejects pipe spec 6X8`() {
        val tags = IsaTagDetector.detectInText("6X8")
        assertTrue(tags.isEmpty())
    }

    @Test fun `rejects pipe class starting with digit 6-BL-245`() {
        val tags = IsaTagDetector.detectInText("6-BL-245")
        assertTrue(tags.none { it.tagId.contains("BL-245") })
    }

    @Test fun `rejects drawing reference P2`() {
        val tags = IsaTagDetector.detectInText("P2")
        assertTrue(tags.none { it.tagId == "P-2" || it.tagId == "P2" })
    }

    @Test fun `rejects short drawing reference S1`() {
        val tags = IsaTagDetector.detectInText("S1")
        assertTrue(tags.isEmpty() || tags.none { it.number == "1" && it.prefix == "S" })
    }

    @Test fun `returns empty for blank string`() {
        val tags = IsaTagDetector.detectInText("")
        assertTrue(tags.isEmpty())
    }

    @Test fun `returns empty for whitespace only`() {
        val tags = IsaTagDetector.detectInText("   \t\n  ")
        assertTrue(tags.isEmpty())
    }

    @Test fun `returns empty for numbers only`() {
        val tags = IsaTagDetector.detectInText("12345 6789")
        assertTrue(tags.isEmpty())
    }

    @Test fun `does not detect lowercase letters as prefix`() {
        val tags = IsaTagDetector.detectInText("lit-5219")
        assertTrue("Lowercase prefixes should not match", tags.none { it.prefix == "lit" })
    }

    // ─── Confidence scoring ──────────────────────────────────────────────────

    @Test fun `known ISA prefix has high confidence`() {
        val tags = IsaTagDetector.detectInText("LIT-5219")
        assertTrue(tags[0].confidence > 0.7f)
    }

    @Test fun `confidence is between 0 and 1`() {
        val tags = IsaTagDetector.detectInText("FIT-5221 LIC-5219 TIT-5234")
        tags.forEach { tag ->
            assertTrue("Confidence ${tag.confidence} out of range for ${tag.tagId}", tag.confidence in 0f..1f)
        }
    }

    // ─── instrumentTypeForPrefix ─────────────────────────────────────────────

    @Test fun `LIT prefix returns Level Indicating Transmitter`() {
        assertEquals("Level Indicating Transmitter", IsaTagDetector.instrumentTypeForPrefix("LIT"))
    }

    @Test fun `PV prefix returns Pressure Control Valve`() {
        assertEquals("Pressure Control Valve", IsaTagDetector.instrumentTypeForPrefix("PV"))
    }

    @Test fun `FIT prefix returns Flow Indicating Transmitter`() {
        assertEquals("Flow Indicating Transmitter", IsaTagDetector.instrumentTypeForPrefix("FIT"))
    }

    @Test fun `unknown prefix returns null`() {
        assertNull(IsaTagDetector.instrumentTypeForPrefix("ZZZ"))
    }

    @Test fun `HS prefix returns Hand Switch`() {
        assertEquals("Hand Switch", IsaTagDetector.instrumentTypeForPrefix("HS"))
    }

    // ─── Position tracking ────────────────────────────────────────────────────

    @Test fun `detectWithPosition sets normalised coordinates`() {
        val tags = IsaTagDetector.detectWithPosition(
            text = "LIT-5219",
            page = 2,
            x = 250f,
            y = 400f,
            pageWidth = 1000f,
            pageHeight = 800f,
        )
        assertEquals(1, tags.size)
        assertEquals(2, tags[0].page)
        assertEquals(0.25f, tags[0].x!!, 0.01f)
        assertEquals(0.5f, tags[0].y!!, 0.01f)
    }

    @Test fun `detectWithPosition handles zero page dimensions gracefully`() {
        val tags = IsaTagDetector.detectWithPosition(
            text = "LIT-5219",
            page = 1,
            x = 100f,
            y = 100f,
            pageWidth = 0f,
            pageHeight = 0f,
        )
        // Should not crash; x/y may be null
        assertEquals(1, tags.size)
        assertNull(tags[0].x)
        assertNull(tags[0].y)
    }

    // ─── Coordinate normalisation — y-axis inversion ─────────────────────────
    // PdfBox yDirAdj is measured from the BOTTOM of the page (low value = near bottom).
    // After normalisation it must be inverted so y=0.0 means top and y=1.0 means bottom,
    // matching Android bitmap / screen coordinate conventions.

    @Test fun `detectWithPosition y near bottom of page maps to near 1`() {
        // y=100 on an 800-pt page is near the bottom in PDF coords → near 1.0 in screen coords
        val tags = IsaTagDetector.detectWithPosition(
            text = "LIT-5219", page = 1,
            x = 400f, y = 100f, pageWidth = 800f, pageHeight = 800f,
        )
        assertEquals(1, tags.size)
        // expected: 1 - (100/800) = 0.875
        assertEquals(0.875f, tags[0].y!!, 0.01f)
    }

    @Test fun `detectWithPosition y near top of page maps to near 0`() {
        // y=700 on an 800-pt page is near the top in PDF coords → near 0.0 in screen coords
        val tags = IsaTagDetector.detectWithPosition(
            text = "FIT-5221", page = 1,
            x = 200f, y = 700f, pageWidth = 800f, pageHeight = 800f,
        )
        assertEquals(1, tags.size)
        // expected: 1 - (700/800) = 0.125
        assertEquals(0.125f, tags[0].y!!, 0.01f)
    }

    @Test fun `detectWithPosition x is normalised to 0 to 1 range`() {
        val tags = IsaTagDetector.detectWithPosition(
            text = "PIC-5224", page = 1,
            x = 300f, y = 400f, pageWidth = 600f, pageHeight = 800f,
        )
        assertEquals(1, tags.size)
        val x = tags[0].x!!
        assertTrue("x=$x must be in [0,1]", x in 0f..1f)
        assertEquals(0.5f, x, 0.01f)
    }

    @Test fun `detectWithPosition y is normalised to 0 to 1 range`() {
        // Test multiple positions; all resulting y values must be within [0, 1]
        listOf(0f, 100f, 400f, 700f, 800f).forEach { rawY ->
            val tags = IsaTagDetector.detectWithPosition(
                text = "FIC-5185", page = 1,
                x = 100f, y = rawY, pageWidth = 800f, pageHeight = 800f,
            )
            assertEquals(1, tags.size)
            val y = tags[0].y!!
            assertTrue("y=$y for rawY=$rawY must be in [0,1]", y in 0f..1f)
        }
    }
}
