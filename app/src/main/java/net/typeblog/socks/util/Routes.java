package net.typeblog.socks.util;

import android.content.Context;
import android.net.VpnService;
import android.util.Log;
import net.typeblog.socks.R;

import java.util.Arrays;

import static net.typeblog.socks.util.Constants.ROUTE_CHN;
import static net.typeblog.socks.util.Constants.ROUTE_NO_LOCAL;

public class Routes {
    public static void addRoutes(Context context, VpnService.Builder builder, String name) {
        String[] routes;
        if(ROUTE_CHN.equals(name)) {
            routes = context.getResources().getStringArray(R.array.simple_route);
        }else if(ROUTE_NO_LOCAL.equals(name)){
            routes = context.getResources().getStringArray(R.array.all_routes_except_local);
        } else {
            routes = new String[]{"0.0.0.0/0"};
        }

        for (String r : routes){
            addRoute(builder,r);
        }
    }

    public static void addRoute(VpnService.Builder builder,String cidr){
            String[] cidrs = cidr.split("/");

            // Cannot handle 127.0.0.0/8
            if (cidrs.length == 2 && !cidrs[0].startsWith("127")) {
                try {
                    int mask = Integer.parseInt(cidrs[1]);
                    builder.addRoute(getNetworkId(cidrs[0],mask), mask);
                }catch (Exception e){
                    e.printStackTrace();
                    Log.d("yuhaiin", "addRoute: "+cidr+" "+ Arrays.toString(cidrs));
                }
            }
    }

    private static String getNetworkId(String ip,int maskStr){
        String mask = getMask(maskStr);
        String[] ips = ip.split("\\.");
        String[] masks = mask.split("\\.");
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<4;i++){
            sb.append(Integer.parseInt(ips[i])&Integer.parseInt(masks[i]));
            if(i!=3){
                sb.append(".");
            }
        }
        return sb.toString();
    }
    private static String getMask(int maskLength){
        int binaryMask = 0xFFFFFFFF << (32 - maskLength);
        StringBuilder sb = new StringBuilder();
        for(int shift=24;shift>0;shift-=8){
            sb.append((binaryMask >>> shift) & 0xFF);
            sb.append(".");
        }
        sb.append(binaryMask & 0xFF);
        return sb.toString();
    }
}
