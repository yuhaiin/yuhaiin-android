package com.github.logviewer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }

                isSystemInDarkTheme() -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                ChangeSystemBarsTheme(!isSystemInDarkTheme())
                LogcatScreen(logs = logs)
            }
        }
    }

    @Composable
    private fun ChangeSystemBarsTheme(lightTheme: Boolean) {
        val barColor = MaterialTheme.colorScheme.background.toArgb()
        LaunchedEffect(lightTheme) {
            if (lightTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        barColor, barColor,
                    ),
                    navigationBarStyle = SystemBarStyle.light(
                        barColor, barColor,
                    ),
                )
            } else {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(
                        barColor,
                    ),
                    navigationBarStyle = SystemBarStyle.dark(
                        barColor,
                    ),
                )
            }
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
                    try {
                        it.readLine()?.let { line ->
                            try {
                                if (skip(excludeList, line))
                                    return@let
                                pushLogs(parseLog(line))
                            } catch (e: Exception) {
                                Log.w("parse log failed", "$e: log: $line")
                                pushLogs(LogEntry(LogLevel.INFO, Date().toString(), line))
                            }
                        } ?: break
                    } catch (e: Exception) {
                        Log.w("read log failed", "$e")
                        break
                    }
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