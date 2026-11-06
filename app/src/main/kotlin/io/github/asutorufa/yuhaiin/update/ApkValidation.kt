package io.github.asutorufa.yuhaiin.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.asutorufa.yuhaiin.R
import java.io.File

/** Preflight before asking for installer permission; Android still validates the final install. */
@Suppress("DEPRECATION")
internal fun validateUpdateApk(context: Context, apk: File) {
    val packages = context.packageManager
    val flags =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
    val candidate =
        packages.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error(context.getString(R.string.update_apk_invalid))
    val installed = packages.getPackageInfo(context.packageName, flags)
    val problem = updateIdentityProblem(installed.updateIdentity(), candidate.updateIdentity())
    val message =
        when (problem) {
            null -> return
            ApkIdentityProblem.PACKAGE -> R.string.update_apk_wrong_package
            ApkIdentityProblem.VERSION -> R.string.update_apk_older_version
            ApkIdentityProblem.SIGNATURE -> R.string.update_apk_wrong_signature
        }
    error(context.getString(message))
}

@Suppress("DEPRECATION")
private fun PackageInfo.updateIdentity(): ApkIdentity {
    if (Build.VERSION.SDK_INT >= 28) {
        val signing = signingInfo
        val history = signing?.signingCertificateHistory.orEmpty()
        val current =
            if (signing?.hasMultipleSigners() == true) signing.apkContentsSigners.orEmpty().toList()
            else history.takeLast(1)
        return ApkIdentity(
            packageName,
            longVersionCode,
            current.map { it.toCharsString() }.toSet(),
            history.map { it.toCharsString() }.toSet(),
        )
    }
    return ApkIdentity(
        packageName,
        versionCode.toLong(),
        signatures.orEmpty().map { it.toCharsString() }.toSet(),
    )
}
