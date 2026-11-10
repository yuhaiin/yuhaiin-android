package io.github.asutorufa.yuhaiin.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebThemeTest {
    @Test
    fun acceptsOnlyTheTwoThemeNames() {
        assertEquals(true, parseWebTheme("\"dark\""))
        assertEquals(false, parseWebTheme("\"light\""))
        assertEquals(true, parseWebTheme("dark"))
        assertNull(parseWebTheme("null"))
        assertNull(parseWebTheme(null))
        assertNull(parseWebTheme("\"dark\\\" onload=\""))
        assertNull(parseWebTheme("\"system\""))
    }
}
