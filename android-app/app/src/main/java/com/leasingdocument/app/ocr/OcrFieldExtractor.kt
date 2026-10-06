package com.leasingdocument.app.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Conservative candidates, never authoritative identity information.
 * Ambiguous values stay unset.
 */
object OcrFieldExtractor {

    fun extract(lines: List<String>): JSONObject {
        val full = lines.joinToString("\n")

        fun candidates(pattern: String): List<String> =
            Regex(pattern, RegexOption.IGNORE_CASE)
                .findAll(full)
                .map { it.value.trim() }
                .distinct()
                .toList()

        fun unique(values: List<String>): Any =
            values.singleOrNull() ?: JSONObject.NULL

        val nic = candidates(
            "(?<![A-Za-z0-9])(?:[0-9]{12}|[0-9]{9}[VX])(?![A-Za-z0-9])"
        )

        val licence = candidates(
            "(?<![A-Za-z0-9])[A-Z]{1,3}[0-9]{5,10}(?![A-Za-z0-9])"
        )

        val dates = candidates(
            "\\b(?:[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}|" +
                    "[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{4})\\b"
        )

        val blood = candidates(
            "(?<![A-Za-z0-9])(?:AB|A|B|O)[+-](?![A-Za-z0-9])"
        )

        val amounts = candidates(
            "(?:LKR|Rs\\.?)\\s*[0-9][0-9,]*(?:\\.[0-9]{2})?"
        )

        // Only use a clearly labelled name.
        // Do not mistake a country or header for a person's name.
        val names = lines.mapNotNull { line ->
            Regex(
                "^\\s*(?:full\\s+name|name)\\s*[:：]\\s*(.+)$",
                RegexOption.IGNORE_CASE
            )
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.trim()
        }.distinct()

        return JSONObject()
            .put("nicNumber", unique(nic))
            .put("licenceNumber", unique(licence))
            .put("possibleName", unique(names))
            .put("dates", JSONArray(dates))
            .put("bloodGroup", unique(blood))
            .put("amounts", JSONArray(amounts))
            .put("nicCandidates", JSONArray(nic))
            .put("licenceCandidates", JSONArray(licence))
            .put(
                "extractionNote",
                "Candidates require manual checking; missing or ambiguous values are not inferred."
            )
    }
}