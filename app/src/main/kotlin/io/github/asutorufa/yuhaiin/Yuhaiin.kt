package io.github.asutorufa.yuhaiin

import android.util.Log
import yuhaiin.App

class Yuhaiin(
    private val host: String,
    private val path: String,
    private val dnsServer: String,
    private val socks5Server: String,
    private val httpserver: String,
    private val fakedns: String,
    private val fakednse: Boolean,
    private val callback: () -> Unit
) : Thread() {
    private val yuhaiin = App()
    override fun run() {
        try {
            yuhaiin.start(host, path, dnsServer, socks5Server, httpserver, fakednse, fakedns)
        } catch (e: Exception) {
            Log.d("yuhaiin", "run: $e")
        }
        callback()
    }

    override fun interrupt() {
        super.interrupt()
        yuhaiin.stop()
    }
}