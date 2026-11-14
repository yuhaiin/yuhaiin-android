package io.github.asutorufa.yuhaiin

/** Persisted/native protocol identifiers. Never translate or derive these from UI labels. */
object Constants {
    /** Cookie the core's platform API expects (mirrors cmd/android PlatformCookieName). */
    const val PLATFORM_COOKIE_NAME = "yuhaiin_android_platform"
    /** Core-owned background policy setting (mirrors cmd/android CoreBackgroundPolicyKey). */
    const val CORE_BACKGROUND_POLICY_KEY = "core_background_policy"
    const val APPEND_HTTP_PROXY_KEY = "Append HTTP Proxy to VPN"
    const val LEGACY_UI_HTTP_PROXY_KEY = "append_http_proxy_to_vpn"
    const val HTTP_PORT_KEY = "http_port"
    const val WEB_PORT_KEY = "yuhaiin_port"
    const val ALLOW_LAN_KEY = "allow_lan"
    const val NETWORK_SPEED_KEY = "network_speed"
    const val NOTIFICATION_SPEED_KEY = "android_notification_speed"
    const val METERED_MODE_KEY = "vpn_metered_mode"
    const val PER_APP_KEY = "per_app"
    const val APP_BYPASS_KEY = "app_bypass"
    const val TUN_DRIVER_KEY = "Tun Driver"
    // Retain the retired stack and driver keys for existing installs. Native
    // startup ignores their legacy selections and always uses lightweight TUN.
    const val TUN_STACK_KEY = "android_tun_stack"
    const val TUN_CONGESTION_CONTROL_KEY = "android_tun_congestion_control"
    const val TUN_OUTPUT_SCHEDULER_KEY = "android_tun_output_scheduler"
    const val TUN_LOSS_RECOVERY_KEY = "android_tun_loss_recovery"
    const val ALL_ROUTE = "All (Default)"
    const val NON_LOCAL_ROUTE = "Non-Local IPs"
    const val SAVED_ROUTES_LIST = "saved_routes_list"
    const val ROUTE_CONTENT_PREFIX = "route_content_"
    const val ROUTE_KEY = "route"
    const val APP_LIST_KEY = "app_list"
    const val AUTO_CONNECT_KEY = "auto_connect"
    const val DNS_HIJACKING_KEY = "dns_hijacking"
    const val SNIFF_KEY = "Sniff"
    const val PROCESS_LOOKUP_MODE_KEY = "process_lookup_mode"
    const val VPN_MTU_PROFILE_KEY = "vpn_mtu_profile"
    const val REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY = "register_underlying_network_callback"
    const val BOOT_CONNECT_POLICY_KEY = "boot_connect_policy"
}
