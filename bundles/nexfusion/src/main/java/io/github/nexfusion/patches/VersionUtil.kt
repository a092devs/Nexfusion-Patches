package io.github.nexfusion.patches

/**
 * Parsed application version supporting semantic versioning principles
 * tailored for Android application version naming conventions (e.g. YouTube, YouTube Music, Reddit).
 *
 * Parsing policy:
 * - The base version consists of one or more non-empty decimal segments separated by dots (e.g. "21.39.0", "20.22.00").
 * - Each base segment MUST consist strictly of decimal digits ('0'..'9').
 * - Arbitrary non-numeric characters (e.g. "21abc", "21.foo.39", "v21.39.0") are NOT extracted or stripped;
 *   any non-numeric base segment renders the version invalid (isValid = false).
 * - Optional pre-release suffix is delimited by '-' (e.g. "21.39.0-alpha", "12.19.1-release.0").
 *   Pre-release identifiers are dot- or hyphen-separated alphanumeric tokens.
 *   Dangling hyphens (e.g. "21.39.0-") or invalid tokens render the version invalid.
 * - Optional build metadata following '+' is ignored in version precedence per SemVer 2.0.0.
 * - Trailing zero segments (e.g. "20.22.00" vs "20.22.0", "21.39" vs "21.39.0") compare as numerically equal.
 * - Blank, empty, or malformed inputs are explicitly marked invalid (isValid = false).
 */
data class AppVersion(
    val raw: String,
    val numericParts: List<Long>,
    val preReleaseParts: List<String>?,
    val isValid: Boolean,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        // 1. Validity precedence:
        // Valid versions rank higher than invalid versions.
        if (this.isValid && !other.isValid) return 1
        if (!this.isValid && other.isValid) return -1
        if (!this.isValid && !other.isValid) {
            // Neither version is valid; they cannot be meaningfully ordered.
            return 0
        }

        // 2. Numeric segment comparison:
        val maxNumericLen = maxOf(this.numericParts.size, other.numericParts.size)
        for (i in 0 until maxNumericLen) {
            val a = this.numericParts.getOrElse(i) { 0L }
            val b = other.numericParts.getOrElse(i) { 0L }
            if (a != b) {
                return a.compareTo(b)
            }
        }

        // 3. Pre-release suffix precedence:
        // When base numeric segments are equal, a release version (no suffix) has higher precedence than a pre-release version.
        // e.g., 21.39.0 > 21.39.0-alpha
        val thisPre = this.preReleaseParts
        val otherPre = other.preReleaseParts
        if (thisPre == null && otherPre != null) return 1   // release > pre-release
        if (thisPre != null && otherPre == null) return -1  // pre-release < release
        if (thisPre != null && otherPre != null) {
            val maxPreLen = maxOf(thisPre.size, otherPre.size)
            for (i in 0 until maxPreLen) {
                if (i >= thisPre.size) return -1 // fewer identifiers < more identifiers
                if (i >= otherPre.size) return 1  // more identifiers > fewer identifiers
                val p1 = thisPre[i]
                val p2 = otherPre[i]
                val p1Num = p1.toLongOrNull()
                val p2Num = p2.toLongOrNull()
                val cmp = when {
                    p1Num != null && p2Num != null -> p1Num.compareTo(p2Num)
                    p1Num != null && p2Num == null -> -1 // numeric identifiers rank lower than non-numeric
                    p1Num == null && p2Num != null -> 1
                    else -> p1.compareTo(p2, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
        }

        return 0
    }

    companion object {
        fun parse(version: String): AppVersion {
            val trimmed = version.trim()
            if (trimmed.isEmpty()) {
                return AppVersion(trimmed, emptyList(), null, isValid = false)
            }

            // Discard leading 'v' / 'V' and strip trailing space-delimited text/architecture
            val clean = trimmed.removePrefix("v").removePrefix("V").substringBefore(' ')
            if (clean.isEmpty()) {
                return AppVersion(trimmed, emptyList(), null, isValid = false)
            }

            // Discard build metadata after '+' (metadata does not affect precedence per SemVer 2.0.0)
            val noMetadata = clean.substringBefore('+')
            if (noMetadata.isEmpty()) {
                return AppVersion(trimmed, emptyList(), null, isValid = false)
            }

            // Separate base version from pre-release suffix (delimited by '-')
            val hasHyphen = noMetadata.contains('-')
            val basePart = if (hasHyphen) noMetadata.substringBefore('-') else noMetadata
            val preReleasePart = if (hasHyphen) noMetadata.substringAfter('-') else null

            // If basePart is empty (e.g. "-alpha") or preReleasePart is empty (e.g. "21.39.0-"), invalid
            if (basePart.isEmpty() || (hasHyphen && preReleasePart.isNullOrEmpty())) {
                return AppVersion(trimmed, emptyList(), null, isValid = false)
            }

            // Base part: split by '.' - each segment must strictly be non-empty digits ('0'..'9')
            val segments = basePart.split('.')
            val numericParts = ArrayList<Long>(segments.size)
            for (seg in segments) {
                if (seg.isEmpty() || !seg.all { it in '0'..'9' }) {
                    return AppVersion(trimmed, emptyList(), null, isValid = false)
                }
                val num = seg.toLongOrNull() ?: return AppVersion(trimmed, emptyList(), null, isValid = false)
                numericParts.add(num)
            }

            // Parse pre-release identifiers split by '.' or '-'
            val preReleaseParts: List<String>? = if (preReleasePart != null) {
                val tokens = preReleasePart.split('.', '-')
                if (tokens.isEmpty() || tokens.any { it.isEmpty() || !it.all { ch -> ch.isLetterOrDigit() } }) {
                    return AppVersion(trimmed, emptyList(), null, isValid = false)
                }
                tokens
            } else {
                null
            }

            return AppVersion(trimmed, numericParts, preReleaseParts, isValid = true)
        }
    }
}

/**
 * Checks whether [actualVersion] is equal to or greater than [targetVersion].
 *
 * Fail-safe policy: Returns true if and only if both [actualVersion] and [targetVersion]
 * are valid version strings and [actualVersion] is equal to or greater than [targetVersion].
 *
 * If either [actualVersion] or [targetVersion] is blank or malformed, returns false.
 */
fun isVersionAtLeast(actualVersion: String, targetVersion: String): Boolean {
    val actual = AppVersion.parse(actualVersion)
    val target = AppVersion.parse(targetVersion)

    if (!actual.isValid || !target.isValid) return false
    return actual >= target
}
