package io.github.asutorufa.yuhaiin.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.R
import java.io.File
import java.io.FileInputStream
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
    WAITING_PERMISSION,
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

@Serializable
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
    private val _state = MutableStateFlow(restoreState())
    @Volatile private var proxyBinder: IYuhaiinVpnBinder? = null

    init {
        scope.launch(Dispatchers.IO) { cleanupOldDownloads() }
    }

    val state: StateFlow<AndroidUpdateState> = _state.asStateFlow()

    fun setProxyBinder(binder: IYuhaiinVpnBinder?) {
        proxyBinder = binder
    }

    fun setChannel(channel: UpdateChannel) {
        if (_state.value.checking) return
        if (
            _state.value.stage in
                listOf(
                    UpdateStage.DOWNLOADING,
                    UpdateStage.VERIFYING,
                    UpdateStage.INSTALLING,
                    UpdateStage.WAITING_PERMISSION,
                )
        )
            return
        preferences
            .edit()
            .putString(CHANNEL_KEY, channel.value)
            .remove("release")
            .remove("phase")
            .remove("verified_checksum")
            .apply()
        _state.value =
            _state.value.copy(
                channel = channel,
                stage = UpdateStage.IDLE,
                release = null,
                reason = null,
                error = null,
            )
    }

    fun check() {
        val current = _state.value
        if (
            current.checking ||
                current.stage == UpdateStage.DOWNLOADING ||
                current.stage == UpdateStage.VERIFYING ||
                current.stage == UpdateStage.INSTALLING ||
                current.stage == UpdateStage.WAITING_PERMISSION
        )
            return

        _state.value =
            current.copy(
                checking = true,
                stage = UpdateStage.CHECKING,
                release = null,
                reason = null,
                error = null,
            )
        scope.launch {
            try {
                val release = fetchRelease(current.channel)
                preferences.edit().remove("phase").remove("release").apply()
                _state.value =
                    _state.value.copy(
                        checking = false,
                        stage = UpdateStage.IDLE,
                        release = release,
                        reason =
                            if (release == null) context.getString(R.string.update_latest_available)
                            else null,
                    )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        checking = false,
                        stage = UpdateStage.ERROR,
                        error = errorMessage(e),
                    )
            }
        }
    }

    fun apply() {
        val current = _state.value
        val release = current.release ?: return
        if (
            current.checking ||
                current.stage == UpdateStage.DOWNLOADING ||
                current.stage == UpdateStage.VERIFYING ||
                current.stage == UpdateStage.INSTALLING ||
                current.stage == UpdateStage.WAITING_PERMISSION ||
                current.stage == UpdateStage.COMPLETED
        )
            return

        persistRelease(release)
        _state.value =
            current.copy(
                stage = UpdateStage.DOWNLOADING,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0,
                error = null,
            )
        scope.launch {
            try {
                val apk = download(release)
                _state.value = _state.value.copy(stage = UpdateStage.VERIFYING, progress = 100)
                verifyChecksum(apk, release)
                beginInstall(apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = UpdateStage.ERROR, error = errorMessage(e))
            }
        }
    }

    fun onInstallResult(status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            preferences.edit().remove("session_id").remove("pending_apk").remove("phase").apply()
        }
        _state.value =
            when (status) {
                PackageInstaller.STATUS_SUCCESS ->
                    _state.value.copy(
                        stage = UpdateStage.COMPLETED,
                        progress = 100,
                        reason = context.getString(R.string.update_install_completed),
                        error = null,
                    )

                PackageInstaller.STATUS_PENDING_USER_ACTION ->
                    _state.value.copy(
                        stage = UpdateStage.INSTALLING,
                        reason = context.getString(R.string.update_install_confirmation),
                        error = null,
                    )

                else ->
                    _state.value.copy(
                        stage = UpdateStage.ERROR,
                        error =
                            message?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.update_install_failed),
                    )
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
            val url =
                "$RELEASES_URL?per_page=100&page=$page&update_cache_bust=${System.currentTimeMillis()}"
            val response = proxyGet(url).toString(Charsets.UTF_8)
            val pageReleases = json.decodeFromString<List<GitHubRelease>>(response)
            result += pageReleases
            if (pageReleases.size < 100) break
        }
        return result
    }

    private fun selectRelease(
        releases: List<GitHubRelease>,
        channel: UpdateChannel,
    ): AndroidUpdateRelease? {
        val assetName = androidAssetName() ?: return null
        val candidates = releases.mapNotNull { release ->
            if (release.draft || !matchesChannel(release, channel)) return@mapNotNull null
            val asset =
                release.assets.firstOrNull { it.name == assetName } ?: return@mapNotNull null
            val checksum = release.assets.firstOrNull { it.name == "checksums.txt" }
            val version = releaseVersion(release, channel)
            if (channel == UpdateChannel.MAIN) {
                if (sameMainVersion(version, BuildConfig.GIT_COMMIT)) return@mapNotNull null
            } else if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) {
                return@mapNotNull null
            }
            AndroidUpdateRelease(
                version,
                release.tag,
                release.body.orEmpty(),
                release.publishedAt.orEmpty(),
                asset.name,
                asset.url,
                checksum?.url,
                asset.size,
            )
        }

        return if (channel == UpdateChannel.MAIN) {
            candidates.maxWithOrNull(
                compareBy<AndroidUpdateRelease> { it.publishedAt }.thenBy { it.version }
            )
        } else {
            candidates.maxWithOrNull(
                Comparator { left, right -> compareVersions(left.version, right.version) }
            )
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
        release.tag == "main" ||
            release.tag.startsWith("main-") ||
            release.name.orEmpty().startsWith("main-")

    private fun releaseVersion(release: GitHubRelease, channel: UpdateChannel): String =
        if (channel == UpdateChannel.MAIN)
            release.name?.takeIf { it.startsWith("main-") } ?: release.tag
        else release.tag

    private fun sameMainVersion(release: String, commit: String): Boolean {
        val current = commit.trim().removePrefix("main-")
        val target = release.trim().removePrefix("main-")
        return current.isNotEmpty() &&
            target.isNotEmpty() &&
            (current.startsWith(target) || target.startsWith(current))
    }

    private suspend fun download(release: AndroidUpdateRelease): File = coroutineScope {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        cleanupOldDownloads()
        val output = File(directory, release.assetName)
        val temporary = File(directory, "${release.assetName}.part")

        if (release.checksumUrl != null) {
            for (candidate in listOf(output, temporary)) {
                if (!candidate.isFile || candidate.length() == 0L) continue
                _state.value =
                    _state.value.copy(
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
                _state.value =
                    _state.value.copy(
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
        val downloadJob =
            async(Dispatchers.IO) {
                proxyDownload(release.assetUrl, temporary.absolutePath)
            }
        while (!downloadJob.isCompleted) {
            val downloaded = temporary.length()
            val progress = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 99) else 0
            _state.value =
                _state.value.copy(
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                )
            delay(250)
        }
        downloadJob.await()
        val downloaded = temporary.length()
        output.delete()
        if (!temporary.renameTo(output)) {
            temporary.delete()
            throw IllegalStateException("could not move downloaded APK into place")
        }
        _state.value =
            _state.value.copy(progress = 100, downloadedBytes = downloaded, totalBytes = total)
        output
    }

    private suspend fun verifyChecksum(apk: File, release: AndroidUpdateRelease) = withContextIo {
        val checksumUrl = release.checksumUrl ?: error("This release has no APK checksum")
        val checksums = proxyGet(checksumUrl).toString(Charsets.UTF_8)
        val expected = apkChecksum(checksums, release.assetName)
        verifyApkChecksum(apk, expected)
        // A verified digest survives the external permission screen and process recreation.
        preferences.edit().putString("verified_checksum", expected).commit()
    }

    private suspend fun beginInstall(apk: File) {
        preferences.edit().putString("pending_apk", apk.name).putString("phase", "install").apply()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
        ) {
            preferences.edit().putString("phase", "permission").apply()
            _state.value =
                _state.value.copy(
                    stage = UpdateStage.WAITING_PERMISSION,
                    reason = context.getString(R.string.update_install_permission),
                )
            openInstallPermission()
            return
        }
        _state.value =
            _state.value.copy(
                stage = UpdateStage.INSTALLING,
                error = null,
                reason = context.getString(R.string.update_install_request_sent),
            )
        install(apk)
    }

    private fun openInstallPermission() {
        context.startActivity(
            Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun resumePendingInstall(openSettings: Boolean = false) {
        if (_state.value.stage != UpdateStage.WAITING_PERMISSION) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
        ) {
            if (openSettings) openInstallPermission()
            return
        }
        _state.value = _state.value.copy(stage = UpdateStage.VERIFYING)
        scope.launch {
            try {
                val release = _state.value.release ?: error("Missing update metadata")
                val apk = File(File(context.cacheDir, "updates"), release.assetName)
                val expected =
                    preferences.getString("verified_checksum", null)
                        ?: error("Missing verified APK checksum; retry the download")
                withContextIo { verifyApkChecksum(apk, expected) }
                beginInstall(apk)
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = UpdateStage.ERROR, error = errorMessage(e))
            }
        }
    }

    private suspend fun install(apk: File) = withContextIo {
        val packageInstaller = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(apk.length())
            }
        val sessionId = packageInstaller.createSession(params)
        preferences.edit().putInt("session_id", sessionId).putString("phase", "install").commit()
        val resultIntent =
            Intent(context, UpdateInstallReceiver::class.java).apply {
                action = INSTALL_RESULT_ACTION
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            }
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
                else 0
        val resultPendingIntent =
            PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                pendingIntentFlags,
            )
        val session = packageInstaller.openSession(sessionId)
        var committed = false
        try {
            FileInputStream(apk).use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(resultPendingIntent.intentSender)
            committed = true
        } finally {
            session.close()
            if (!committed) packageInstaller.abandonSession(sessionId)
        }
    }

    private fun loadChannel(): UpdateChannel =
        when (preferences.getString(CHANNEL_KEY, null)) {
            UpdateChannel.BETA.value -> UpdateChannel.BETA
            UpdateChannel.MAIN.value -> UpdateChannel.MAIN
            else -> UpdateChannel.STABLE
        }

    private fun androidAssetName(): String? =
        when (Build.SUPPORTED_ABIS.firstOrNull()) {
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
            ?: throw IllegalStateException(
                "The proxy is not running. Start the proxy and try again."
            )

    private fun proxyDownload(url: String, destination: String) {
        val binder =
            proxyBinder
                ?: throw IllegalStateException(
                    "The proxy is not running. Start the proxy and try again."
                )
        binder.proxyDownload(url, destination)
    }

    private fun cleanupOldDownloads() {
        val directory = File(context.cacheDir, "updates")
        val cutoff = System.currentTimeMillis() - DOWNLOAD_RETENTION_MS
        directory.listFiles()?.forEach { file ->
            if (
                (file.name.endsWith(".apk") || file.name.endsWith(".part")) &&
                    file.lastModified() < cutoff
            ) {
                file.delete()
            }
        }
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

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
        private const val RELEASES_URL =
            "https://api.github.com/repos/yuhaiin/yuhaiin-android/releases"
        private const val RELEASE_PAGE_LIMIT = 10
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val INSTALL_RESULT_ACTION = "io.github.asutorufa.yuhaiin.UPDATE_INSTALL_RESULT"
        private const val DOWNLOAD_RETENTION_MS = 24 * 60 * 60 * 1000L
    }

    private fun persistRelease(release: AndroidUpdateRelease) {
        preferences
            .edit()
            .putString("release", json.encodeToString(release))
            .putString("phase", "download")
            .remove("verified_checksum")
            .apply()
    }

    private fun restoreState(): AndroidUpdateState {
        val release =
            preferences.getString("release", null)?.let {
                runCatching { json.decodeFromString<AndroidUpdateRelease>(it) }.getOrNull()
            }
        val session = preferences.getInt("session_id", -1)
        val installing =
            session >= 0 && context.packageManager.packageInstaller.getSessionInfo(session) != null
        val phase = preferences.getString("phase", null)
        val stage =
            when {
                installing -> UpdateStage.INSTALLING
                phase == "permission" -> UpdateStage.WAITING_PERMISSION
                phase != null -> UpdateStage.ERROR
                else -> UpdateStage.IDLE
            }
        return AndroidUpdateState(
            channel = loadChannel(),
            release = release,
            stage = stage,
            reason =
                when (stage) {
                    UpdateStage.WAITING_PERMISSION ->
                        context.getString(R.string.update_install_permission)
                    UpdateStage.INSTALLING ->
                        context.getString(R.string.update_install_confirmation)
                    UpdateStage.ERROR -> context.getString(R.string.update_download_interrupted)
                    else -> null
                },
        )
    }
}
