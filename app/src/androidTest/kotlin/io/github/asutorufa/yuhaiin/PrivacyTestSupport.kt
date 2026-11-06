package io.github.asutorufa.yuhaiin

import android.content.Context
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose

/** Lifecycle tests start after disclosure; PrivacyFlowsTest exercises the actual consent UI. */
internal fun grantVpnConsentForLifecycleTest(context: Context) {
    PrivacyConsent.forContext(context).grant(Purpose.VPN)
}
