package io.github.asutorufa.yuhaiin.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.YuhaiinVpnService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


object Utility {
    private val TAG = Utility::class.java.simpleName

    fun exec(cmd: String, callback: () -> Unit): Process {
        val p = Runtime.getRuntime().exec(cmd)

        if (DEBUG) {
            val th = Thread {
                val input = BufferedReader(InputStreamReader(p.inputStream))
                try {
                    var line: String?
                    while (input.readLine().also { line = it } != null) {
                        println(line)
                    }
                    input.close()
                } catch (e: Exception) {
                    Log.d(TAG, "exec: $e")
                }
            }

            th.start()
        }

        Thread { p.waitFor(); callback();Log.d(TAG, "exec callback") }.start()

        return p
    }

    fun join(list: List<String?>, separator: String): String {
        if (list.isEmpty()) return ""
        val ret = StringBuilder()
        for (s in list) {
            ret.append(s).append(separator)
        }
        return ret.substring(0, ret.length - separator.length)
    }

    @Throws(IOException::class, InterruptedException::class)
    fun startYuhaiin(context: Context, host: String): Process {
        val cmd = context.applicationInfo.nativeLibraryDir + "/libyuhaiin.so" +
                " -path " + context.getExternalFilesDir("yuhaiin") +
                " -host " + host
        Log.d(TAG, "startYuhaiin: $cmd")
        return exec(cmd) {}
    }

    private fun saveToIntent(mPref: Intent, profile: Profile) {
        mPref.apply {
            putExtra(Constants.PREF_HTTP_SERVER_PORT, profile.httpServerPort)
            putExtra(Constants.PREF_SOCKS5_SERVER_PORT, profile.socks5ServerPort)
            putExtra(Constants.PREF_AUTH_USERPW, profile.isUserPw)
            putExtra(Constants.PREF_AUTH_USERNAME, profile.username)
            putExtra(Constants.PREF_AUTH_PASSWORD, profile.password)
            putExtra(Constants.PREF_ADV_ROUTE, profile.route)
            putExtra(Constants.PREF_ADV_FAKE_DNS_CIDR, profile.fakeDnsCidr)
            putExtra(Constants.PREF_ADV_DNS_PORT, profile.dnsPort)
            putExtra(Constants.PREF_ADV_PER_APP, profile.isPerApp)
            putExtra(Constants.PREF_ADV_APP_BYPASS, profile.isBypassApp)
            putExtra(Constants.PREF_ADV_APP_LIST, profile.appList.toTypedArray())
            putExtra(Constants.PREF_IPV6_PROXY, profile.hasIPv6)
            putExtra(Constants.PREF_ADV_AUTO_CONNECT, profile.autoConnect)
            putExtra(Constants.PREF_YUHAIIN_PORT, profile.yuhaiinPort)
            putExtra(Constants.PREF_SAVE_LOGCAT, profile.saveLogcat)
            putExtra(Constants.PREF_PROFILE, profile.name)
            putExtra(Constants.PREF_ALLOW_LAN, profile.allowLan)
        }
    }

    fun startVpn(context: Context, profile: Profile) {
        val intent = Intent(context, YuhaiinVpnService::class.java).also { saveToIntent(it, profile) }
        ContextCompat.startForegroundService(context, intent)
    }

}