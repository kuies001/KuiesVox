package tw.kuies.voiceime

internal data class SemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val preRelease: List<String> = emptyList()
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }

        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val sharedLength = minOf(preRelease.size, other.preRelease.size)
        for (index in 0 until sharedLength) {
            compareIdentifiers(preRelease[index], other.preRelease[index])
                .takeIf { it != 0 }
                ?.let { return it }
        }
        return compareValues(preRelease.size, other.preRelease.size)
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (preRelease.isNotEmpty()) append('-').append(preRelease.joinToString("."))
    }

    companion object {
        private val pattern = Regex(
            """^[vV]?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"""
        )

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val major = match.groupValues[1].toLongOrNull() ?: return null
            val minor = match.groupValues[2].toLongOrNull() ?: return null
            val patch = match.groupValues[3].toLongOrNull() ?: return null
            val preRelease = match.groupValues[4]
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                .orEmpty()
            if (preRelease.any { it.isNumericIdentifier() && it.length > 1 && it.startsWith('0') }) {
                return null
            }
            return SemanticVersion(major, minor, patch, preRelease)
        }

        private fun compareIdentifiers(first: String, second: String): Int {
            val firstNumeric = first.isNumericIdentifier()
            val secondNumeric = second.isNumericIdentifier()
            if (firstNumeric && secondNumeric) {
                compareValues(first.length, second.length).takeIf { it != 0 }?.let { return it }
                return first.compareTo(second)
            }
            if (firstNumeric != secondNumeric) return if (firstNumeric) -1 else 1
            return first.compareTo(second)
        }

        private fun String.isNumericIdentifier(): Boolean = isNotEmpty() && all(Char::isDigit)
    }
}
