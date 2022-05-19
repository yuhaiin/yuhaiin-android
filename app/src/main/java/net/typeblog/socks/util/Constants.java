package net.typeblog.socks.util;

public class Constants {
    public static final String ROUTE_ALL = "ALL",
            ROUTE_NO_LOCAL = "Non-Local IPs",
            ROUTE_CHN = "Non-Chinese IPs";
    public static final String PREF = "profile";
    public static final String PREF_PROFILE = "profile";
    public static final String PREF_LAST_PROFILE = "last_profile";
    public static final String PREF_HTTP_SERVER_PORT = "http_server_port";
    public static final String PREF_SOCKS5_SERVER_PORT = "socks5_server_port";
    public static final String PREF_IPV6_PROXY = "ipv6_proxy";
    public static final String PREF_AUTH_USERPW = "auth_userpw";
    public static final String PREF_AUTH_USERNAME = "auth_username";
    public static final String PREF_AUTH_PASSWORD = "auth_password";
    public static final String PREF_ADV_ROUTE = "adv_route";

    public static final String PREF_ADV_FAKE_DNS_CIDR = "adv_fake_dns_cidr";
    public static final String PREF_ADV_DNS_PORT = "adv_dns_port";
    public static final String PREF_ADV_PER_APP = "adv_per_app";
    public static final String PREF_ADV_APP_BYPASS = "adv_app_bypass";
    public static final String PREF_ADV_APP_LIST = "adv_app_list_msel";
    public static final String PREF_ADV_AUTO_CONNECT = "adv_auto_connect";

    public static final String PREF_YUHAIIN_HOST = "yuhaiin_host";
    private static final String INTENT_PREFIX = "SOCKS";
    public static final String INTENT_DISCONNECTED = INTENT_PREFIX + "DISCONNECTED";
    public static final String INTENT_CONNECTED = INTENT_PREFIX + "CONNECTED";
    public static final String INTENT_CONNECTING = INTENT_PREFIX + "CONNECTING";
    public static final String INTENT_DISCONNECTING = INTENT_PREFIX + "DISCONNECTING";
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
            INTENT_IPV6_PROXY = INTENT_PREFIX + "IPV6";
}
