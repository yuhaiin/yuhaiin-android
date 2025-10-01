// IYuhaiinVpnBinder.aidl
package io.github.asutorufa.yuhaiin;

// Declare any non-default types here with import statements

import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback;

interface IYuhaiinVpnBinder {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
//    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
//            double aDouble, String aString);

            void stop();
            boolean isRunning();
            void registerCallback(IYuhaiinVpnCallback cb);
            void unregisterCallback(IYuhaiinVpnCallback cb);
}