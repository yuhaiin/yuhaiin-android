// IYuhaiinVpnCallback.aidl
package io.github.asutorufa.yuhaiin;

// Declare any non-default types here with import statements

interface IYuhaiinVpnCallback {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void onStateChanged(int state);
    void onMsg(String msg);
}
