package io.github.asutorufa.yuhaiin.util

object Constants {
    const val ROUTE_ALL = "ALL"
    const val ROUTE_NO_LOCAL = "Non-Local IPs"
    const val ROUTE_CHN = "Non-Chinese IPs"

    const val PREF = "profile"
    const val PREF_PROFILE = "profile"
    const val PREF_LAST_PROFILE = "last_profile"
    const val PREF_HTTP_SERVER_PORT = "http_server_port"
    const val PREF_SOCKS5_SERVER_PORT = "socks5_server_port"
    const val PREF_IPV6_PROXY = "ipv6_proxy"
    const val PREF_AUTH_USERPW = "auth_userpw"
    const val PREF_AUTH_USERNAME = "auth_username"
    const val PREF_AUTH_PASSWORD = "auth_password"
    const val PREF_ADV_ROUTE = "adv_route"
    const val PREF_ADV_FAKE_DNS_CIDR = "adv_fake_dns_cidr"
    const val PREF_ADV_DNS_PORT = "adv_dns_port"
    const val PREF_ADV_PER_APP = "adv_per_app"
    const val PREF_ADV_APP_BYPASS = "adv_app_bypass"
    const val PREF_ADV_APP_LIST = "adv_app_list"
    const val PREF_ADV_AUTO_CONNECT = "adv_auto_connect"
    const val PREF_YUHAIIN_PORT = "yuhaiin_port"
    const val PREF_ALLOW_LAN = "allow_lan"
    const val PREF_SAVE_LOGCAT = "save_logcat"

    private const val INTENT_PREFIX = "SOCKS"
    const val INTENT_DISCONNECTED = INTENT_PREFIX + "DISCONNECTED"
    const val INTENT_CONNECTED = INTENT_PREFIX + "CONNECTED"
    const val INTENT_CONNECTING = INTENT_PREFIX + "CONNECTING"
    const val INTENT_DISCONNECTING = INTENT_PREFIX + "DISCONNECTING"
}