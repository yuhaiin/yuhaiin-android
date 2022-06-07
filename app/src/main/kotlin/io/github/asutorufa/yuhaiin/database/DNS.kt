package io.github.asutorufa.yuhaiin.database

class DNS {
    var host: String = ""
    var type: Int = 0
    var subnet: String = ""
    var tlsServerName: String = ""
    var proxy: Boolean = false

    enum class Type(val value: Int) {
        Reserve(0),
        UDP(1),
        TCP(2),
        HTTPS(3),
        TLS(4),
        QUIC(5),
        HTTPS3(6);

        companion object {
            fun fromInt(value: Int) = values().first { it.value == value }
        }
    }

    companion object {
        const val DefaultRemoteJson =
            """{"host":"cloudflare-dns.com","proxy":true,"subnet":"","tlsServerName":"","type":3}"""
        const val DefaultLocalJson =
            """{"host":"223.5.5.5","proxy":false,"subnet":"","tlsServerName":"","type":3}"""
        const val DefaultBootstrapJson =
            """{"host":"223.5.5.5","proxy":false,"subnet":"","tlsServerName":"","type":3}"""

        val DefaultRemote = DNS().apply {
            host = "cloudflare-dns.com"
            type = Type.HTTPS.value
            proxy = true
            subnet = ""
            tlsServerName = ""
        }
        val DefaultLocal = DNS().apply {
            host = "223.5.5.5"
            type = Type.HTTPS.value
            subnet = ""
            tlsServerName = ""
        }
        val DefaultBootstrap = DefaultLocal
    }
}