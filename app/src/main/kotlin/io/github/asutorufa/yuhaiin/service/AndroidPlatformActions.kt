package io.github.asutorufa.yuhaiin.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import io.github.asutorufa.yuhaiin.update.AndroidUpdateState
import io.github.asutorufa.yuhaiin.update.UpdateChannel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yuhaiin.PlatformActions

@Serializable data class PolicySection(val title: String, val body: String)

@Serializable
data class PrivacyState(
    val title: String,
    val effectiveDate: String,
    val sections: List<PolicySection>,
    val contactUrl: String,
    val withdrawBody: String,
    val consents: Map<String, Boolean>,
)

@Serializable
data class UpdateRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val publishedAt: String,
    val assetSize: Long,
)

@Serializable
data class UpdateStateJson(
    val channel: String,
    val channels: List<String>,
    val checking: Boolean,
    val stage: String,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val error: String? = null,
    val reason: String? = null,
    val release: UpdateRelease? = null,
    val currentVersion: String,
)

@Serializable
data class AppInfoJson(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val commit: String,
    val updateTrack: String,
)

/** Pure mapping, unit tested. */
internal fun AndroidUpdateState.toJson(currentVersion: String) =
    UpdateStateJson(
        channel = channel.value,
        channels = UpdateChannel.entries.map { it.value },
        checking = checking,
        stage = stage.name,
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        error = error,
        reason = reason,
        release =
            release?.let { UpdateRelease(it.version, it.tag, it.notes, it.publishedAt, it.assetSize) },
        currentVersion = currentVersion,
    )

/**
 * Android side of the core's platform API (/android/v1/), running in ":bg". Calls arrive on Go
 * threads; UI-thread work is posted to the main looper.
 */
class AndroidPlatformActions(
    private val context: Context,
    private val core: CoreControl,
) : PlatformActions {
    interface CoreControl {
        /** Stops the core, runs [between] and starts it again, asynchronously. */
        fun restart(between: (suspend () -> Unit)?)

        fun disableVpn()
    }

    private val json = Json { encodeDefaults = true }
    private val main = Handler(Looper.getMainLooper())

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val done = CountDownLatch(1)
        main.post {
            result = runCatching(block)
            done.countDown()
        }
        check(done.await(10, TimeUnit.SECONDS)) { "main thread is busy" }
        return result!!.getOrThrow()
    }

    override fun restoreConfig(path: String) {
        val file = File(path)
        core.restart {
            try {
                yuhaiin.Yuhaiin.importConfig(file.readBytes())
            } finally {
                file.delete()
            }
        }
    }

    override fun restartCore() = core.restart(null)

    override fun privacyState(): String {
        val consent = PrivacyConsent.forContext(context)
        val sections =
            listOf(
                    R.string.privacy_developer_title to R.string.privacy_developer_body,
                    R.string.vpn_disclosure_title to R.string.vpn_disclosure_body,
                    R.string.apps_disclosure_title to R.string.apps_disclosure_body,
                    R.string.privacy_storage_title to R.string.privacy_storage_body,
                    R.string.privacy_network_title to R.string.privacy_network_body,
                    R.string.privacy_retention_title to R.string.privacy_retention_body,
                )
                .map { (title, body) -> PolicySection(context.getString(title), context.getString(body)) }
        return json.encodeToString(
            PrivacyState.serializer(),
            PrivacyState(
                title = context.getString(R.string.privacy_policy_title),
                effectiveDate = context.getString(R.string.privacy_effective_date),
                sections = sections,
                contactUrl = "https://github.com/yuhaiin/yuhaiin-android/issues",
                withdrawBody = context.getString(R.string.privacy_withdraw_body),
                consents =
                    mapOf(
                        "vpn" to consent.has(Purpose.VPN),
                        "installedApps" to consent.has(Purpose.INSTALLED_APPS),
                    ),
            ),
        )
    }

    override fun withdrawConsent() {
        PrivacyConsent.forContext(context).revoke()
        kotlinx.coroutines.runBlocking { MainApplication.installedApps.clear() }
        core.disableVpn()
    }

    override fun updateState(): String =
        json.encodeToString(
            UpdateStateJson.serializer(),
            MainApplication.updateManager.state.value.toJson(BuildConfig.VERSION_NAME),
        )

    override fun updateAction(action: String, arg: String) {
        val manager = MainApplication.updateManager
        onMain {
            when (action) {
                "check" -> manager.check()
                "apply" -> manager.apply()
                "permission" -> manager.resumePendingInstall(openSettings = true)
                "channel" ->
                    manager.setChannel(
                        UpdateChannel.entries.firstOrNull { it.value == arg }
                            ?: throw IllegalArgumentException("unknown channel $arg")
                    )
                else -> throw IllegalArgumentException("unknown action $action")
            }
        }
    }

    override fun appInfo(): String =
        json.encodeToString(
            AppInfoJson.serializer(),
            AppInfoJson(
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.GIT_COMMIT,
                BuildConfig.UPDATE_TRACK,
            ),
        )
}
