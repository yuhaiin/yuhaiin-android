package io.github.asutorufa.yuhaiin.update

import kotlin.test.*
import org.junit.Test

class SemVersionTest {
    @Test
    fun numericPrereleaseIdentifiersHaveNumericPrecedence() {
        assertTrue(compareVersions("v1.2.3-beta.10", "v1.2.3-beta.2") > 0)
        assertTrue(compareVersions("1.2.3-beta.2", "1.2.3-beta.10") < 0)
    }

    @Test
    fun standardPrecedenceExamplesAndBuildMetadata() {
        val versions =
            listOf(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0",
            )
        versions.zipWithNext().forEach { (left, right) ->
            assertTrue(compareVersions(left, right) < 0)
        }
        assertEquals(0, compareVersions("v1.0.0+one", "1.0.0+two"))
    }

    @Test
    fun malformedAndLargeVersionsDoNotOverflow() {
        assertNull(SemVersion.parse("1.2.3-beta..1"))
        assertNull(SemVersion.parse("garbage"))
        assertTrue(compareVersions("9999999999999999.0.0", "2.0.0") > 0)
    }
}
