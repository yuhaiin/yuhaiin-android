package io.github.asutorufa.yuhaiin.util

import android.util.Log
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader


object Utility {
    private val TAG = Utility::class.java.simpleName

    @DelicateCoroutinesApi
    fun exec(cmd: String, callback: () -> Unit): Process {
        Log.d(TAG, (cmd))
        val p = Runtime.getRuntime().exec(cmd)

        GlobalScope.launch(Dispatchers.IO) {
            p.waitFor()
            callback()
            Log.d(TAG, "exec callback")
        }

        if (DEBUG) {
            GlobalScope.launch(Dispatchers.IO) {
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
        }
        return p
    }
}