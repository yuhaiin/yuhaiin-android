package net.typeblog.socks.util;

public class Constants
{
    public static final String ROUTE_ALL = "all",
    ROUTE_NO_LOCAL = "no_local",
            ROUTE_CHN = "chn";

    private static final String INTENT_PREFIX = "SOCKS";


    public static final String INTENT_DISCONNECT = INTENT_PREFIX + "DISCONNECT";
    public static final String INTENT_DISCONNECTED = INTENT_PREFIX + "DISCONNECTED";
    public static final String INTENT_CONNECTED = INTENT_PREFIX + "CONNECTED";
    public static final String INTENT_RECONNECT = INTENT_PREFIX + "RECONNECT";

    public static final String INTENT_NAME = INTENT_PREFIX + "NAME",
            INTENT_HTTP_SERVER_PORT = INTENT_PREFIX + "HTTPSERVPORT",
            INTENT_SOCKS5_SERVER_PORT = INTENT_PREFIX + "SOCKS5SERVERPORT",
            INTENT_USERNAME = INTENT_PREFIX + "UNAME",
            INTENT_PASSWORD = INTENT_PREFIX + "PASSWD",
            INTENT_ROUTE = INTENT_PREFIX + "ROUTE",
            INTENT_FAKE_DNS_CIDR = INTENT_PREFIX + "FAKEDNSCIDR",
            INTENT_DNS_PORT = INTENT_PREFIX + "DNSPORT",
            INTENT_PER_APP = INTENT_PREFIX + "PERAPP",
            INTENT_APP_BYPASS = INTENT_PREFIX + "APPBYPASS",
            INTENT_APP_LIST = INTENT_PREFIX + "APPLIST",
            INTENT_IPV6_PROXY = INTENT_PREFIX + "IPV6",
            INTENT_UDP_GW = INTENT_PREFIX + "UDPGW";

    public static final String PREF = "profile",
            PREF_PROFILE = "profile",
            PREF_LAST_PROFILE = "last_profile",
            PREF_HTTP_SERVER_PORT = "http_server_port",
            PREF_SOCKS5_SERVER_PORT = "socks5_server_port",
            PREF_IPV6_PROXY = "ipv6_proxy",
            PREF_UDP_PROXY = "udp_proxy",
            PREF_UDP_GW = "udp_gw",
            PREF_AUTH_USERPW = "auth_userpw",
            PREF_AUTH_USERNAME = "auth_username",
            PREF_AUTH_PASSWORD = "auth_password",
            PREF_ADV_ROUTE = "adv_route",

            PREF_ADV_FAKE_DNS_CIDR = "adv_fake_dns_cidr",
            PREF_ADV_DNS_PORT = "adv_dns_port",
            PREF_ADV_PER_APP = "adv_per_app",
            PREF_ADV_APP_BYPASS = "adv_app_bypass",
            PREF_ADV_APP_LIST = "adv_app_list_msel",
            PREF_ADV_AUTO_CONNECT = "adv_auto_connect",

            PREF_YUHAIIN_HOST="yuhaiin_host";
}
