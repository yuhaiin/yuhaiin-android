package io.github.asutorufa.yuhaiin.compose

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.asutorufa.yuhaiin.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shares this app's logcat when the core cannot start, so the Web UI's log page is unavailable.
 * Callers show the sensitive-data disclosure first.
 */
suspend fun shareLogs(context: Context) {
    val file =
        withContext(Dispatchers.IO) {
            val directory = context.externalCacheDir ?: context.cacheDir
            File(directory, "yuhaiin.log").apply {
                val process =
                    ProcessBuilder("/system/bin/logcat", "-d", "-v", "threadtime", "-t", "20000")
                        .redirectErrorStream(true)
                        .start()
                outputStream().use { out -> process.inputStream.use { it.copyTo(out) } }
                process.waitFor()
            }
        }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.logcat_fileprovider", file)
    context.startActivity(
        Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newRawUri("yuhaiin log", uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                context.getString(R.string.open_logs),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
