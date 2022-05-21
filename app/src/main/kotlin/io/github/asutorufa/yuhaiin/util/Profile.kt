package io.github.asutorufa.yuhaiin.util


import android.content.SharedPreferences
import androidx.core.content.edit


class Profile internal constructor(private val mPref: SharedPreferences, val name: String) {
    private val mPrefix: String = prefPrefix(name)

    var httpServerPort: Int
        get() = mPref.getInt(key("http_server_port"), 8188)
        set(server) {
            mPref.edit().putInt(key("http_server_port"), server).apply()
        }
    var socks5ServerPort: Int
        get() = mPref.getInt(key("socks5_server_port"), 1080)
        set(port) {
            mPref.edit().putInt(key("socks5_server_port"), port).apply()
        }
    val isUserPw: Boolean
        get() = mPref.getBoolean(key("userpw"), false)

    fun setIsUserpw(`is`: Boolean) {
        mPref.edit().putBoolean(key("userpw"), `is`).apply()
    }

    var username: String
        get() = mPref.getString(key("username"), "") ?: ""
        set(username) {
            mPref.edit().putString(key("username"), username).apply()
        }
    var password: String
        get() = mPref.getString(key("password"), "") ?: ""
        set(password) {
            mPref.edit().putString(key("password"), password).apply()
        }
    var route: String
        get() = mPref.getString(key("route"), Constants.ROUTE_ALL) ?: Constants.ROUTE_ALL
        set(route) {
            mPref.edit().putString(key("route"), route).apply()
        }
    var fakeDnsCidr: String
        get() = mPref.getString(key("fake_dns_cidr"), "10.0.2.1/24") ?: "10.0.2.1/24"
        set(dns) {
            mPref.edit().putString(key("fake_dns_cidr"), dns).apply()
        }
    var dnsPort: Int
        get() = mPref.getInt(key("dns_port"), 35353)
        set(port) {
            mPref.edit().putInt(key("dns_port"), port).apply()
        }
    var isPerApp: Boolean
        get() = mPref.getBoolean(key("perapp"), false)
        set(bool) {
            mPref.edit().putBoolean(key("perapp"), bool).apply()
        }
    var isBypassApp: Boolean
        get() = mPref.getBoolean(key("appbypass"), false)
        set(bool) {
            mPref.edit().putBoolean(key("appbypass"), bool).apply()
        }

    var appList: Set<String>
        get() = mPref.getStringSet(key("applist"), emptySet()) ?: emptySet()
        set(list) {
            mPref.edit {
                putStringSet(key("applist"), list)
            }
        }

    fun hasIPv6(): Boolean {
        return mPref.getBoolean(key("ipv6"), false)
    }

    fun setHasIPv6(has: Boolean) {
        mPref.edit().putBoolean(key("ipv6"), has).apply()
    }

    fun autoConnect(): Boolean {
        return mPref.getBoolean(key("auto"), false)
    }

    fun setAutoConnect(auto: Boolean) {
        mPref.edit().putBoolean(key("auto"), auto).apply()
    }

    var yuhaiinHost: String
        get() = mPref.getString(key("yuhaiin_host"), "127.0.0.1:50051") ?: "127.0.0.1:50051"
        set(host) {
            mPref.edit().putString(key("yuhaiin_host"), host).apply()
        }

    fun delete() {
        mPref.edit()
            .remove(key("userpw"))
            .remove(key("username"))
            .remove(key("password"))
            .remove(key("route"))
            .remove(key("dns_port"))
            .remove(key("perapp"))
            .remove(key("appbypass"))
            .remove(key("applist"))
            .remove(key("ipv6"))
            .remove(key("fake_dns_cidr"))
            .remove(key("auto"))
            .remove(key("http_server_port"))
            .remove(key("socks5_server_port"))
            .remove(key("yuhaiin_host"))
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