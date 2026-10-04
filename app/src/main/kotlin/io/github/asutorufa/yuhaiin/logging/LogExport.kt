package io.github.asutorufa.yuhaiin.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.asutorufa.yuhaiin.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun exportLogs(context: Context, entries: List<LogEntry>) {
    val file =
        withContext(Dispatchers.IO) {
            File(context.externalCacheDir, "yuhaiin.log").apply {
                bufferedWriter().use { writer -> entries.forEach { writer.appendLine(it.line()) } }
            }
        }
    val uri =
        FileProvider.getUriForFile(context, "${context.packageName}.logcat_fileprovider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, uri),
            context.getString(R.string.export_logs),
        )
    )
}
