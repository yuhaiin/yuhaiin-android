package io.github.asutorufa.yuhaiin.util

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.YuhaiinVpnService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object Utility {
    private val TAG = Utility::class.java.simpleName

    fun exec(cmd: String, callback: () -> Unit): Process {
        val p = Runtime.getRuntime().exec(cmd)

        if (DEBUG) {
            val th = Thread {
                val input = BufferedReader(InputStreamReader(p.inputStream))
                try {
                    var line: String?
                    while (input.readLine().also { line = it } != null) {
                        println(line)
                    }
                    input.close()
                } catch (e: Exception) {
                    Log.d(TAG, "exec: $e")
                }
            }

            th.start()
        }

        Thread { p.waitFor(); callback();Log.d(TAG, "exec callback") }.start()

        return p
    }

    fun join(list: List<String?>, separator: String): String {
        if (list.isEmpty()) return ""
        val ret = StringBuilder()
        for (s in list) {
            ret.append(s).append(separator)
        }
        return ret.substring(0, ret.length - separator.length)
    }

    @Throws(IOException::class, InterruptedException::class)
    fun startYuhaiin(context: Context, host: String): Process {
        val cmd = context.applicationInfo.nativeLibraryDir + "/libyuhaiin.so" +
                " -path " + context.getExternalFilesDir("yuhaiin") +
                " -host " + host
        Log.d(TAG, "startYuhaiin: $cmd")
        return exec(cmd) {}
    }

    fun startVpn(context: Context) = context.startService(Intent(context, YuhaiinVpnService::class.java))
}