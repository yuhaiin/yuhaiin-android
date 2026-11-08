package io.github.asutorufa.yuhaiin

import io.github.asutorufa.yuhaiin.service.VpnActions

/** Opens the native Android settings screen that v2 keeps behind the Web UI. */
fun openNativeSettings(activity: MainActivity) {
    activity.runOnUiThread { activity.navigationAction.value = VpnActions.SETTINGS }
}
