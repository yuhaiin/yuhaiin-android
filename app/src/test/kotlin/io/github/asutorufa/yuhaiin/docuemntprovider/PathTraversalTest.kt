package io.github.asutorufa.yuhaiin.docuemntprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PathTraversalTest {

    @Test
    fun testIsChild() {
        val provider = YuhaiinDocumentProvider()
        val tempDir = Files.createTempDirectory("yuhaiin_test").toFile().canonicalFile
        val otherDir = Files.createTempDirectory("yuhaiin_other").toFile().canonicalFile
        val partialMatchDir = File(tempDir.parentFile, tempDir.name + "_extra")
        try {
            val subDir = File(tempDir, "subdir")
            subDir.mkdir()
            val fileInSubDir = File(subDir, "file.txt")
            fileInSubDir.createNewFile()

            val otherFile = File(otherDir, "other.txt")
            otherFile.createNewFile()

            // Construct a path that looks like it's inside tempDir but isn't after canonicalization
            val traversalFile = File(tempDir, "../" + otherDir.name + "/other.txt")

            assertTrue("Should be child of itself", provider.isChild(tempDir, tempDir))
            assertTrue("Should be child of tempDir", provider.isChild(tempDir, subDir))
            assertTrue("Should be child of tempDir", provider.isChild(tempDir, fileInSubDir))

            assertFalse("Should not be child of otherDir", provider.isChild(tempDir, otherDir))
            assertFalse("Should not be child of otherDir", provider.isChild(tempDir, otherFile))
            assertFalse("Traversal should be blocked: ${traversalFile.path}", provider.isChild(tempDir, traversalFile))

            partialMatchDir.mkdir()
            assertFalse("Partial name match should be blocked: ${partialMatchDir.path}", provider.isChild(tempDir, partialMatchDir))
        } finally {
            tempDir.deleteRecursively()
            otherDir.deleteRecursively()
            partialMatchDir.deleteRecursively()
        }
    }
}
