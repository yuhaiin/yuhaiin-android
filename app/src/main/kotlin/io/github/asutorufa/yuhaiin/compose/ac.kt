package io.github.asutorufa.yuhaiin.compose

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap

class ACAutomaton {
    private val root = Node()

    init {
        root.fail = root
    }

    private class Node {
        var next: Char2ObjectOpenHashMap<Node> = Char2ObjectOpenHashMap()
        lateinit var fail: Node
        var mark: String? = null
    }

    fun insert(pattern: String) {
        var node = root
        for (c in pattern) node = node.next.getOrPut(c) { Node() }
        node.mark = pattern
    }

    fun buildFail() {
        class QueueElem(val p: Node, val n: Node, val b: Char = '\u0000')

        val queue = ArrayDeque<QueueElem>()
        queue.add(QueueElem(root, root))
        while (queue.isNotEmpty()) {
            queue.removeAt(0).let {
                it.n.fail = findFail(it.p, it.b)
                for ((k, v) in it.n.next) {
                    queue.add(QueueElem(it.n, v, k))
                }
            }
        }
    }

    private tailrec fun findFail(parent: Node, b: Char): Node {
        val tmp = if (parent == root) parent else parent.fail.next[b]
        if (tmp != null) return tmp
        return findFail(parent.fail, b)
    }

    fun search(text: String): List<String> {
        val result = ArrayDeque<String>()
        var node = root
        for (c in text) {
            while (node != root) {
                node.next[c]?.let { break }
                node = node.fail
            }

            node.next[c]?.let {
                node = it
                node.mark?.let { m -> result.add(m) }
                node.fail.mark?.let { m -> result.add(m) }
            }
        }
        return result
    }

    fun exist(text: String): Boolean {
        var node = root
        for (c in text) {
            while (node != root) {
                node.next[c]?.let { break }
                node = node.fail
            }

            node.next[c]?.let {
                node = it
                node.mark?.let { return true }
                node.fail.mark?.let { return true }
            }
        }

        return false
    }
}

fun main() {
    val ac = ACAutomaton()
    ac.insert("he")
    ac.insert("she")
    ac.insert("his")
    ac.insert("hers")
    ac.buildFail()

    val text = "ushers"
    val matches = ac.search(text)

    println(matches)
}