package com.fieldtag.domain.parser

/**
 * Detects ISA 5.1 instrument tag identifiers from raw text strings.
 *
 * ISA 5.1 format: [A-Z]{1,5} optionally separated by hyphen/space from [0-9]{3,6} with optional suffix letter.
 * Examples: LIT-5219, PV 5218, FIT5221, LIC-5219A
 *
 * False-positive filters applied:
 *  - Strings starting with a digit (pipe specs: 6-BL-245, 4x3)
 *  - Dimension strings: containing 'x' or 'X' between digits
 *  - Single-letter + digit combos that are drawing references (V-25, P2, S1)
 *  - Too short (single letter prefix only: A1, B2)
 *  - Known equipment naming patterns (e.g. 542-10-18)
 *  - Pure hand valve prefixes when only 1 letter: V, K (V-25 is a hand valve, not an instrument)
 */
object IsaTagDetector {

    private val ISA_PATTERN = Regex(
        """(?<![A-Z0-9\-])([A-Z]{1,5})[-\s]?(\d{3,6})([A-Z]?)(?![A-Z0-9\-])"""
    )

    private val KNOWN_ISA_PREFIXES = setOf(
        // Flow
        "FI", "FIT", "FIC", "FE", "FV", "FC", "FF", "FQ", "FQI",
        // Level
        "LI", "LIT", "LIC", "LV", "LC", "LE", "LG",
        // Pressure
        "PI", "PIT", "PIC", "PV", "PT", "PE", "PSV", "PSH", "PSL",
        // Temperature
        "TI", "TIT", "TIC", "TE", "TV", "TT", "TW",
        // Analysis
        "AI", "AIT", "AIC", "AT",
        // Discrete / Digital
        "DI", "DT", "DE",
        // Hand / Manual
        "HS", "HC", "HV", "HI",
        // Vibration
        "VI", "VS", "VT",
        // Control valves (instrument-grade)
        "CV",
        // Speed
        "SI", "SIT", "SIC", "SE",
        // Density
        "DI", "DIT",
        // Conductivity / Concentration
        "QI", "QIT",
        // Weight
        "WI", "WIT",
        // Multifunction
        "LSHL", "PSHH", "PSLL",
    )

    // Prefixes that are ONLY acceptable when they appear as multi-letter combos
    // Single-letter prefixes alone map to hand valves, not instruments
    private val SINGLE_LETTER_EXCLUSIONS = setOf("V", "K", "P", "G", "M", "N", "X", "Y", "Z")

    // Regex patterns that indicate false positives
    private val FALSE_POSITIVE_PATTERNS = listOf(
        Regex("""^\d"""),                           // starts with digit
        Regex("""\d[xX]\d"""),                      // dimension like 4x3, 6X8
        Regex("""^[A-Z]-\d{1,2}$"""),              // short refs: V-3, V-4, P2
        Regex("""^\d{3,}-\d{2,}-\d{2,}$"""),       // equipment code: 542-10-18
    )

    /**
     * Detect all ISA tags within a single text string.
     * Returns a list of [ParsedTag] candidates with no page/coordinate info (to be set by caller).
     */
    fun detectInText(text: String, page: Int = 1, pageWidth: Float = 1f, pageHeight: Float = 1f): List<ParsedTag> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<ParsedTag>()

        for (match in ISA_PATTERN.findAll(text)) {
            val prefix = match.groupValues[1]
            val number = match.groupValues[2]
            val suffix = match.groupValues[3]

            if (isFalsePositive(prefix, number, suffix, match.value)) continue

            val confidence = computeConfidence(prefix, number, suffix)
            val tagId = buildTagId(prefix, number, suffix)

            results.add(
                ParsedTag(
                    tagId = tagId,
                    prefix = prefix,
                    number = number,
                    suffix = suffix,
                    page = page,
                    x = null,
                    y = null,
                    confidence = confidence,
                )
            )
        }

        return results
    }

    /**
     * Detect ISA tags from a text run that includes position information.
     *
     * @param x          Raw x from PdfBox (xDirAdj), in PDF points.
     * @param y          Raw y from PdfBox (yDirAdj), in PDF points — bottom-origin.
     * @param pageWidth  Effective visual page width (post-rotation, post-cropBox).
     * @param pageHeight Effective visual page height.
     * @param originX    Lower-left x of the crop box in the direction that maps to screen-x.
     * @param originY    Lower-left y of the crop box in the direction that maps to screen-y.
     */
    fun detectWithPosition(
        text: String,
        page: Int,
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        originX: Float = 0f,
        originY: Float = 0f,
    ): List<ParsedTag> {
        val tags = detectInText(text, page)
        return tags.map { tag ->
            tag.copy(
                // Subtract crop-box origin so coords are relative to the visible page area,
                // then divide by effective dimension.  Clamp handles slight out-of-bounds text.
                x = if (pageWidth  > 0f) ((x - originX) / pageWidth ).coerceIn(0f, 1f) else null,
                // PdfBox y is bottom-origin; invert so y=0 is screen top.
                y = if (pageHeight > 0f) (1f - (y - originY) / pageHeight).coerceIn(0f, 1f) else null,
            )
        }
    }

    private fun isFalsePositive(prefix: String, number: String, suffix: String, fullMatch: String): Boolean {
        // Single-letter prefixes that are exclusively hand valves / drawing refs
        if (prefix.length == 1 && prefix in SINGLE_LETTER_EXCLUSIONS) return true

        // Very short prefix with no ISA meaning
        if (prefix.length == 1 && prefix !in setOf("F", "L", "P", "T", "A", "D", "H", "V", "S", "Q", "W")) return true

        // False-positive patterns applied to the full match
        for (pattern in FALSE_POSITIVE_PATTERNS) {
            if (pattern.containsMatchIn(fullMatch)) return true
        }

        return false
    }

    private fun computeConfidence(prefix: String, number: String, suffix: String): Float {
        var score = 0.5f

        // Known ISA prefix gets a strong boost
        if (prefix in KNOWN_ISA_PREFIXES) score += 0.4f

        // Longer prefix = more likely a real instrument
        if (prefix.length >= 2) score += 0.05f
        if (prefix.length >= 3) score += 0.05f

        // Numbers in common loop number ranges
        val numInt = number.toIntOrNull() ?: 0
        if (numInt in 1000..9999) score += 0.05f

        return score.coerceIn(0f, 1f)
    }

    private fun buildTagId(prefix: String, number: String, suffix: String): String {
        return if (suffix.isNotEmpty()) "$prefix-$number$suffix" else "$prefix-$number"
    }

    /**
     * Returns the human-readable instrument type for a given ISA prefix.
     */
    fun instrumentTypeForPrefix(prefix: String): String? = ISA_TYPE_MAP[prefix]

    private val ISA_TYPE_MAP = mapOf(
        "FI" to "Flow Indicator",
        "FIT" to "Flow Indicating Transmitter",
        "FIC" to "Flow Indicating Controller",
        "FE" to "Flow Element",
        "FV" to "Flow Control Valve",
        "FC" to "Flow Controller",
        "FQ" to "Flow Totaliser",
        "LI" to "Level Indicator",
        "LIT" to "Level Indicating Transmitter",
        "LIC" to "Level Indicating Controller",
        "LV" to "Level Control Valve",
        "LC" to "Level Controller",
        "LG" to "Level Glass",
        "PI" to "Pressure Indicator",
        "PIT" to "Pressure Indicating Transmitter",
        "PIC" to "Pressure Indicating Controller",
        "PV" to "Pressure Control Valve",
        "PT" to "Pressure Transmitter",
        "PSV" to "Pressure Safety Valve",
        "PSH" to "Pressure Switch High",
        "PSL" to "Pressure Switch Low",
        "TI" to "Temperature Indicator",
        "TIT" to "Temperature Indicating Transmitter",
        "TIC" to "Temperature Indicating Controller",
        "TE" to "Temperature Element",
        "TW" to "Thermowell",
        "TT" to "Temperature Transmitter",
        "AI" to "Analyser Indicator",
        "AIT" to "Analyser Indicating Transmitter",
        "AT" to "Analyser Transmitter",
        "DI" to "Density Indicator",
        "DT" to "Density Transmitter",
        "HS" to "Hand Switch",
        "HC" to "Hand Controller",
        "HV" to "Hand Valve",
        "VI" to "Vibration Indicator",
        "VS" to "Vibration Switch",
        "SI" to "Speed Indicator",
        "SIT" to "Speed Indicating Transmitter",
        "CV" to "Control Valve",
    )
}
