package net.typeblog.socks.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import net.typeblog.socks.SocksVpnService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import static net.typeblog.socks.util.Constants.*;

public class Utility {
    private static final String TAG = Utility.class.getSimpleName();

    public static Process exec(String cmd, boolean wait) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(cmd);
        Thread th = new Thread(() -> {
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            try {
                String line;
                while ((line = input.readLine()) != null) {
                    System.out.println(line);
                }
                input.close();
            }catch (Exception e) {
                e.printStackTrace();
            }
        });
        th.start();
        if (wait) {
            p.waitFor();
        }
        return  p;
    }

    public static Process exec(String cmd) throws IOException, InterruptedException {
        return exec(cmd, false);
    }

    public static String join(List<String> list, String separator) {
        if (list.isEmpty()) return "";

        StringBuilder ret = new StringBuilder();

        for (String s : list) {
            ret.append(s).append(separator);
        }

        return ret.substring(0, ret.length() - separator.length());
    }

    public static  Process startYuhaiin(Context context,String host) throws IOException, InterruptedException {
        String cmd = context.getApplicationInfo().nativeLibraryDir +"/libyuhaiin.so"+
                " -path "+context.getExternalFilesDir("yuhaiin")+
                " -host "+host;
        Log.d(TAG, "startYuhaiin: "+cmd);
        return exec(cmd,false);
    }

    public static void startVpn(Context context, Profile profile, Handler handler) {
        class StartVpnThread implements Runnable {
            final Context context;
            final Profile profile;
            final Handler handler;

            public StartVpnThread(Context context, Profile profile, Handler handler) {
                this.context = context;
                this.profile = profile;
                this.handler = handler;
            }

            @Override
            public void run() {
                Intent i = new Intent(context, SocksVpnService.class)
                        .putExtra(INTENT_NAME, profile.getName())
                        .putExtra(INTENT_SERVER, profile.getServer())
                        .putExtra(INTENT_PORT, profile.getPort())
                        .putExtra(INTENT_ROUTE, profile.getRoute())
                        .putExtra(INTENT_DNS, profile.getDns())
                        .putExtra(INTENT_DNS_PORT, profile.getDnsPort())
                        .putExtra(INTENT_PER_APP, profile.isPerApp())
                        .putExtra(INTENT_IPV6_PROXY, profile.hasIPv6())
                        .putExtra(PREF_YUHAIIN_HOST,profile.getYuhaiinHost());

                if (profile.isUserPw()) {
                    i.putExtra(INTENT_USERNAME, profile.getUsername())
                            .putExtra(INTENT_PASSWORD, profile.getPassword());
                }

                if (profile.isPerApp()) {
                    i.putExtra(INTENT_APP_BYPASS, profile.isBypassApp())
                            .putExtra(INTENT_APP_LIST, profile.getAppList().split("\n"));
                }

                if (profile.hasUDP()) {
                    i.putExtra(INTENT_UDP_GW, profile.getUDPGW());
                }

                context.startService(i);

                if (handler != null) {
                    Bundle data = new Bundle();
                    data.putBoolean("success", true);
                    Message msg = new Message();
                    msg.setData(data);
                    handler.sendMessage(msg);
                }
            }
        }

        new Thread(new StartVpnThread(context, profile, handler)).start();
    }
}
