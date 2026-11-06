package io.github.asutorufa.yuhaiin.privacy

import android.content.Context
import java.io.File

/** Device-local receipts, shared by the UI and VPN processes without preference caches. */
class PrivacyConsent(private val directory: File) {
    enum class Purpose(val receipt: String) {
        VPN("vpn-v1"),
        INSTALLED_APPS("installed-apps-v1"),
    }

    fun has(purpose: Purpose): Boolean = File(directory, purpose.receipt).isFile

    fun grant(purpose: Purpose) {
        check(directory.mkdirs() || directory.isDirectory) { "Cannot save privacy choice" }
        val receipt = File(directory, purpose.receipt)
        // Creating a receipt is atomic. Other processes never observe a partially written value.
        check(receipt.createNewFile() || receipt.isFile) { "Cannot save privacy choice" }
    }

    fun revoke() {
        for (purpose in Purpose.entries) {
            val receipt = File(directory, purpose.receipt)
            check(!receipt.exists() || receipt.delete()) { "Cannot withdraw privacy choice" }
        }
    }

    companion object {
        // Consent must not travel in cloud backups, device transfer or exported configuration.
        fun forContext(context: Context) =
            PrivacyConsent(File(context.noBackupFilesDir, "privacy-consent"))
    }
}
