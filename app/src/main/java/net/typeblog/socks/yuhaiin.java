package net.typeblog.socks;
import yuhaiin.Yuhaiin_;
public class yuhaiin extends Thread {

    private final String host;
    private final String path;
    private final String dnsServer;
    private final String socks5Server;
    private final Yuhaiin_ yuhaiin = new Yuhaiin_();
    yuhaiin(String host,String path,String dnsServer,String socks5Server){
        this.host = host;
        this.path = path;
        this.dnsServer = dnsServer;
        this.socks5Server = socks5Server;
    }

    @Override
    public void run() {
        try {
            yuhaiin.start(this.host,this.path,this.dnsServer,this.socks5Server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        yuhaiin.stop();
    }
}
