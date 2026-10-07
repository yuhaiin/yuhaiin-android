package io.github.asutorufa.yuhaiin.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import yuhaiin.Yuhaiin

const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

data class BackupPreview(
    val createdAt: String,
    val nodes: Int,
    val routes: Int,
    val preferences: Int,
)

internal fun backupPreview(data: ByteArray): BackupPreview {
    require(data.isNotEmpty() && data.size <= MAX_BACKUP_BYTES) { "Invalid backup size" }
    val root =
        Json.parseToJsonElement(data.decodeToString(throwOnInvalidSequence = true)).jsonObject
    require(root["format"]?.jsonPrimitive?.content == "yuhaiin-config") {
        "Unsupported backup format"
    }
    require(root["version"]?.jsonPrimitive?.int == 1) { "Unsupported backup version" }
    val tables = root.getValue("tables").jsonObject
    fun rows(name: String) = tables.getValue(name).jsonObject.getValue("rows").jsonArray.size
    return BackupPreview(
        root.getValue("createdAt").jsonPrimitive.content,
        rows("nodes_v2"),
        rows("route_rules_v2"),
        rows("android_extra_preferences"),
    )
}

object ConfigBackup {
    private suspend fun bytes(): ByteArray =
        withContext(Dispatchers.IO) {
            MainApplication.settings.flush()
            Yuhaiin.exportConfig()
        }

    suspend fun export(context: Context, uri: Uri) =
        withContext(Dispatchers.IO) {
            val data = bytes()
            context.contentResolver.openOutputStream(uri, "wt").use { stream ->
                requireNotNull(stream) { "Unable to open backup destination" }.write(data)
            }
        }

    suspend fun read(context: Context, uri: Uri): Pair<ByteArray, BackupPreview> =
        withContext(Dispatchers.IO) {
            val data =
                context.contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream) { "Unable to open backup" }
                    stream.readBytesLimited(MAX_BACKUP_BYTES)
                }
            MainApplication.settings.ready.await()
            Yuhaiin.validateConfig(data)
            data to backupPreview(data)
        }

    suspend fun restore(data: ByteArray) =
        withContext(Dispatchers.IO) {
            // Serialized with UI preference writes; Go rejects a simultaneous VPN start
            // in either process and restores every configuration table atomically.
            MainApplication.settings.commit {
                Yuhaiin.importConfig(data)
            }
            MainApplication.settings.refresh()
            io.github.asutorufa.yuhaiin.data.RouteRepository.refresh()
        }

    suspend fun share(context: Context) {
        val file =
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "backups").apply { mkdirs() }
                directory
                    .listFiles()
                    ?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }
                    ?.forEach { it.delete() }
                File(directory, "yuhaiin-backup-${System.currentTimeMillis()}.json").apply {
                    writeBytes(bytes())
                }
            }
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.backup_fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newRawUri("yuhaiin backup", uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                context.getString(R.string.backup_share),
            )
        )
    }
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "Backup exceeds 32 MiB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
