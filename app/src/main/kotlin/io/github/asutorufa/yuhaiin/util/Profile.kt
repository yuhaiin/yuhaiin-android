package io.github.asutorufa.yuhaiin.util


import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_APP_BYPASS
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_APP_LIST
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_AUTO_CONNECT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_DNS_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_FAKE_DNS_CIDR
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_PER_APP
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_ROUTE
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ALLOW_LAN
import io.github.asutorufa.yuhaiin.util.Constants.PREF_AUTH_PASSWORD
import io.github.asutorufa.yuhaiin.util.Constants.PREF_AUTH_USERNAME
import io.github.asutorufa.yuhaiin.util.Constants.PREF_AUTH_USERPW
import io.github.asutorufa.yuhaiin.util.Constants.PREF_HTTP_SERVER_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_IPV6_PROXY
import io.github.asutorufa.yuhaiin.util.Constants.PREF_SAVE_LOGCAT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_SOCKS5_SERVER_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_YUHAIIN_PORT


class Profile internal constructor(private val mPref: SharedPreferences, val name: String) {
    private val mPrefix: String = prefPrefix(name)

    var httpServerPort: Int
        get() = mPref.getInt(key(PREF_HTTP_SERVER_PORT), 8188)
        set(server) {
            mPref.edit().putInt(key(PREF_HTTP_SERVER_PORT), server).apply()
        }
    var socks5ServerPort: Int
        get() = mPref.getInt(key(PREF_SOCKS5_SERVER_PORT), 1080)
        set(port) {
            mPref.edit().putInt(key(PREF_SOCKS5_SERVER_PORT), port).apply()
        }

    var isUserPw: Boolean
        get() = mPref.getBoolean(key(PREF_AUTH_USERPW), false)
        set(open) {
            mPref.edit().putBoolean(key(PREF_AUTH_USERPW), open).apply()
        }

    var username: String
        get() = mPref.getString(key(PREF_AUTH_USERNAME), "") ?: ""
        set(username) {
            mPref.edit().putString(key(PREF_AUTH_USERNAME), username).apply()
        }
    var password: String
        get() = mPref.getString(key(PREF_AUTH_PASSWORD), "") ?: ""
        set(password) {
            mPref.edit().putString(key(PREF_AUTH_PASSWORD), password).apply()
        }
    var route: String
        get() = mPref.getString(key(PREF_ADV_ROUTE), Constants.ROUTE_ALL) ?: Constants.ROUTE_ALL
        set(route) {
            mPref.edit().putString(key(PREF_ADV_ROUTE), route).apply()
        }
    var fakeDnsCidr: String
        get() = mPref.getString(key(PREF_ADV_FAKE_DNS_CIDR), "10.0.2.1/24") ?: "10.0.2.1/24"
        set(dns) {
            mPref.edit().putString(key(PREF_ADV_FAKE_DNS_CIDR), dns).apply()
        }
    var dnsPort: Int
        get() = mPref.getInt(key(PREF_ADV_DNS_PORT), 35353)
        set(port) {
            mPref.edit().putInt(key(PREF_ADV_DNS_PORT), port).apply()
        }
    var isPerApp: Boolean
        get() = mPref.getBoolean(key(PREF_ADV_PER_APP), false)
        set(bool) {
            mPref.edit().putBoolean(key(PREF_ADV_PER_APP), bool).apply()
        }
    var isBypassApp: Boolean
        get() = mPref.getBoolean(key(PREF_ADV_APP_BYPASS), false)
        set(bool) {
            mPref.edit().putBoolean(key(PREF_ADV_APP_BYPASS), bool).apply()
        }

    var appList: Set<String>
        get() = mPref.getStringSet(key(PREF_ADV_APP_LIST), emptySet()) ?: emptySet()
        set(list) {
            mPref.edit {
                putStringSet(key(PREF_ADV_APP_LIST), list)
            }
        }

    var hasIPv6: Boolean
        get() = mPref.getBoolean(key(PREF_IPV6_PROXY), false)
        set(has) {
            mPref.edit().putBoolean(key(PREF_IPV6_PROXY), has).apply()
        }

    var autoConnect: Boolean
        get() = mPref.getBoolean(key(PREF_ADV_AUTO_CONNECT), false)
        set(auto) {
            mPref.edit().putBoolean(key(PREF_ADV_AUTO_CONNECT), auto).apply()
        }

    var yuhaiinPort: Int
        get() = mPref.getInt(key(PREF_YUHAIIN_PORT), 50051)
        set(host) {
            mPref.edit().putInt(key(PREF_YUHAIIN_PORT), host).apply()
        }

    var saveLogcat: Boolean
        get() = mPref.getBoolean(key(PREF_SAVE_LOGCAT), false)
        set(isSave) {
            mPref.edit().putBoolean(key(PREF_SAVE_LOGCAT), isSave).apply()
        }


    var allowLan: Boolean
        get() = mPref.getBoolean(key(PREF_ALLOW_LAN), false)
        set(allow) {
            mPref.edit().putBoolean(key(PREF_ALLOW_LAN), allow).apply()
        }

    fun delete() {
        mPref.edit()
            .remove(key(PREF_AUTH_USERPW))
            .remove(key(PREF_AUTH_USERNAME))
            .remove(key(PREF_AUTH_PASSWORD))
            .remove(key(PREF_ADV_ROUTE))
            .remove(key(PREF_ADV_DNS_PORT))
            .remove(key(PREF_ADV_PER_APP))
            .remove(key(PREF_ADV_APP_BYPASS))
            .remove(key(PREF_ADV_APP_LIST))
            .remove(key(PREF_IPV6_PROXY))
            .remove(key(PREF_ADV_FAKE_DNS_CIDR))
            .remove(key(PREF_ADV_AUTO_CONNECT))
            .remove(key(PREF_HTTP_SERVER_PORT))
            .remove(key(PREF_SOCKS5_SERVER_PORT))
            .remove(key(PREF_YUHAIIN_PORT))
            .remove(key(PREF_SAVE_LOGCAT))
            .remove(key(PREF_ALLOW_LAN))
            .apply()
    }

    private fun key(k: String): String {
        return mPrefix + k
    }

    companion object {
        private fun prefPrefix(name: String): String {
            return name.replace("_", "__").replace(" ", "_")
        }
    }
}
