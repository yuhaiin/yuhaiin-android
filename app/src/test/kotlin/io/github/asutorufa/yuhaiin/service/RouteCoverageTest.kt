package io.github.asutorufa.yuhaiin.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import org.junit.Test

class RouteCoverageTest {
    private fun range(cidr: String): LongRange {
        val address =
            cidr.substringBefore('/').split('.').fold(0L) { total, part ->
                (total shl 8) or part.toLong()
            }
        val size = 1L shl (32 - cidr.substringAfter('/').toInt())
        val first = address / size * size
        return first until first + size
    }

    @Test
    fun newExclusionsExactlyCoverSameIpv4SpaceAsLegacyPreset() {
        val document =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/values/simpleroute.xml"))
        val arrays = document.getElementsByTagName("string-array")
        val array =
            (0 until arrays.length)
                .map { arrays.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "all_routes_except_local" }
        val old =
            (0 until array.childNodes.length)
                .map { array.childNodes.item(it).textContent.trim() }
                .filter { '.' in it && ':' !in it }
                .map(::range)
        val excluded = nonLocalExclusions.map(::range)
        // Prove equivalence over every interval partition, including the address-space edges.
        val boundaries =
            (old + excluded)
                .flatMap { listOf(it.first, it.last + 1) }
                .plus(listOf(0, 1L shl 32))
                .distinct()
                .sorted()
        boundaries.zipWithNext().forEach { (start, end) ->
            if (start < end)
                assertEquals(
                    old.any { start in it },
                    excluded.none { start in it },
                    "Coverage at $start",
                )
        }
    }
}
