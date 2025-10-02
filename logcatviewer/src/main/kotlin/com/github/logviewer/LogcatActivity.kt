package com.github.logviewer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern


class LogcatActivity : AppCompatActivity() {
    private val excludeList by lazy { intent.getStringArrayListExtra(INTENT_EXCLUDE_LIST)!! }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val logs = remember { mutableStateListOf<LogEntry>() }
            var stop by rememberSaveable { mutableStateOf(false) }
            start(stop) { logs.add(it) }
            DisposableEffect(Unit) {
                onDispose {
                    stop = true
                }
            }

            LogcatScreen(logs = logs)
        }
    }


    private fun skip(mExcludeList: List<Pattern>, line: String): Boolean {
        for (pattern in mExcludeList) if (pattern.matcher(line).matches()) return true
        return false
    }

    @DelicateCoroutinesApi
    fun start(stop: Boolean, pushLogs: (LogEntry) -> Unit) {
        val mExcludeList: MutableList<Pattern> = ArrayList()
        excludeList.forEach {
            try {
                mExcludeList.add(Pattern.compile(it))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            val cmd = ArrayList(listOf("logcat", "-v", "threadtime"))

            val process = ProcessBuilder(cmd).start()

            process.inputStream.bufferedReader().use {
                while (!stop)
                    it.readLine()?.let { line ->
                        if (skip(mExcludeList, line))
                            return@let
                        try {

                            pushLogs(parseLog(line))
                        } catch (e: Exception) {
                            Log.w("parse log failed", "$e: log: $line")
                            pushLogs(LogEntry(LogLevel.INFO, Date().toString(), line))
                        }
                    } ?: break
            }
            process.destroy()
        }
    }


    companion object {
        const val INTENT_EXCLUDE_LIST = "exclude_list"
        fun start(excludeList: ArrayList<String>, activity: Activity) {
            val starter = Intent(activity, LogcatActivity::class.java)
                .putStringArrayListExtra(INTENT_EXCLUDE_LIST, excludeList)
            activity.startActivity(starter)
        }

        fun intent(excludeList: ArrayList<String>, activity: Activity) =
            Intent(activity, LogcatActivity::class.java)
                .putStringArrayListExtra(INTENT_EXCLUDE_LIST, excludeList)
    }

}