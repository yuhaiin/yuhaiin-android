package io.github.asutorufa.yuhaiin

import android.util.Log
import yuhaiin.Yuhaiin_

class yuhaiin(
    private val host: String,
    private val path: String,
    private val dnsServer: String,
    private val socks5Server: String,
    private val httpserver: String,
    private val fakedns: String,
    private val fakednse: Boolean,
    private val callback1: Callback
) : Thread() {
    private val yuhaiin = Yuhaiin_()
    override fun run() {
        try {
            yuhaiin.start(host, path, dnsServer, socks5Server, httpserver, fakednse, fakedns)
        } catch (e: Exception) {
            Log.d("yuhaiin", "run: $e")
        }
        callback1.run()
    }

    override fun interrupt() {
        super.interrupt()
        yuhaiin.stop()
    }

    open class Callback {
        open fun run() {}
    }
}