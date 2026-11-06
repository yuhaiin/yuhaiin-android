package io.github.asutorufa.yuhaiin.update

import org.junit.Assert.*
import org.junit.Test

class ApkIdentityTest {
    private val installed = ApkIdentity("io.github.asutorufa.yuhaiin", 184, setOf("release-key"))

    @Test
    fun sameVersionRollingBuildAndNewerBuildAreAllowed() {
        assertNull(updateIdentityProblem(installed, installed))
        assertNull(updateIdentityProblem(installed, installed.copy(versionCode = 185)))
    }

    @Test
    fun unrelatedPackageDowngradeAndMissingOrUntrustedSignersAreRejected() {
        assertEquals(
            ApkIdentityProblem.PACKAGE,
            updateIdentityProblem(installed, installed.copy(packageName = "another.app")),
        )
        assertEquals(
            ApkIdentityProblem.VERSION,
            updateIdentityProblem(installed, installed.copy(versionCode = 183)),
        )
        for (signers in listOf(emptySet(), setOf("unrelated-key"))) {
            assertEquals(
                ApkIdentityProblem.SIGNATURE,
                updateIdentityProblem(
                    installed,
                    installed.copy(signers = signers, signingHistory = signers),
                ),
            )
        }
    }

    @Test
    fun rotationIsForwardOnlyAndMultiSignerPackagesRequireExactSets() {
        val rotated =
            installed.copy(
                signers = setOf("new-key"),
                signingHistory = setOf("release-key", "new-key"),
            )
        assertNull(updateIdentityProblem(installed, rotated))
        assertEquals(ApkIdentityProblem.SIGNATURE, updateIdentityProblem(rotated, installed))
        val multiple = installed.copy(signers = setOf("release-key", "second-key"))
        assertNull(updateIdentityProblem(multiple, multiple))
        assertEquals(ApkIdentityProblem.SIGNATURE, updateIdentityProblem(multiple, installed))
        assertEquals(ApkIdentityProblem.SIGNATURE, updateIdentityProblem(installed, multiple))
    }
}
