package com.mckimquyen.reader.infrastructure.android

/**
 * Debug-only inputs of the Google UMP consent form: which region to pretend to be in, and which phones (UMP's own hash, not the AdMob one) may do it.
 * Both come from gradle properties in a debug build and are empty in a release build, so a release can never force a region.
 */
object AdUmpDebug {

    private val KNOWN_GEOGRAPHIES = setOf("EEA", "NOT_EEA", "US_STATE", "DISABLED")

    /** `null` for a blank or unknown value, so a typo never reaches the SDK (it would silently mean "real region"). */
    fun geographyOrNull(raw: String): String? = raw.trim().uppercase().takeIf { it in KNOWN_GEOGRAPHIES }

    fun parseHashedIds(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
