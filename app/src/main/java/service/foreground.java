package service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.blankj.utilcode.util.ActivityUtils;
import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_main;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import Utils.HomePageUtils;
import Utils.SPUtils;

public class foreground extends Service {
    public static Context context = null;
    public static foreground that = null;

    public static int PID = 9991;
    public static final String CHANNEL_ID = "Foreground";
    public static String CHANNEL_NAME = "喵喵常驻通知";

    private static NotificationChannel notificationChannel = null;
    private static android.app.Notification notification = null;
    private static NotificationCompat.Builder builder = null;
    private static NotificationManager manager = null;
    private static RemoteViews notificationLayout;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        that = this;
        context = this;
        // 1. 立即启动前台通知（确保避开后台启动限制）
        startForeground();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 核心修复：之前的 ID 错写成了 9990，导致通知栏的常驻通知关不掉，这里改为正确的 PID
        if (manager != null) {
            manager.cancel(PID);
        }
        stopForeground(true);
    }

    @SuppressLint({"NotificationTrampoline", "ForegroundServiceType"})
    public void startForeground() {
        // 1. 提前构建好跳转到当前应用通知设置页面的 PendingIntent
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            // 兼容老版本的写法
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        }

        // 适配 Android 12+ (API 31+) 对 PendingIntent 可变性的硬性要求
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, flags);

        // 2. 根据系统版本构建 Notification 实例
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 核心修改 1：将 IMPORTANCE_HIGH 改为 IMPORTANCE_MIN (最低重要度)
            notificationChannel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN);

            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(false);
            notificationChannel.setSound(null, null);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            manager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(notificationChannel);
            }

            try {
                int siteItem = SPUtils.getInstance().get("setting_appitem", 0);
                JSONArray jsonArray = new JSONArray(HomePageUtils.Site);
                if (siteItem < 0 || siteItem >= jsonArray.length()) {
                    siteItem = 0;
                    SPUtils.getInstance().put("setting_appitem", 0);
                }

                JSONObject jsonObject = null;
                String titleText = "安全防护";
                if (jsonArray.length() > 0) {
                    jsonObject = jsonArray.getJSONObject(siteItem);
                    titleText = jsonObject.getString("name");
                }

                builder = new NotificationCompat.Builder(this, CHANNEL_ID);
                builder.setContentTitle(titleText + " 服务正在运行");
                builder.setContentText("如需关闭本条通知，请点击这里，然后关闭”喵喵常驻通知“");

                // 核心添加：绑定点击事件
                builder.setContentIntent(pIntent);

                builder.setSound(null);
                builder.setOngoing(true); // 设为常驻通知，用户无法滑动删除
                builder.setWhen(System.currentTimeMillis());
                builder.setSmallIcon(R.drawable.ic_stat_logo);
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
                builder.setShowWhen(false);

            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            notification = builder.build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("系统服务")
                    .setContentText("核心链路已就绪")
                    .setSmallIcon(R.drawable.ic_stat_logo)
                    .setContentIntent(pIntent) // 👈 老版本同样绑定点击事件
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
        }

        // 3. 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.startForeground(PID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            this.startForeground(PID, notification);
        }
    }

    /**
     * 新增方法：无论在第几级页面，强行拉起 activity_main 并清空之上的所有子页面
     */
    private void backToMainActivity() {
        try {
            Intent intent = new Intent(this, activity_main.class);
            // 1. Service 启动 Activity 必须加 NEW_TASK
            // 2. CLEAR_TOP 和 SINGLE_TOP 会把 activity_main 之上的所有子页面全部清理掉
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            // 备用方案：如果上面由于特殊原因失败，则使用工具类保底
            if (activity_main.that != null) {
                ActivityUtils.finishToActivity(activity_main.that, false);
            }
        }
    }
}