package com.github.logviewer

import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.logviewer.databinding.LogcatViewerActivityLogcatBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class ReadLogcat(
    mBinding: LogcatViewerActivityLogcatBinding,
    excludeList: ArrayList<String>,
    float: Boolean = false
) {
    private val mExcludeList: MutableList<Pattern> = ArrayList()
    private val adapter = LogcatAdapter()
    private var mReading = false
    private var time: Date? = null
    private var mBinding: LogcatViewerActivityLogcatBinding

    init {
        this.mBinding = mBinding
        mBinding.list.apply {
            adapter = this@ReadLogcat.adapter
            if (!float) onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                    this@ReadLogcat.adapter.getItem(position).apply {
                        val text = String.format(
                            Locale.getDefault(),
                            CONTENT_TEMPLATE,
                            SimpleDateFormat(
                                "MM-dd hh:mm:ss.SSS",
                                Locale.getDefault()
                            ).format(time), processId, threadId, priority, tag, content
                        )
                        AlertDialog.Builder(context)
                            .setMessage(text)
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show().apply {
                                findViewById<View?>(android.R.id.message).let {
                                    if (it is TextView)
                                        it.setTextIsSelectable(true)
                                }
                            }
                    }
                }
        }

        mExcludeList.clear()
        excludeList.forEach {
            try {
                mExcludeList.add(Pattern.compile(it))
            } catch (e: Exception) {
                e.printStackTrace()
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
            Scanner(process.inputStream).apply {
                while (mReading && hasNext()) {
                    val line = nextLine()
                    if (LogItem.IGNORED_LOG.contains(line) || skip(mExcludeList, line)) continue
                    try {
                        LogItem(line).let {
                            time = it.time
                            mBinding.list.post { adapter.append(it) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                process.destroy()
                close()
                mReading = false
            }
        }
    }

    private fun skip(mExcludeList: List<Pattern>, line: String): Boolean {
        for (pattern in mExcludeList) if (pattern.matcher(line).matches()) return true
        return false
    }

    fun running(): Boolean = mReading
    fun clear() = adapter.clear()
    fun filter(filter: CharSequence?) = adapter.filter.filter(filter)

    fun stop() {
        mReading = false
    }

    companion object {
        private const val CONTENT_TEMPLATE =
            "Time: %s\nPid: %d Tid: %d Priority: %s Tag: %s\n\nContent: \n%s"
        const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
    }
}