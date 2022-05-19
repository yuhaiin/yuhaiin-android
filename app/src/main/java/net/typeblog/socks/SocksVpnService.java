package net.typeblog.socks;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.*;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import net.typeblog.socks.util.Routes;
import net.typeblog.socks.util.Utility;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import static net.typeblog.socks.BuildConfig.DEBUG;
import static net.typeblog.socks.util.Constants.*;

public class SocksVpnService extends VpnService {
    private static final String PRIVATE_VLAN4_CLIENT = "172.19.0.1";
    private static final String PRIVATE_VLAN4_ROUTER = "172.19.0.2";
    private static final String PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2";
    private static final String TAG = SocksVpnService.class.getSimpleName();
    private static int VPN_MTU = 1500;
    private static String PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1";
    private final IBinder mBinder = new VpnBinder();
    private final ConnectivityManager.NetworkCallback defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(new Network[]{network});
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(new Network[]{network});
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(null);
            }
        }
    };
    ConnectivityManager mg;
    private ParcelFileDescriptor mInterface;
    private boolean mRunning = false;
    private Thread yuhaiin;
    private Process tun2socks;


    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getApplicationContext().sendBroadcast(new Intent(INTENT_CONNECTING));

        if (DEBUG) {
            Log.d(TAG, "starting");
        }

        if (intent == null) {
            getApplicationContext().sendBroadcast(new Intent(INTENT_DISCONNECTED));
            return START_STICKY;
        }

        if (mRunning) {
            getApplicationContext().sendBroadcast(new Intent(INTENT_CONNECTED));
            return START_STICKY;
        }

        final String name = intent.getStringExtra(INTENT_NAME);
        final int httpServerPort = intent.getIntExtra(INTENT_HTTP_SERVER_PORT, 8188);
        final int socks5ServerPort = intent.getIntExtra(INTENT_SOCKS5_SERVER_PORT, 1080);
        final String username = intent.getStringExtra(INTENT_USERNAME);
        final String passwd = intent.getStringExtra(INTENT_PASSWORD);
        final String route = intent.getStringExtra(INTENT_ROUTE);
        final String fakeDnsCidr = intent.getStringExtra(INTENT_FAKE_DNS_CIDR);
        final int dnsPort = intent.getIntExtra(INTENT_DNS_PORT, 53);
        final boolean perApp = intent.getBooleanExtra(INTENT_PER_APP, false);
        final boolean appBypass = intent.getBooleanExtra(INTENT_APP_BYPASS, false);
        final String[] appList = intent.getStringArrayExtra(INTENT_APP_LIST);
        final boolean ipv6 = intent.getBooleanExtra(INTENT_IPV6_PROXY, false);
        final String yuhaiinHost = intent.getStringExtra(PREF_YUHAIIN_HOST);

        // Notifications on Oreo and above need a channel
        String NOTIFICATION_CHANNEL_ID = "io.github.asutorufa.yuhaiin";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN);
            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        // Create the notification
        int NOTIFICATION_ID = 1;
//        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            intentFlags |= PendingIntent.FLAG_MUTABLE;
//        }
//        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
//                new Intent(this, MainActivity.class), intentFlags);
        startForeground(NOTIFICATION_ID, builder
                .setContentTitle("yuhaiin running")
                .setContentText(String.format(getString(R.string.notify_msg), name))
                .setSmallIcon(R.drawable.ic_vpn)
//                .setContentIntent(contentIntent)
                .build());

        // Create an fd.
        configure(name, route, fakeDnsCidr, httpServerPort, perApp, appBypass, appList, ipv6);

        int fd = mInterface.getFd();

        if (DEBUG)
            Log.d(TAG, "fd: " + fd);

        if (mInterface != null)
            start(yuhaiinHost, httpServerPort, socks5ServerPort, username, passwd, fakeDnsCidr, dnsPort, ipv6);

        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        stopMe();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMe();
    }

    private void stopMe() {
        getApplicationContext().sendBroadcast(new Intent(INTENT_DISCONNECTING));
        stopForeground(true);
//        if (yuhaiin != null) yuhaiin.destroy();

        if (yuhaiin != null) {
            yuhaiin.interrupt();
            yuhaiin = null;
        }

        if (tun2socks != null) {
            tun2socks.destroy();
            tun2socks = null;
        }

        stopSelf();
        try {
            mInterface.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mRunning = false;
        getApplicationContext().sendBroadcast(new Intent(INTENT_DISCONNECTED));
    }

    private void configure(String name, String route, String fakedns, int httpserverport, boolean perApp, boolean bypass, String[] apps, boolean ipv6) {
        Builder b = new Builder();
        b.setMtu(VPN_MTU)
                .setSession(name)
                .addAddress(PRIVATE_VLAN4_CLIENT, 24)
                .addDnsServer(PRIVATE_VLAN4_ROUTER);

        if (ipv6) {
            // Route all IPv6 traffic
            b.addAddress(PRIVATE_VLAN6_CLIENT, 126)
                    .addRoute("::", 0);
        }

        Routes.addRoutes(this, b, route);
        Routes.addRoute(b, fakedns);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mg == null) mg = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest req = new NetworkRequest.Builder().
                    addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                    build();
            mg.requestNetwork(req, defaultNetworkCallback);
        }

        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && httpserverport != 0) {
            b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpserverport));
        }

        // Add the default DNS
        // Note that this DNS is just a stub.
        // Actual DNS requests will be redirected through pdnsd.
//        b.addRoute("223.5.5.5", 32);

        // Do app routing
        if (!perApp) {
            // Just bypass myself
            try {
                b.addDisallowedApplication(getApplicationInfo().packageName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (bypass) {
                // First, bypass myself
                try {
                    b.addDisallowedApplication(getApplicationInfo().packageName);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                for (String p : apps) {
                    if (TextUtils.isEmpty(p))
                        continue;

                    try {
                        b.addDisallowedApplication(p.trim());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                for (String p : apps) {
                    if (TextUtils.isEmpty(p) || p.trim().equals(getApplicationInfo().packageName)) {
                        continue;
                    }

                    try {
                        b.addAllowedApplication(p.trim());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        mInterface = b.establish();
    }

    private void start(String host, int httpport, int socks5port, String user, String passwd, String fakeDnsCidr, int dnsPort, boolean ipv6) {
        if (!Objects.equals(host, "")) {
            try {
                yuhaiin = new yuhaiin(host,
                        this.getExternalFilesDir("yuhaiin").getAbsolutePath(),
                        "127.0.0.1:" + dnsPort,
                        "127.0.0.1:" + socks5port,
                        "127.0.0.1:" + httpport, fakeDnsCidr,
                        !Objects.equals(fakeDnsCidr, ""),
                        new yuhaiin.callback() {
                            @Override
                            public void run() {
                                stopMe();
                            }
                        });

                yuhaiin.start();
//                yuhaiin = Utility.startYuhaiin(this, host);
                Toast.makeText(this, "start yuhaiin success, listen at: " + host + ".", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "failed to start yuhaiin");
                Toast.makeText(this, "start yuhaiin failed.", Toast.LENGTH_LONG).show();
                stopMe();
                return;
            }
        }

        String command = String.format(Locale.US,
                "%s/libtun2socks.so"
                        + " --netif-ipaddr " + PRIVATE_VLAN4_ROUTER
//                        + " --netif-netmask 255.255.255.252"
                        + " --socks-server-addr 127.0.0.1:%d"
//                        + " --tunfd %d"
                        + " --tunmtu 1500"
                        + " --loglevel debug"
//                        + " --pid %s/tun2socks.pid"
                        + " --sock-path %s/sock_path"
                , getApplicationInfo().nativeLibraryDir, socks5port, getApplicationInfo().dataDir);

        if (user != null) {
            command += " --username " + user;
            command += " --password " + passwd;
        }

        if (ipv6) {
            command += " --netif-ip6addr " + PRIVATE_VLAN6_ROUTER;
        }

        command += " --dnsgw 127.0.0.1:" + dnsPort + " --enable-udprelay";

        if (DEBUG) {
            Log.d(TAG, command);
        }

        try {
            tun2socks = Utility.exec(command);
            Toast.makeText(this, "start tun2socks success.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            stopMe();
            return;
        }

        // Try to send the Fd through socket.
        try {
            Log.d(TAG, "send sock_path:" + new File(getApplicationInfo().dataDir + "/sock_path").getAbsolutePath());
            sendFd(new File(getApplicationInfo().dataDir + "/sock_path").getAbsolutePath());
            mRunning = true;
            getApplicationContext().sendBroadcast(new Intent(INTENT_CONNECTED));
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Should not get here. Must be a failure.
        stopMe();
    }

    private void sendFd(String path) throws IOException, InterruptedException {
        int tries = 0;
        FileDescriptor fd = mInterface.getFileDescriptor();
        while (true) {
            try {
                Log.d(TAG, "sdFd tries: " + tries);
                Thread.sleep(140L * tries);
                try (LocalSocket localSocket = new LocalSocket()) {
                    localSocket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
                    localSocket.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                    localSocket.getOutputStream().write(42);
                }
                break;
            } catch (Exception e) {
                if (tries > 5) throw e;
                tries += 1;
            }
        }
    }

    class VpnBinder extends IVpnService.Stub {
        @Override
        public boolean isRunning() {
            return mRunning;
        }

        @Override
        public void stop() {
            stopMe();
        }

    }
}
