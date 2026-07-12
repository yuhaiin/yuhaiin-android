package io.github.asutorufa.yuhaiin.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

enum class UpdateChannel(val value: String) {
    STABLE("stable"),
    BETA("beta"),
    MAIN("main"),
}

enum class UpdateStage {
    IDLE,
    CHECKING,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    COMPLETED,
    ERROR,
}

data class AndroidUpdateState(
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val checking: Boolean = false,
    val release: AndroidUpdateRelease? = null,
    val stage: UpdateStage = UpdateStage.IDLE,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
    val reason: String? = null,
)

data class AndroidUpdateRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val publishedAt: String,
    val assetName: String,
    val assetUrl: String,
    val checksumUrl: String?,
    val assetSize: Long,
)

class UpdateManager(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val preferences = context.getSharedPreferences("software_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        AndroidUpdateState(channel = loadChannel())
    )
    @Volatile
    private var proxyBinder: IYuhaiinVpnBinder? = null

    init {
        cleanupOldDownloads()
    }

    val state: StateFlow<AndroidUpdateState> = _state.asStateFlow()

    fun setProxyBinder(binder: IYuhaiinVpnBinder?) {
        proxyBinder = binder
    }

    fun setChannel(channel: UpdateChannel) {
        preferences.edit().putString(CHANNEL_KEY, channel.value).apply()
        _state.value = _state.value.copy(channel = channel, release = null, reason = null, error = null)
    }

    fun check() {
        val current = _state.value
        if (current.checking || current.stage == UpdateStage.DOWNLOADING || current.stage == UpdateStage.VERIFYING || current.stage == UpdateStage.INSTALLING) return

        _state.value = current.copy(checking = true, stage = UpdateStage.CHECKING, release = null, reason = null, error = null)
        scope.launch {
            try {
                val release = fetchRelease(current.channel)
                _state.value = _state.value.copy(
                    checking = false,
                    stage = UpdateStage.IDLE,
                    release = release,
                    reason = if (release == null) context.getString(R.string.update_latest_available) else null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(checking = false, stage = UpdateStage.ERROR, error = errorMessage(e))
            }
        }
    }

    fun apply() {
        val current = _state.value
        val release = current.release ?: return
        if (current.checking || current.stage == UpdateStage.DOWNLOADING || current.stage == UpdateStage.VERIFYING || current.stage == UpdateStage.INSTALLING || current.stage == UpdateStage.COMPLETED) return

        _state.value = current.copy(stage = UpdateStage.DOWNLOADING, progress = 0, downloadedBytes = 0, totalBytes = 0, error = null)
        scope.launch {
            try {
                val apk = download(release)
                _state.value = _state.value.copy(stage = UpdateStage.VERIFYING, progress = 100)
                verifyChecksum(apk, release)
                _state.value = _state.value.copy(stage = UpdateStage.INSTALLING)
                install(apk)
                _state.value = _state.value.copy(stage = UpdateStage.COMPLETED, progress = 100)
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = UpdateStage.ERROR, error = errorMessage(e))
            }
        }
    }

    private suspend fun fetchRelease(channel: UpdateChannel): AndroidUpdateRelease? =
        withContextIo {
            val releases = fetchReleases()
            selectRelease(releases, channel)
        }

    private fun fetchReleases(): List<GitHubRelease> {
        val result = mutableListOf<GitHubRelease>()
        for (page in 1..RELEASE_PAGE_LIMIT) {
            val url = "$RELEASES_URL?per_page=100&page=$page"
            val response = proxyGet(url).toString(Charsets.UTF_8)
            val pageReleases = json.decodeFromString<List<GitHubRelease>>(response)
            result += pageReleases
            if (pageReleases.size < 100) break
        }
        return result
    }

    private fun selectRelease(releases: List<GitHubRelease>, channel: UpdateChannel): AndroidUpdateRelease? {
        val assetName = androidAssetName() ?: return null
        val candidates = releases.mapNotNull { release ->
            if (release.draft || !matchesChannel(release, channel)) return@mapNotNull null
            val asset = release.assets.firstOrNull { it.name == assetName } ?: return@mapNotNull null
            val checksum = release.assets.firstOrNull { it.name == "checksums.txt" }
            val version = releaseVersion(release, channel)
            if (channel == UpdateChannel.MAIN) {
                if (sameMainVersion(version, BuildConfig.GIT_COMMIT)) return@mapNotNull null
            } else if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) {
                return@mapNotNull null
            }
            AndroidUpdateRelease(version, release.tag, release.body.orEmpty(), release.publishedAt.orEmpty(), asset.name, asset.url, checksum?.url, asset.size)
        }

        return if (channel == UpdateChannel.MAIN) {
            candidates.maxWithOrNull(compareBy<AndroidUpdateRelease> { it.publishedAt }.thenBy { it.version })
        } else {
            candidates.maxWithOrNull(Comparator { left, right -> compareVersions(left.version, right.version) })
        }
    }

    private fun matchesChannel(release: GitHubRelease, channel: UpdateChannel): Boolean {
        val main = isMainRelease(release)
        return when (channel) {
            UpdateChannel.STABLE -> !release.prerelease && !main
            UpdateChannel.BETA -> release.prerelease && !main
            UpdateChannel.MAIN -> main
        }
    }

    private fun isMainRelease(release: GitHubRelease): Boolean =
        release.tag == "main" || release.tag.startsWith("main-") || release.name.orEmpty().startsWith("main-")

    private fun releaseVersion(release: GitHubRelease, channel: UpdateChannel): String =
        if (channel == UpdateChannel.MAIN) release.name?.takeIf { it.startsWith("main-") } ?: release.tag else release.tag

    private fun sameMainVersion(release: String, commit: String): Boolean {
        val current = commit.trim().removePrefix("main-")
        val target = release.trim().removePrefix("main-")
        return current.isNotEmpty() && target.isNotEmpty() && (current.startsWith(target) || target.startsWith(current))
    }

    private suspend fun download(release: AndroidUpdateRelease): File = coroutineScope {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        cleanupOldDownloads()
        val output = File(directory, release.assetName)
        val temporary = File(directory, "${release.assetName}.part")

        if (release.checksumUrl != null) {
            for (candidate in listOf(output, temporary)) {
                if (!candidate.isFile || candidate.length() == 0L) continue
                _state.value = _state.value.copy(
                    stage = UpdateStage.VERIFYING,
                    progress = 0,
                    downloadedBytes = candidate.length(),
                    totalBytes = candidate.length(),
                )
                try {
                    verifyChecksum(candidate, release)
                } catch (_: Exception) {
                    candidate.delete()
                    continue
                }
                if (candidate != output) {
                    output.delete()
                    if (!candidate.renameTo(output)) {
                        candidate.delete()
                        throw IllegalStateException("could not move verified APK into place")
                    }
                } else {
                    temporary.delete()
                }
                _state.value = _state.value.copy(
                    progress = 100,
                    downloadedBytes = output.length(),
                    totalBytes = output.length(),
                )
                return@coroutineScope output
            }
        }

        temporary.delete()
        val total = release.assetSize
        _state.value = _state.value.copy(progress = 0, downloadedBytes = 0, totalBytes = total)
        val downloadJob = async(Dispatchers.IO) {
            proxyDownload(release.assetUrl, temporary.absolutePath)
        }
        while (!downloadJob.isCompleted) {
            val downloaded = temporary.length()
            val progress = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0
            _state.value = _state.value.copy(progress = progress, downloadedBytes = downloaded, totalBytes = total)
            delay(250)
        }
        downloadJob.await()
        val downloaded = temporary.length()
        output.delete()
        if (!temporary.renameTo(output)) {
            temporary.delete()
            throw IllegalStateException("could not move downloaded APK into place")
        }
        _state.value = _state.value.copy(progress = 100, downloadedBytes = downloaded, totalBytes = total)
        output
    }

    private suspend fun verifyChecksum(apk: File, release: AndroidUpdateRelease) = withContextIo {
        val checksumUrl = release.checksumUrl ?: return@withContextIo
        val checksums = proxyGet(checksumUrl).toString(Charsets.UTF_8)
        val expected = checksums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && File(it[1]).name == apk.name }
            ?.first()
            ?: throw IllegalStateException("checksum for ${apk.name} is missing")
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
        if (!actual.equals(expected, ignoreCase = true)) throw IllegalStateException("APK checksum mismatch")
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            throw IllegalStateException("Allow installs from this source, then try again")
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.update_fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun loadChannel(): UpdateChannel = when (preferences.getString(CHANNEL_KEY, null)) {
        UpdateChannel.BETA.value -> UpdateChannel.BETA
        UpdateChannel.MAIN.value -> UpdateChannel.MAIN
        else -> UpdateChannel.STABLE
    }

    private fun androidAssetName(): String? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "yuhaiin-arm64-v8a-release.apk"
        "x86_64" -> "yuhaiin-x86_64-release.apk"
        else -> null
    }

    private fun errorMessage(error: Exception): String {
        val message = error.message ?: error.javaClass.simpleName
        return if (message.contains("not initialized", ignoreCase = true)) {
            "The proxy is not running. Start the proxy and try again."
        } else {
            message
        }
    }

    private fun proxyGet(url: String): ByteArray =
        proxyBinder?.proxyGet(url)
            ?: throw IllegalStateException("The proxy is not running. Start the proxy and try again.")

    private fun proxyDownload(url: String, destination: String) {
        val binder = proxyBinder
            ?: throw IllegalStateException("The proxy is not running. Start the proxy and try again.")
        binder.proxyDownload(url, destination)
    }

    private fun cleanupOldDownloads() {
        val directory = File(context.cacheDir, "updates")
        val cutoff = System.currentTimeMillis() - DOWNLOAD_RETENTION_MS
        directory.listFiles()?.forEach { file ->
            if ((file.name.endsWith(".apk") || file.name.endsWith(".part")) && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )

    companion object {
        private const val CHANNEL_KEY = "channel"
        private const val RELEASES_URL = "https://api.github.com/repos/yuhaiin/yuhaiin-android/releases"
        private const val RELEASE_PAGE_LIMIT = 10
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_RETENTION_MS = 24 * 60 * 60 * 1000L

        private fun compareVersions(left: String, right: String): Int {
            val a = parseVersion(left) ?: return -1
            val b = parseVersion(right) ?: return 1
            for (index in 0..2) {
                if (a.numbers[index] != b.numbers[index]) return a.numbers[index].compareTo(b.numbers[index])
            }
            if (a.prerelease == b.prerelease) return 0
            if (a.prerelease == null) return 1
            if (b.prerelease == null) return -1
            return a.prerelease.compareTo(b.prerelease)
        }

        private fun parseVersion(value: String): ParsedVersion? {
            val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?").find(value) ?: return null
            return ParsedVersion(
                intArrayOf(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()),
                match.groupValues[4].ifBlank { null },
            )
        }

        private data class ParsedVersion(val numbers: IntArray, val prerelease: String?)
    }
}
