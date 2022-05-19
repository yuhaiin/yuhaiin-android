package net.typeblog.socks;

import android.util.Log;
import yuhaiin.Yuhaiin_;

public class yuhaiin extends Thread {

    private final String host;
    private final String path;
    private final String dnsServer;
    private final String socks5Server;
    private final String httpserver;
    private final String fakedns;
    private final boolean fakednse;
    private final Yuhaiin_ yuhaiin = new Yuhaiin_();

    private final callback callback;

    yuhaiin(String host, String path, String dnsServer, String socks5Server, String httpserver, String fakedns, boolean fakednse, callback callbackstop) {
        this.host = host;
        this.path = path;
        this.dnsServer = dnsServer;
        this.socks5Server = socks5Server;
        this.httpserver = httpserver;
        this.fakednse = fakednse;
        this.fakedns = fakedns;
        this.callback = callbackstop;
    }

    @Override
    public void run() {
        try {
            yuhaiin.start(this.host, this.path, this.dnsServer, this.socks5Server, this.httpserver, this.fakednse, this.fakedns);
        } catch (Exception e) {
            Log.d("yuhaiin", "run: " + e);
        }

        this.callback.run();
    }

    @Override
    public void interrupt() {
        super.interrupt();
        yuhaiin.stop();
    }

    public static class callback {
        public void run() {
        }
    }
}
