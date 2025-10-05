package io.github.asutorufa.yuhaiin.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ACAutomatonTest {

    @Test
    fun testBasicSearch() {
        val ac = ACAutomaton()
        ac.insert("he")
        ac.insert("she")
        ac.insert("his")
        ac.insert("hers")
        ac.buildFail()

        val text = "ushers"
        val matches = ac.search(text)

        println(matches)

        // 验证匹配结果
        assertTrue(matches.contains("he"))
        assertTrue(matches.contains("she"))
        assertTrue(matches.contains("hers"))
        assertEquals(listOf("she", "he", "hers"), matches)
    }


    @Test
    fun testNoMatch() {
        val ac = ACAutomaton()
        ac.insert("hello")
        ac.insert("world")
        ac.buildFail()

        val text = "kotlin"
        val matches = ac.search(text)

        assertTrue(matches.isEmpty())
    }
}