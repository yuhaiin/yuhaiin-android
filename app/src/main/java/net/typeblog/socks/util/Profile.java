package net.typeblog.socks.util;

import android.content.SharedPreferences;

import static net.typeblog.socks.util.Constants.*;

public class Profile {
    private final SharedPreferences mPref;
    private final String mName;
    private final String mPrefix;

    Profile(SharedPreferences pref, String name) {
        mPref = pref;
        mName = name;
        mPrefix = prefPrefix(name);
    }

    public String getName() {
        return mName;
    }

    public int getHttpServerPort() {
        return mPref.getInt(key("http_server_port"), 8188);
    }

    public void setHttpServerPort(int server) {
        mPref.edit().putInt(key("http_server_port"), server).apply();
    }

    public int getSocks5ServerPort() {
        return mPref.getInt(key("socks5_server_port"), 1080);
    }

    public void setSocks5ServerPort(int port) {
        mPref.edit().putInt(key("socks5_server_port"), port).apply();
    }

    public boolean isUserPw() {
        return mPref.getBoolean(key("userpw"), false);
    }

    public void setIsUserpw(boolean is) {
        mPref.edit().putBoolean(key("userpw"), is).apply();
    }

    public String getUsername() {
        return mPref.getString(key("username"), "");
    }

    public void setUsername(String username) {
        mPref.edit().putString(key("username"), username).apply();
    }

    public String getPassword() {
        return mPref.getString(key("password"), "");
    }

    public void setPassword(String password) {
        mPref.edit().putString(key("password"), password).apply();
    }

    public String getRoute() {
        return mPref.getString(key("route"), ROUTE_ALL);
    }

    public void setRoute(String route) {
        mPref.edit().putString(key("route"), route).apply();
    }

    public String getFakeDnsCidr() {
        return mPref.getString(key("fake_dns_cidr"), "192.0.2.1/24");
    }

    public void setFakeDnsCidr(String dns) {
        mPref.edit().putString(key("fake_dns_cidr"), dns).apply();
    }

    public int getDnsPort() {
        return mPref.getInt(key("dns_port"), 35353);
    }

    public void setDnsPort(int port) {
        mPref.edit().putInt(key("dns_port"), port).apply();
    }

    public boolean isPerApp() {
        return mPref.getBoolean(key("perapp"), false);
    }

    public void setIsPerApp(boolean is) {
        mPref.edit().putBoolean(key("perapp"), is).apply();
    }

    public boolean isBypassApp() {
        return mPref.getBoolean(key("appbypass"), false);
    }

    public void setIsBypassApp(boolean is) {
        mPref.edit().putBoolean(key("appbypass"), is).apply();
    }

    public String getAppList() {
        return mPref.getString(key("applist"), "");
    }

    public void setAppList(String list) {
        mPref.edit().putString(key("applist"), list).apply();
    }

    public boolean hasIPv6() {
        return mPref.getBoolean(key("ipv6"), false);
    }

    public void setHasIPv6(boolean has) {
        mPref.edit().putBoolean(key("ipv6"), has).apply();
    }

    public boolean hasUDP() {
        return mPref.getBoolean(key("udp"), false);
    }

    public String getUDPGW() {
        return mPref.getString(key("udpgw"), "127.0.0.1:7300");
    }

    public boolean autoConnect() {
        return mPref.getBoolean(key("auto"), false);
    }

    public void setAutoConnect(boolean auto) {
        mPref.edit().putBoolean(key("auto"), auto).apply();
    }

    public  void setYuhaiinHost(String host) {mPref.edit().putString(key("yuhaiin_host"),host).apply();}
    public String getYuhaiinHost() {return  mPref.getString(key("yuhaiin_host"),"127.0.0.1:50051");}
    void delete() {
        mPref.edit()
                .remove(key("server"))
                .remove(key("port"))
                .remove(key("userpw"))
                .remove(key("username"))
                .remove(key("password"))
                .remove(key("route"))
                .remove(key("dns"))
                .remove(key("dns_port"))
                .remove(key("perapp"))
                .remove(key("appbypass"))
                .remove(key("applist"))
                .remove(key("ipv6"))
                .remove(key("udp"))
                .remove(key("udpgw"))
                .remove(key("auto"))
                .apply();
    }

    private String key(String k) {
        return mPrefix + k;
    }

    private static String prefPrefix(String name) {
        return name.replace("_", "__").replace(" ", "_");
    }
}
