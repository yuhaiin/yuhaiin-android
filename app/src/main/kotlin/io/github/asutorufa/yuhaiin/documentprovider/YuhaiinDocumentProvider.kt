package io.github.asutorufa.yuhaiin.documentprovider

import android.content.Context
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
import java.util.UUID

class YuhaiinDocumentProvider : DocumentsProvider() {
    companion object {
        private const val ALL_MIME_TYPES = "*/*"
        private const val DOCUMENT_ID_PREFIX = "doc:"
        private const val DOCUMENT_ID_PREFS = "yuhaiin_document_provider_ids"
        private const val ID_KEY_PREFIX = "id:"
        private const val PATH_KEY_PREFIX = "path:"
        private const val SEARCH_RESULT_LIMIT = 50

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
    private val rootDocumentId by lazy { canonicalBaseDir.path }
    private val documentIds by lazy {
        val providerContext = context ?: throw IllegalStateException("Context is null")
        providerContext.getSharedPreferences(DOCUMENT_ID_PREFS, Context.MODE_PRIVATE)
    }
    private val documentIdLock = Any()

    override fun onCreate(): Boolean = baseDir.isDirectory || baseDir.mkdirs()

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        addColumn(result, row, Root.COLUMN_ROOT_ID, rootDocumentId)
        addColumn(result, row, Root.COLUMN_DOCUMENT_ID, rootDocumentId)
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
        addColumn(result, row, Root.COLUMN_AVAILABLE_BYTES, canonicalBaseDir.usableSpace)
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
        val parentDocumentId = rootId ?: rootDocumentId
        setChildNotificationUri(result, parentDocumentId)

        val parent = getFileForDocId(parentDocumentId)
        if (!parent.isDirectory) return result

        val normalizedQuery = query.orEmpty().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return result

        val pending = ArrayDeque<File>()
        val visited = HashSet<String>()
        visited.add(parent.path)
        parent.listFiles()?.forEach(pending::addLast)

        while (!pending.isEmpty() && result.count < SEARCH_RESULT_LIMIT) {
            val file = canonicalListedFile(pending.removeFirst()) ?: continue
            if (!visited.add(file.path)) continue

            if (file.name.lowercase(Locale.ROOT).contains(normalizedQuery)) {
                includeFile(result, null, file)
                if (result.count >= SEARCH_RESULT_LIMIT) break
            }
            if (file.isDirectory) file.listFiles()?.forEach(pending::addLast)
        }
        return result
    }

    private fun getFileForDocId(docId: String): File {
        val file = when {
            docId == rootDocumentId -> canonicalBaseDir
            docId.startsWith(DOCUMENT_ID_PREFIX) -> {
                val relativePath = synchronized(documentIdLock) {
                    documentIds.getString(idKey(docId), null)
                } ?: throw FileNotFoundException("Unknown document id $docId")
                resolveRelativePath(relativePath)
            }
            else -> try {
                // Compatibility with path-based IDs shared by older versions.
                File(docId).canonicalFile
            } catch (error: IOException) {
                throw FileNotFoundException("Invalid document id $docId").apply {
                    initCause(error)
                }
            }
        }

        if (!isInsideBase(file) || !file.exists()) {
            throw FileNotFoundException("Document $docId not found")
        }
        return file
    }

    private fun getDocIdForFile(file: File): String {
        val canonicalFile = canonicalDocumentFile(file)
        if (!canonicalFile.exists()) {
            throw FileNotFoundException("Document ${canonicalFile.path} not found")
        }
        if (canonicalFile == canonicalBaseDir) return rootDocumentId

        val relativePath = relativePathOf(canonicalFile)
        synchronized(documentIdLock) {
            val existing = documentIds.getString(pathKey(relativePath), null)
            if (existing != null && documentIds.getString(idKey(existing), null) == relativePath) {
                return existing
            }

            val documentId = DOCUMENT_ID_PREFIX + UUID.randomUUID()
            if (!documentIds.edit()
                    .putString(idKey(documentId), relativePath)
                    .putString(pathKey(relativePath), documentId)
                    .commit()
            ) {
                throw IllegalStateException("Failed to persist document id")
            }
            return documentId
        }
    }

    private fun canonicalDocumentFile(file: File): File {
        val canonicalFile = try {
            file.canonicalFile
        } catch (error: IOException) {
            throw FileNotFoundException("Invalid document file ${file.path}").apply {
                initCause(error)
            }
        }
        if (!isInsideBase(canonicalFile)) {
            throw FileNotFoundException("Document is outside the provider directory")
        }
        return canonicalFile
    }

    private fun canonicalListedFile(file: File): File? {
        val canonicalFile = try {
            file.canonicalFile
        } catch (_: IOException) {
            return null
        }
        if (!isInsideBase(canonicalFile)) return null

        // Do not follow symlinks: rename/delete must operate on the entry shown to the user.
        if (file.absoluteFile.path != canonicalFile.path) return null
        return canonicalFile
    }

    private fun resolveRelativePath(relativePath: String): File {
        if (
            relativePath.isEmpty() ||
            relativePath.startsWith("/") ||
            relativePath.split('/').any { it == "." || it == ".." }
        ) {
            throw FileNotFoundException("Invalid stored document path")
        }
        return canonicalDocumentFile(File(canonicalBaseDir, relativePath))
    }

    private fun relativePathOf(file: File): String {
        val canonicalFile = canonicalDocumentFile(file)
        if (canonicalFile == canonicalBaseDir) return ""

        val basePath = canonicalBaseDir.path + File.separator
        if (!canonicalFile.path.startsWith(basePath)) {
            throw FileNotFoundException("Document is outside the provider directory")
        }
        return canonicalFile.path.substring(basePath.length).replace(File.separatorChar, '/')
    }

    private fun isInsideBase(file: File): Boolean {
        val basePath = canonicalBaseDir.path
        return file.path == basePath || file.path.startsWith(basePath + File.separator)
    }

    private fun idKey(documentId: String) = ID_KEY_PREFIX + documentId
    private fun pathKey(relativePath: String) = PATH_KEY_PREFIX + relativePath

    private fun updateDocumentPathsAfterRename(oldRelativePath: String, newRelativePath: String) {
        synchronized(documentIdLock) {
            val moved = documentIds.all.mapNotNull { (key, value) ->
                if (!key.startsWith(ID_KEY_PREFIX) || value !is String) return@mapNotNull null
                if (value != oldRelativePath && !value.startsWith("$oldRelativePath/")) {
                    return@mapNotNull null
                }
                val documentId = key.removePrefix(ID_KEY_PREFIX)
                val suffix = value.removePrefix(oldRelativePath)
                documentId to (newRelativePath + suffix)
            }
            if (moved.isEmpty()) return

            val movedIds = moved.mapTo(HashSet()) { it.first }
            val editor = documentIds.edit()
            moved.forEach { (documentId, relativePath) ->
                documentIds.getString(idKey(documentId), null)?.let {
                    editor.remove(pathKey(it))
                }
                documentIds.getString(pathKey(relativePath), null)
                    ?.takeUnless(movedIds::contains)
                    ?.let { staleId -> editor.remove(idKey(staleId)) }

                editor.putString(idKey(documentId), relativePath)
                editor.putString(pathKey(relativePath), documentId)
            }
            if (!editor.commit()) {
                throw IllegalStateException("Failed to update document ids after rename")
            }
        }
    }

    private fun removeDocumentIdsUnder(relativePath: String) {
        synchronized(documentIdLock) {
            val editor = documentIds.edit()
            documentIds.all.forEach { (key, value) ->
                if (!key.startsWith(ID_KEY_PREFIX) || value !is String) return@forEach
                if (value != relativePath && !value.startsWith("$relativePath/")) return@forEach
                editor.remove(key)
                editor.remove(pathKey(value))
            }
            editor.apply()
        }
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return "application/octet-stream"
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(
            result,
            documentId ?: throw FileNotFoundException("Document is missing"),
            null,
        )
        return result
    }

    private fun includeFile(result: MatrixCursor, documentId: String?, file: File?) {
        val documentFile = if (documentId != null) {
            getFileForDocId(documentId)
        } else {
            file?.let(::canonicalDocumentFile)
                ?: throw FileNotFoundException("Document file is missing")
        }

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

        val row = result.newRow()
        addColumn(result, row, DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocIdForFile(documentFile))
        addColumn(result, row, DocumentsContract.Document.COLUMN_DISPLAY_NAME, documentFile.name)
        addColumn(result, row, DocumentsContract.Document.COLUMN_SIZE, documentFile.length())
        addColumn(result, row, DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(documentFile))
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
        val documentId = parentDocumentId
            ?: throw FileNotFoundException("Parent document is missing")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        setChildNotificationUri(result, documentId)

        val parent = getFileForDocId(documentId)
        if (!parent.isDirectory) throw FileNotFoundException("Parent document is not a directory")

        parent.listFiles()
            ?.asSequence()
            ?.mapNotNull(::canonicalListedFile)
            ?.filter { it.parentFile == parent }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.forEach { includeFile(result, null, it) }
        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = getFileForDocId(
            documentId ?: throw FileNotFoundException("Document is missing"),
        )
        if (file.isDirectory) throw FileNotFoundException("Cannot open a directory as a file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode ?: "r"))
    }

    override fun createDocument(
        parentDocumentId: String?,
        mimeType: String?,
        displayName: String?,
    ): String {
        val parent = getFileForDocId(
            parentDocumentId ?: throw FileNotFoundException("Parent document is missing"),
        )
        if (!parent.isDirectory || !parent.canWrite()) {
            throw FileNotFoundException("Parent document is not writable")
        }

        val safeName = resolveCreateDisplayName(mimeType, displayName)
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

        val documentId = getDocIdForFile(target)
        notifyChanged(parent)
        return documentId
    }

    override fun renameDocument(documentId: String?, displayName: String?): String? {
        val requestedId = documentId ?: throw FileNotFoundException("Document is missing")
        val source = getFileForDocId(requestedId)
        if (source == canonicalBaseDir) {
            throw FileNotFoundException("Cannot rename the root document")
        }

        // Migrate old path-based IDs before moving so the returned ID is durable.
        val stableDocumentId = getDocIdForFile(source)
        val oldRelativePath = relativePathOf(source)
        val parent = source.parentFile ?: throw FileNotFoundException("Parent document is missing")
        val safeName = validateDisplayName(displayName)
        val target = File(parent, safeName).canonicalFile
        if (!isInsideBase(target) || target.parentFile != parent) {
            throw FileNotFoundException("Invalid document name")
        }
        if (target.exists()) throw FileNotFoundException("Document already exists")
        if (!source.renameTo(target)) throw FileNotFoundException("Failed to rename document")

        try {
            updateDocumentPathsAfterRename(oldRelativePath, relativePathOf(target))
        } catch (error: Exception) {
            // Keep the filesystem and ID registry in sync if persistence fails.
            target.renameTo(source)
            throw FileNotFoundException("Failed to persist renamed document").apply {
                initCause(error)
            }
        }
        notifyChanged(parent)

        // Opaque IDs remain valid across rename, so the framework must not migrate/revoke them.
        // Legacy path IDs become invalid and need to be migrated to the new stable ID.
        return if (requestedId.startsWith(DOCUMENT_ID_PREFIX)) null else stableDocumentId
    }

    override fun deleteDocument(documentId: String?) {
        val requestedId = documentId ?: throw FileNotFoundException("Document is missing")
        val file = getFileForDocId(requestedId)
        if (file == canonicalBaseDir) throw FileNotFoundException("Cannot delete the root document")

        val relativePath = relativePathOf(file)
        val revokeIds = collectDocumentIdsForDeletion(file)
        val parent = file.parentFile

        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document $requestedId")
        }

        // The framework revokes requestedId after return; descendants are our responsibility.
        revokeIds.forEach(::revokeDocumentPermission)
        removeDocumentIdsUnder(relativePath)
        notifyChanged(parent)
    }

    private fun collectDocumentIdsForDeletion(root: File): Set<String> {
        val result = LinkedHashSet<String>()
        val pending = ArrayDeque<File>()
        val visited = HashSet<String>()
        pending.add(root)

        while (!pending.isEmpty()) {
            val listedFile = pending.removeFirst()
            val file = if (listedFile == root) {
                canonicalDocumentFile(listedFile)
            } else {
                canonicalListedFile(listedFile) ?: continue
            }
            if (!visited.add(file.path)) continue

            result.add(getDocIdForFile(file))
            result.add(file.path) // Revoke grants issued by path-based older versions.

            if (file.isDirectory) file.listFiles()?.forEach(pending::addLast)
        }
        return result
    }

    private fun resolveCreateDisplayName(mimeType: String?, displayName: String?): String {
        val safeName = validateDisplayName(displayName)
        if (mimeType.isNullOrBlank() || mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            return safeName
        }
        if (safeName.substringAfterLast('.', "").isNotEmpty()) return safeName

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: return safeName
        return "$safeName.$extension"
    }

    private fun validateDisplayName(displayName: String?): String {
        val name = displayName ?: throw FileNotFoundException("Document name is missing")
        if (
            name.isEmpty() ||
            name == "." ||
            name == ".." ||
            name.contains('/') ||
            name.contains('\\') ||
            name.contains('\u0000')
        ) {
            throw FileNotFoundException("Invalid document name")
        }
        return name
    }

    private fun setChildNotificationUri(result: MatrixCursor, documentId: String) {
        val providerContext = context ?: return
        result.setNotificationUri(
            providerContext.contentResolver,
            DocumentsContract.buildChildDocumentsUri(
                BuildConfig.DOCUMENTS_AUTHORITY,
                documentId,
            ),
        )
    }

    private fun notifyChanged(parent: File?) {
        val providerContext = context ?: return
        val parentFile = parent ?: canonicalBaseDir
        val resolver = providerContext.contentResolver
        val authority = BuildConfig.DOCUMENTS_AUTHORITY

        // Notify both the new opaque ID and legacy path ID while old persisted URIs exist.
        linkedSetOf(getDocIdForFile(parentFile), parentFile.path).forEach { documentId ->
            resolver.notifyChange(
                DocumentsContract.buildChildDocumentsUri(authority, documentId),
                null,
            )
        }
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
