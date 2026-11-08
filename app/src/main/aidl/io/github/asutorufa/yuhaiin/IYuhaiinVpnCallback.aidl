// IYuhaiinVpnCallback.aidl
package io.github.asutorufa.yuhaiin;

interface IYuhaiinVpnCallback {
    void onStateChanged(int state);
    void onMsg(String msg);
    // v2: the core started, stopped or moved to another management port.
    void onCoreChanged(int coreState, int port);
}
