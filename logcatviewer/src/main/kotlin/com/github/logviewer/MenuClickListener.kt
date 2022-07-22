package com.github.logviewer

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MenuClickListener internal constructor(
    private val context: Context,
    private val readLogcat: ReadLogcat,
    private val floatingWindowLauncher: () -> Unit = {},
) : Toolbar.OnMenuItemClickListener {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear -> readLogcat.clear()
            R.id.export -> exportLogFile()
            R.id.floating -> floatingWindowLauncher()
            R.id.Verbose, R.id.Debug, R.id.Info, R.id.Warning, R.id.Error, R.id.Fatal ->
                readLogcat.filter(item.title)
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
}