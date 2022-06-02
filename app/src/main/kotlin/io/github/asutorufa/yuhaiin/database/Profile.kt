package io.github.asutorufa.yuhaiin.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey @ColumnInfo(
        name = "profile_name",
        defaultValue = "Default"
    ) val name: String,
    @ColumnInfo(name = "http_server_port", defaultValue = "8188") var httpServerPort: Int = 8188,
    @ColumnInfo(
        name = "socks5_server_port",
        defaultValue = "1080"
    ) var socks5ServerPort: Int = 1080,
    @ColumnInfo(name = "yuhaiin_port", defaultValue = "50051") var yuhaiinPort: Int = 50051,
    @ColumnInfo(name = "user_pw", defaultValue = "false") var isUserPw: Boolean = false,
    @ColumnInfo(name = "username") var username: String = "",
    @ColumnInfo(name = "password") var password: String = "",
    @ColumnInfo(name = "route", defaultValue = "All (Default)") var route: String = "All (Default)",
    @ColumnInfo(
        name = "fake_dns_cidr",
        defaultValue = "10.0.2.1/24"
    ) var fakeDnsCidr: String = "10.0.2.1/24",
    @ColumnInfo(name = "dns_port", defaultValue = "35353") var dnsPort: Int = 35353,
    @ColumnInfo(name = "per_app", defaultValue = "false") var isPerApp: Boolean = false,
    @ColumnInfo(name = "bypassApp", defaultValue = "false") var isBypassApp: Boolean = false,
    @ColumnInfo(name = "app_list") var appList: Set<String> = HashSet(),
    @ColumnInfo(name = "ipv6", defaultValue = "false") var hasIPv6: Boolean = false,
    @ColumnInfo(name = "auto_connect", defaultValue = "false") var autoConnect: Boolean = false,
    @ColumnInfo(name = "save_logcat", defaultValue = "false") var saveLogcat: Boolean = false,
    @ColumnInfo(name = "allow_lan", defaultValue = "false") var allowLan: Boolean = false,
    @ColumnInfo(name = "rule_block") var ruleBlock: String = "",
    @ColumnInfo(name = "rule_proxy") var ruleProxy: String = "",
    @ColumnInfo(name = "rule_direct") var ruleDirect: String = "",
)
