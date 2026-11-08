// IYuhaiinVpnBinder.aidl
package io.github.asutorufa.yuhaiin;

import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback;
import android.os.Bundle;

// Served by YuhaiinCoreService in ":bg". The service is not exported, so only
// this app can bind it.
interface IYuhaiinVpnBinder {
            // Turns the Android VPN (the core's TUN inbound) off. The core keeps running.
            void stop();
            int state();
            Bundle snapshot();
            void snooze(int minutes);
            void checkHealth();
            void registerCallback(IYuhaiinVpnCallback cb);
            void unregisterCallback(IYuhaiinVpnCallback cb);
            byte[] proxyGet(String url);
            void proxyDownload(String url, String destination);
            void refreshMonitoring();

            // v2: core lifecycle and the WebView management session.
            // Keys: coreState (int), coreError (String), port (int), token (String), https (boolean).
            Bundle session();
            void startCore();
            void stopCore();
            // Enables the VPN. Callers must have completed consent and VpnService.prepare().
            void setVpnEnabled(boolean enabled);
            // Platform VPN runtime status JSON reported by the core.
            String vpnStatus();
}
