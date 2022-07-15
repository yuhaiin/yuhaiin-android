package io.github.asutorufa.yuhaiin.database

class Bypass {
    var tcp: Int = 0
    var udp: Int = 0

    enum class Type(val value: Int) {
        Bypass(0),
        Direct(1),
        Proxy(2),
        Block(3);

        companion object {
            fun fromInt(value: Int) = values().first { it.value == value }
        }
    }


    companion object {
        const val DefaultJson =
            """{"tcp":0,"udp":0}"""

        val Default = Bypass().apply {
            tcp = Type.Bypass.value
            udp = Type.Bypass.value
        }
    }
}
