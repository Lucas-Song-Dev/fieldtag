package com.fieldtag.domain.parser

/**
 * A single instrument tag detected from a P&ID text layer.
 *
 * @param tagId     Canonical tag ID, e.g. "LIT-5219"
 * @param prefix    ISA function letters, e.g. "LIT"
 * @param number    Loop/instrument number, e.g. "5219"
 * @param suffix    Optional suffix letter, e.g. "A" for "LIT-5219A"
 * @param page      1-based PDF page number
 * @param x         Normalised x coordinate (0.0–1.0) relative to page width; null if unavailable
 * @param y         Normalised y coordinate (0.0–1.0) relative to page height; null if unavailable
 * @param confidence Score 0.0–1.0 indicating how confident the detector is this is a real instrument
 */
data class ParsedTag(
    val tagId: String,
    val prefix: String,
    val number: String,
    val suffix: String = "",
    val page: Int,
    val x: Float? = null,
    val y: Float? = null,
    val confidence: Float = 1.0f,
)

data class ParseResult(
    val tags: List<ParsedTag>,
    val pageCount: Int,
    val rawTextJson: String?,
    val warnings: List<String>,
)
