package Receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.view.View;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.blankj.utilcode.util.NetworkUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;

import Data.ChatUtils;
import Utils.AudioRecorderUtils;
import socket.AutoReconnectWebSocket;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static final String ACTION_NETWORK_STATUS_CHANGED = "com.qapp.midian.NETWORK_STATUS_CHANGED";
    public static final String EXTRA_IS_CONNECTED = "is_connected";
    public static final String EXTRA_NETWORK_TYPE = "network_type"; // "wifi", "mobile", or "none"

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isConnected = NetworkUtils.isConnected();
        String networkType = getNetworkType(context);

        Log.e("NetworkChangeReceiver", "网络状态: " + (isConnected) + ", 类型: " + networkType);

        if (isConnected && App.UID > 0) {
            // ✨【核心修复】：判断 instance 而不是 socket
            if (activity_friend.that != null) {
                activity_friend.nettis.setVisibility(View.GONE);
            }
            if (activity_chat.that!=null)
            {
                activity_chat.top_tis.setVisibility(View.GONE);
            }
            if (AutoReconnectWebSocket.instance != null) {
                if (!AutoReconnectWebSocket.socketConnected.get()) {
                    Log.i("Network", "网络恢复，触发 Socket 重连...");
                    AutoReconnectWebSocket.instance.connect();
                }
            } else {
                // 如果连 instance 都是空的，说明是无网状态下冷启动的 App，直接全量初始化
                Log.i("Network", "网络恢复，初次启动 Socket...");
                AutoReconnectWebSocket.startSocket();
            }
        } else if(!isConnected)
        {
            if (activity_friend.that!=null)
            {
                activity_friend.nettis.setVisibility(View.VISIBLE);
            }

            if (activity_chat.that!=null)
            {
                activity_chat.top_tis.setVisibility(View.VISIBLE);
            }
        }

        Intent localIntent = new Intent(ACTION_NETWORK_STATUS_CHANGED);
        localIntent.putExtra(EXTRA_IS_CONNECTED, isConnected);
        localIntent.putExtra(EXTRA_NETWORK_TYPE, networkType);
        LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent);
    }

    /**
     * 简单判断网络类型：wifi / mobile / none
     */
    private String getNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "none";

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            int type = activeNetwork.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                return "wifi";
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                return "mobile";
            }
        }
        return "none";
    }

    public static String getAction() {
        return ACTION_NETWORK_STATUS_CHANGED;
    }
}