package io.github.asutorufa.yuhaiin.update

/** Certificate identities and rotation history obtained from Android's package parser. */
internal data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>,
    val signingHistory: Set<String> = signers,
)

internal enum class ApkIdentityProblem {
    PACKAGE,
    VERSION,
    SIGNATURE,
}

internal fun updateIdentityProblem(
    installed: ApkIdentity,
    candidate: ApkIdentity,
): ApkIdentityProblem? {
    if (candidate.packageName != installed.packageName) return ApkIdentityProblem.PACKAGE
    // Rolling GitHub builds currently share a version code. Equal codes remain installable.
    if (candidate.versionCode < installed.versionCode) return ApkIdentityProblem.VERSION
    if (installed.signers.isEmpty() || candidate.signers.isEmpty())
        return ApkIdentityProblem.SIGNATURE
    if (installed.signers == candidate.signers) return null
    // Multi-signer packages must match the complete signer set. Single signers can rotate
    // forward only through a proof-of-rotation history verified by the platform.
    if (
        installed.signers.size == 1 &&
            candidate.signers.size == 1 &&
            installed.signers.single() in candidate.signingHistory
    )
        return null
    return ApkIdentityProblem.SIGNATURE
}
