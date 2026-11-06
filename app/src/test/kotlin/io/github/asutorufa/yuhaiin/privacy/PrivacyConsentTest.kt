package io.github.asutorufa.yuhaiin.privacy

import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivacyConsentTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun choicesAreIndependentAndVisibleToOtherProcessesWithoutReloading() {
        val directory = File(temporary.root, "privacy-consent")
        val ui = PrivacyConsent(directory)
        val service = PrivacyConsent(directory)
        assertFalse(service.has(Purpose.VPN))
        assertFalse(service.has(Purpose.INSTALLED_APPS))
        ui.grant(Purpose.VPN)
        assertTrue(service.has(Purpose.VPN))
        assertFalse(service.has(Purpose.INSTALLED_APPS))
        ui.grant(Purpose.INSTALLED_APPS)
        ui.grant(Purpose.INSTALLED_APPS) // A repeated affirmative choice is harmless.
        assertTrue(service.has(Purpose.INSTALLED_APPS))
        ui.revoke()
        assertFalse(service.has(Purpose.VPN))
        assertFalse(service.has(Purpose.INSTALLED_APPS))
    }

    @Test
    fun oldReceiptOrDirectoryDoesNotGrantCurrentConsent() {
        val directory = temporary.newFolder()
        File(directory, "vpn-v0").createNewFile()
        File(directory, Purpose.VPN.receipt).mkdir()
        assertFalse(PrivacyConsent(directory).has(Purpose.VPN))
    }

    @Test
    fun failureToPersistNeverGrantsConsent() {
        val invalidDirectory = temporary.newFile()
        val consent = PrivacyConsent(invalidDirectory)
        assertThrows(IllegalStateException::class.java) { consent.grant(Purpose.VPN) }
        assertFalse(consent.has(Purpose.VPN))
    }
}
