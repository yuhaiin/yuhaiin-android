package io.github.asutorufa.yuhaiin.update

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkChecksumTest {
    @Test
    fun completedPartUsesPublishedAssetNameAndRejectsModifiedBytes() {
        val file = Files.createTempFile("release.apk", ".part").toFile()
        try {
            file.writeText("completed APK")
            val digest =
                MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                    "%02x".format(it)
                }
            val expected = apkChecksum("$digest  *outputs/release.apk\n", "release.apk")
            assertEquals(digest, expected)
            verifyApkChecksum(file, expected)
            file.appendText("modified")
            assertThrows(IllegalStateException::class.java) { verifyApkChecksum(file, expected) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun absentOrMalformedChecksumCannotBeAccepted() {
        assertThrows(IllegalStateException::class.java) { apkChecksum("", "release.apk") }
        assertThrows(IllegalArgumentException::class.java) {
            apkChecksum("bad  release.apk", "release.apk")
        }
    }
}
