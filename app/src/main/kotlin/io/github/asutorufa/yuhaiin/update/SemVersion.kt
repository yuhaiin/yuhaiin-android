package io.github.asutorufa.yuhaiin.update

import java.math.BigInteger

/**
 * SemVer precedence: numeric prerelease identifiers are compared numerically; build metadata is
 * ignored.
 */
internal data class SemVersion(val numbers: List<BigInteger>, val prerelease: List<String>) :
    Comparable<SemVersion> {
    override fun compareTo(other: SemVersion): Int {
        numbers.zip(other.numbers).forEach { (left, right) ->
            left
                .compareTo(right)
                .takeIf { it != 0 }
                ?.let {
                    return it
                }
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty())
            return when {
                prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
                prerelease.isEmpty() -> 1
                else -> -1
            }
        prerelease.zip(other.prerelease).forEach { (left, right) ->
            val a = left.toBigIntegerOrNull()
            val b = right.toBigIntegerOrNull()
            val order =
                when {
                    a != null && b != null -> a.compareTo(b)
                    a != null -> -1
                    b != null -> 1
                    else -> left.compareTo(right)
                }
            if (order != 0) return order
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(value: String): SemVersion? {
            val match =
                Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
                    .matchEntire(value.trim()) ?: return null
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.isEmpty() }) return null
            return SemVersion((1..3).map { match.groupValues[it].toBigInteger() }, pre)
        }
    }
}

internal fun compareVersions(left: String, right: String): Int {
    val a = SemVersion.parse(left) ?: return -1
    val b = SemVersion.parse(right) ?: return 1
    return a.compareTo(b)
}
