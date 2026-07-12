package io.github.asutorufa.yuhaiin.documentprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale

class YuhaiinDocumentProvider : DocumentsProvider() {
    companion object {
        private const val ALL_MIME_TYPES = "*/*"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }

    private val baseDir by lazy {
        val providerContext = context ?: throw IllegalStateException("Context is null")
        providerContext.getExternalFilesDir("yuhaiin")
            ?: throw IllegalStateException("External files directory is unavailable")
    }

    private val canonicalBaseDir by lazy { baseDir.canonicalFile }

    override fun onCreate(): Boolean {
        return baseDir.isDirectory || baseDir.mkdirs()
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        val rootId = getDocIdForFile(baseDir)
        addColumn(result, row, Root.COLUMN_ROOT_ID, rootId)
        addColumn(result, row, Root.COLUMN_DOCUMENT_ID, rootId)
        addColumn(result, row, Root.COLUMN_SUMMARY, null)
        addColumn(
            result,
            row,
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD,
        )
        addColumn(
            result,
            row,
            Root.COLUMN_TITLE,
            context?.getString(R.string.app_name) ?: "yuhaiin",
        )
        addColumn(result, row, Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
        addColumn(result, row, Root.COLUMN_AVAILABLE_BYTES, baseDir.usableSpace)
        addColumn(result, row, Root.COLUMN_ICON, R.mipmap.ic_launcher_v2_round)
        return result
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (parentDocumentId == null || documentId == null) return false
        return try {
            val parent = getFileForDocId(parentDocumentId)
            val child = getFileForDocId(documentId)
            child != parent && child.path.startsWith(parent.path + File.separator)
        } catch (_: FileNotFoundException) {
            false
        }
    }

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = rootId?.let(::getFileForDocId) ?: canonicalBaseDir
        if (!parent.isDirectory) return result

        val normalizedQuery = query.orEmpty().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return result

        val pending = ArrayDeque<File>()
        pending.add(parent)
        while (!pending.isEmpty() && result.count < 50) {
            val file = try {
                pending.removeFirst().canonicalFile
            } catch (_: IOException) {
                continue
            }
            if (!isInsideBase(file)) continue
            if (file.isDirectory) {
                file.listFiles()?.forEach(pending::addLast)
            } else if (file.name.lowercase(Locale.ROOT).contains(normalizedQuery)) {
                includeFile(result, null, file)
            }
        }
        return result
    }

    private fun getFileForDocId(docId: String): File {
        val file = try {
            File(docId).canonicalFile
        } catch (error: IOException) {
            throw FileNotFoundException("Invalid document id $docId").apply { initCause(error) }
        }
        if (!isInsideBase(file) || !file.exists()) {
            throw FileNotFoundException("Document $docId not found")
        }
        return file
    }

    private fun getDocIdForFile(file: File): String {
        val canonicalFile = try {
            file.canonicalFile
        } catch (error: IOException) {
            throw FileNotFoundException("Invalid document file ${file.path}").apply { initCause(error) }
        }
        if (!isInsideBase(canonicalFile)) {
            throw FileNotFoundException("Document is outside the provider directory")
        }
        return canonicalFile.path
    }

    private fun isInsideBase(file: File): Boolean {
        val basePath = canonicalBaseDir.path
        return file.path == basePath || file.path.startsWith(basePath + File.separator)
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val lastDot = file.name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = file.name.substring(lastDot + 1).lowercase(Locale.ROOT)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return "application/octet-stream"
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    private fun includeFile(result: MatrixCursor, documentId: String?, file: File?) {
        val documentFile = if (documentId != null) {
            getFileForDocId(documentId)
        } else {
            file?.let { getFileForDocId(getDocIdForFile(it)) }
                ?: throw FileNotFoundException("Document file is missing")
        }
        val docId = getDocIdForFile(documentFile)
        var flags = 0
        if (documentFile.isDirectory) {
            if (documentFile.canWrite()) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            }
        } else if (documentFile.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        if (documentFile != canonicalBaseDir && documentFile.parentFile?.canWrite() == true) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        val mimeType = getMimeType(documentFile)
        if (mimeType.startsWith("image/")) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        }

        val row = result.newRow()
        addColumn(result, row, DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        addColumn(result, row, DocumentsContract.Document.COLUMN_DISPLAY_NAME, documentFile.name)
        addColumn(result, row, DocumentsContract.Document.COLUMN_SIZE, documentFile.length())
        addColumn(result, row, DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        addColumn(
            result,
            row,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            documentFile.lastModified(),
        )
        addColumn(result, row, DocumentsContract.Document.COLUMN_FLAGS, flags)
        addColumn(result, row, DocumentsContract.Document.COLUMN_ICON, R.mipmap.ic_launcher_v2_round)
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId ?: throw FileNotFoundException("Parent document is missing"))
        if (!parent.isDirectory) throw FileNotFoundException("Parent document is not a directory")
        parent.listFiles()
            ?.asSequence()
            ?.mapNotNull { file ->
                try {
                    file.canonicalFile.takeIf(::isInsideBase)
                } catch (_: IOException) {
                    null
                }
            }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.forEach { includeFile(result, null, it) }
        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException("Document is missing"))
        if (file.isDirectory) throw FileNotFoundException("Cannot open a directory as a file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode ?: "r"))
    }

    override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
        val parent = getFileForDocId(parentDocumentId ?: throw FileNotFoundException("Parent document is missing"))
        if (!parent.isDirectory || !parent.canWrite()) {
            throw FileNotFoundException("Parent document is not writable")
        }
        val safeName = validateDisplayName(displayName)
        val target = File(parent, safeName).canonicalFile
        if (!isInsideBase(target) || target.parentFile != parent) {
            throw FileNotFoundException("Invalid document name")
        }
        if (target.exists()) throw FileNotFoundException("Document already exists")

        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            target.mkdir()
        } else {
            target.createNewFile()
        }
        if (!created) throw FileNotFoundException("Failed to create document $safeName")
        notifyChanged(parent)
        return getDocIdForFile(target)
    }

    override fun renameDocument(documentId: String?, displayName: String?): String {
        val source = getFileForDocId(documentId ?: throw FileNotFoundException("Document is missing"))
        if (source == canonicalBaseDir) throw FileNotFoundException("Cannot rename the root document")
        val safeName = validateDisplayName(displayName)
        val target = File(source.parentFile ?: throw FileNotFoundException("Parent document is missing"), safeName)
            .canonicalFile
        if (!isInsideBase(target) || target.parentFile != source.parentFile) {
            throw FileNotFoundException("Invalid document name")
        }
        if (target.exists()) throw FileNotFoundException("Document already exists")
        if (!source.renameTo(target)) throw FileNotFoundException("Failed to rename document")
        notifyChanged(source.parentFile)
        return getDocIdForFile(target)
    }

    override fun deleteDocument(documentId: String?) {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException("Document is missing"))
        if (file == canonicalBaseDir) throw FileNotFoundException("Cannot delete the root document")
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document $documentId")
        }
        notifyChanged(file.parentFile)
    }

    private fun validateDisplayName(displayName: String?): String {
        val name = displayName ?: throw FileNotFoundException("Document name is missing")
        if (name.isEmpty() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.contains('\u0000')
        ) {
            throw FileNotFoundException("Invalid document name")
        }
        return name
    }

    private fun notifyChanged(parent: File?) {
        val providerContext = context ?: return
        val parentFile = parent ?: canonicalBaseDir
        val resolver = providerContext.contentResolver
        val authority = BuildConfig.DOCUMENTS_AUTHORITY
        resolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority, getDocIdForFile(parentFile)),
            null,
        )
        resolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
    }

    private fun addColumn(
        result: MatrixCursor,
        row: MatrixCursor.RowBuilder,
        column: String,
        value: Any?,
    ) {
        if (result.getColumnIndex(column) >= 0) row.add(column, value)
    }
}
