package io.github.asutorufa.yuhaiin.update

import java.io.File
import java.security.MessageDigest

/** Match the published asset name, even when a completed download still has its .part suffix. */
internal fun apkChecksum(checksums: String, assetName: String): String =
    checksums
        .lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { it.size == 2 && File(it[1].removePrefix("*")).name == assetName }
        ?.first()
        ?.also { require(it.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid APK checksum" } }
        ?: error("Checksum for $assetName is missing")

/** Stream the file on the caller's IO dispatcher; never read an APK into memory. */
internal fun verifyApkChecksum(apk: File, expected: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    apk.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    check(actual.equals(expected, ignoreCase = true)) { "APK checksum mismatch" }
}
