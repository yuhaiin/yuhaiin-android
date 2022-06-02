package io.github.asutorufa.yuhaiin.docuemntprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import io.github.asutorufa.yuhaiin.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*


@RequiresApi(Build.VERSION_CODES.N)
class YuhaiinDocumentProvider : DocumentsProvider() {
    companion object {
        private const val ALL_MIME_TYPES = "*/*"

        // The default columns to return information about a root if no specific
        // columns are requested in a query.
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        // The default columns to return information about a document if no specific
        // columns are requested in a query.
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    private val baseDir by lazy {
        context?.getExternalFilesDir("yuhaiin") ?: throw IllegalStateException("Context is null")
    }


    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d("yuhaiin", "queryRoots: ....")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, baseDir)
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(baseDir))
        row.add(Root.COLUMN_SUMMARY, null)
        row.add(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD
        )
        row.add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "yuhaiin")
        row.add(Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
        row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return result
    }

    /**
     * Get the document id given a file. This document id must be consistent across time as other
     * applications may save the ID and use it to reference documents later.
     *
     *
     * The reverse of @{link #getFileForDocId}.
     */
    private fun getDocIdForFile(file: File): String? {
        return file.absolutePath
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (documentId != null) {
            return parentDocumentId?.let { documentId.startsWith(it) } ?: false
        }

        return false
    }

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?
    ): Cursor {
        val result = MatrixCursor(
            projection ?: DEFAULT_DOCUMENT_PROJECTION
        )
        val parent = getFileForDocId(rootId!!)

        // This example implementation searches file names for the query and doesn't rank search
        // results, so we can stop as soon as we find a sufficient number of matches.  Other
        // implementations might rank results and use other data about files, rather than the file
        // name, to produce a match.

        // This example implementation searches file names for the query and doesn't rank search
        // results, so we can stop as soon as we find a sufficient number of matches.  Other
        // implementations might rank results and use other data about files, rather than the file
        // name, to produce a match.
        val pending = LinkedList<File>()
        pending.add(parent)

        val maxSearchResults = 50
        while (!pending.isEmpty() && result.count < maxSearchResults) {
            val file = pending.removeFirst()
            // Avoid directories outside the $HOME directory linked with symlinks (to avoid e.g. search
            // through the whole SD card).
            val isInsideHome: Boolean = try {
                file.canonicalPath.startsWith(baseDir.toString())
            } catch (e: IOException) {
                true
            }
            if (isInsideHome) {
                if (file.isDirectory) {
                    file.listFiles()?.let { Collections.addAll(pending, *it) }
                } else {
                    if (file.name.lowercase(Locale.getDefault()).contains(query!!)) {
                        includeFile(result, null, file)
                    }
                }
            }
        }

        return result
    }

    /**
     * Get the file given a document id (the reverse of [.getDocIdForFile]).
     */
    private fun getFileForDocId(docId: String): File {
        val f = File(docId)
        if (!f.exists()) throw FileNotFoundException(f.absolutePath + " not found")
        return f
    }

    private fun getMimeType(file: File): String {
        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val name = file.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase(Locale.getDefault())
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) return mime
            }
            "application/octet-stream"
        }
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docID  the document ID representing the desired file (may be null if given file)
     * @param fil   the File object representing the desired file (may be null if given docID)
     */
    private fun includeFile(result: MatrixCursor, docID: String?, fil: File?) {
        var docId: String? = docID
        var file = fil
        if (docId == null) {
            docId = getDocIdForFile(file!!)
        } else {
            file = getFileForDocId(docId)
        }

        var flags = 0
        if (file.isDirectory) {
            if (file.canWrite()) flags = 0 or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = 0 or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        if (file.parentFile?.canWrite() == true) flags =
            flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        val displayName = file.name
        val mimeType = getMimeType(file)
        if (mimeType.startsWith("image/")) flags =
            flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL


        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_ICON, R.mipmap.ic_launcher)
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId!!)
        for (file in parent.listFiles()!!) {
            includeFile(result, null, file!!)
        }
        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = documentId?.let { getFileForDocId(it) }
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }


    override fun getDocumentType(documentId: String?): String {
        val file = getFileForDocId(documentId!!)
        return getMimeType(file)
    }

    override fun deleteDocument(documentId: String?) {
        val file = getFileForDocId(documentId!!)
        if (!file.delete()) {
            throw FileNotFoundException("Failed to delete document with id $documentId")
        }
    }
}