package io.github.asutorufa.yuhaiin.backup

import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class ConfigBackupTest {
    @Test
    fun fileReadRejectsOversizedInputBeforeLoadingWholeFile() {
        val stream = ByteArrayInputStream(ByteArray(8193))
        assertFailsWith<IllegalArgumentException> { stream.readBytesLimited(8192) }
        assertEquals(8192, ByteArrayInputStream(ByteArray(8192)).readBytesLimited(8192).size)
    }

    @Test
    fun previewRejectsOtherFilesAndFutureVersions() {
        assertFailsWith<IllegalArgumentException> { backupPreview("{}".toByteArray()) }
        assertFailsWith<IllegalArgumentException> {
            backupPreview("""{"format":"yuhaiin-config","version":2}""".toByteArray())
        }
        assertFailsWith<java.nio.charset.MalformedInputException> {
            backupPreview(byteArrayOf(0xc3.toByte()))
        }
    }
}
