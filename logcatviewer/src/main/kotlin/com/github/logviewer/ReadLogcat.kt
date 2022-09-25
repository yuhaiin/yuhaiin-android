package com.github.logviewer

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class ReadLogcat(
    private val context: Context,
    private val mBinding: LogcatViewerActivityLogcatBinding,
    excludeList: ArrayList<String>,
    private val floatingWindowLauncher: () -> Unit = emptyFunction(),
) : Toolbar.OnMenuItemClickListener {
    private val mExcludeList: MutableList<Pattern> = ArrayList()
    private var adapter: LogcatAdapter =
        LogcatAdapter(floatingWindowLauncher == emptyFunction(), mBinding)
    private var mReading = false
    private var time: Date? = null

    init {
        mBinding.list.adapter = this@ReadLogcat.adapter
        mExcludeList.clear()
        excludeList.forEach {
            ignore {
                mExcludeList.add(Pattern.compile(it))
            }
        }
    }

    @DelicateCoroutinesApi
    fun start() {
        GlobalScope.launch(Dispatchers.IO) {
            mReading = true
            val cmd = ArrayList(listOf("logcat", "-v", "threadtime"))
            time?.let {
                cmd.add("-T")
                cmd.add(SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(it))
            }

            val process = ProcessBuilder(cmd).start()

            process.inputStream.bufferedReader().use {
                while (mReading)
                    it.readLine()?.let { line ->
                        if (LogItem.IGNORED_LOG.contains(line) || skip(mExcludeList, line))
                            return@let
                        ignore {
                            LogItem(line).let { item ->
                                time = item.time
                                adapter.append(item)
                            }
                        }
                    } ?: break
            }

            process.destroy()
            stop()
        }
    }

    private fun skip(mExcludeList: List<Pattern>, line: String): Boolean {
        for (pattern in mExcludeList) if (pattern.matcher(line).matches()) return true
        return false
    }

    fun running(): Boolean = mReading

    fun stop() {
        mReading = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear -> adapter.clear()
            R.id.export -> exportLogFile()
            R.id.floating -> floatingWindowLauncher()
            R.id.Verbose, R.id.Debug, R.id.Info, R.id.Warning, R.id.Error, R.id.Fatal ->
                adapter.setFilter(item.title?.get(0).toString())
            else -> return false
        }
        return true
    }

    @DelicateCoroutinesApi
    fun exportLogFile() {
        GlobalScope.launch(Dispatchers.IO) {
            File(context.externalCacheDir, "yuhaiin.log").apply {
                writeText("Yuhaiin Logcat:\n")
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d")).inputStream.use { input ->
                        FileOutputStream(this, true).use {
                            input.copyTo(it)
                        }
                    }

                val authority = "${context.packageName}.logcat_fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, this)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_STREAM, uri),
                        "Export Logcat"
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    companion object {
        const val CONTENT_TEMPLATE =
            "Time: %s\nPid: %d Tid: %d Priority: %s Tag: %s\n\nContent: \n%s"
        const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"

        fun ignore(f: () -> Unit) {
            try {
                f()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun emptyFunction(): () -> Unit = {}
    }
}