package io.github.asutorufa.yuhaiin.util

import android.util.Log
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import java.io.BufferedReader
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
}