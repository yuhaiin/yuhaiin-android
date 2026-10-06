package io.github.asutorufa.yuhaiin.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class NativeNode(val id: String = "", val name: String = "")

@Serializable
data class NativeSession(
    val download: String = "0",
    val upload: String = "0",
    val totalDownload: String = "0",
    val totalUpload: String = "0",
    val active: Int = 0,
    val opened: String = "0",
    val failed: String = "0",
)

@Serializable
data class NativeStatus(
    val startedAt: Long = 0,
    val durationSeconds: Long = 0,
    val tcp: NativeNode? = null,
    val udp: NativeNode? = null,
    val session: NativeSession = NativeSession(),
)

@Serializable
data class NativeNodeHealth(
    val id: String = "",
    val ok: Boolean = false,
    val latencyMs: Long = 0,
    val error: String = "",
)

@Serializable
data class NativeHealth(
    val checkedAt: Long = 0,
    val tcp: NativeNodeHealth? = null,
    val udp: NativeNodeHealth? = null,
)

private val nativeJson = Json { ignoreUnknownKeys = true }

fun decodeNativeStatus(value: String): NativeStatus? = runCatching {
    nativeJson.decodeFromString<NativeStatus>(value)
}.getOrNull()

fun decodeNativeHealth(value: String): NativeHealth? = runCatching {
    nativeJson.decodeFromString<NativeHealth>(value)
}.getOrNull()

internal fun formatBytes(value: String): String {
    var bytes = value.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var index = 0
    while (bytes >= 1024 && index < units.lastIndex) {
        bytes /= 1024
        index++
    }
    return java.lang.String.format(java.util.Locale.getDefault(), "%.2f %s", bytes, units[index])
}
