package com.github.logviewer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern


class LogcatActivity : AppCompatActivity() {
    private val excludeList by lazy {
        val mExcludeList: MutableList<Pattern> = ArrayList()
        intent.getStringArrayListExtra(INTENT_EXCLUDE_LIST)!!.forEach {
            try {
                mExcludeList.add(Pattern.compile(it))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@lazy mExcludeList
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val logs = remember { mutableStateListOf<LogEntry>() }
            val scope = rememberCoroutineScope()
            val process = runLogcat(scope) { logs.add(it) }
            DisposableEffect(Unit) {
                onDispose {
                    logs.add(LogEntry(LogLevel.INFO, "", "stop read logcat"))
                    process.destroy()
                }
            }
            LogcatScreen(logs = logs)
        }
    }

    private fun skip(mExcludeList: List<Pattern>, line: String): Boolean {
        for (pattern in mExcludeList) if (pattern.matcher(line).matches()) return true
        return false
    }

    fun runLogcat(scope: CoroutineScope, pushLogs: (LogEntry) -> Unit): Process {
        val process = ProcessBuilder(listOf("logcat", "-v", "threadtime")).start()

        scope.launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().use {
                while (true) {
                    it.readLine()?.let { line ->
                        if (skip(excludeList, line))
                            return@let
                        try {
                            pushLogs(parseLog(line))
                        } catch (e: Exception) {
                            Log.w("parse log failed", "$e: log: $line")
                            pushLogs(LogEntry(LogLevel.INFO, Date().toString(), line))
                        }
                    } ?: break
                }
            }

            Log.i("logcat process", "stop read logcat")
            process.destroy()
        }

        return process
    }

    companion object {
        const val INTENT_EXCLUDE_LIST = "exclude_list"

        fun start(excludeList: ArrayList<String>, activity: Activity) =
            activity.startActivity(intent(excludeList, activity))

        fun intent(excludeList: ArrayList<String>, activity: Activity) =
            Intent(activity, LogcatActivity::class.java)
                .putStringArrayListExtra(INTENT_EXCLUDE_LIST, excludeList)
    }

}