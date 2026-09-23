package Utils;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_main;

import org.json.JSONArray;
import org.json.JSONObject;

public class Notification {
    private static final String TAG = "NotificationUtil";
    private static final String CHANNEL_ID = "Message";
    private static final String CHANNEL_NAME = "蜜电消息提醒";
    private static final int PID = 8888;

    private static NotificationManager notificationManager;

    /**
     * 初始化通知管理器与通知渠道（Android 8.0+ 强制要求）
     */
    private static void initManager(Context context) {
        if (notificationManager == null && context != null) {
            // 必须使用 ApplicationContext，防止持有 Activity 导致内存泄漏
            notificationManager = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        }

        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                // 开启通知渠道桌面角标支持
                channel.setShowBadge(true);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 生成通知通用的点击 Intent
     */
    private static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, activity_main.class);
        Bundle bundle = new Bundle();
        bundle.putString("from", "Notification");
        intent.putExtra("data_bundle", bundle);

        // Android 12 (API 31+) 必须显式声明 FLAG_IMMUTABLE 或 FLAG_MUTABLE
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    /**
     * 基础通知推送统一封装
     */
    private static void postNotification(Context context, String title, String content, int largeIconId, int unreadCount) {
        if (context == null) return;
        initManager(context);

        if (notificationManager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message) // 状态栏必须使用单色/透明背景小图标
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(createPendingIntent(context))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setAutoCancel(true);

        // 设置彩色应用大图标
        if (largeIconId != 0) {
            try {
                builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), largeIconId));
            } catch (Exception e) {
                Log.e(TAG, "加载大图标资源失败", e);
            }
        }

        // 绑定角标数量（适配主流国产系统桌面启动器）
        if (unreadCount > 0) {
            builder.setNumber(unreadCount);
        }

        notificationManager.notify(PID, builder.build());
    }

    /**
     * 伪装应用常驻/运行状态通知
     */
    @SuppressLint("NotificationTrampoline")
    public static void createNotification(Context context, String content) {
        if (context == null) return;

        try {
            int siteItem = SPUtils.getInstance().get("setting_appitem", 0);
            JSONArray jsonArray = new JSONArray(HomePageUtils.Site);

            if (siteItem < 0 || siteItem >= jsonArray.length()) {
                siteItem = 0;
            }

            JSONObject jsonObject = jsonArray.getJSONObject(siteItem);
            String title = jsonObject.optString("name", "系统") + "正在运行中";

            int iconResId;
            // 🌟 修复图标映射错误：已完全对齐 HomePageUtils.Site 的索引定义
            switch (siteItem) {
                case 3:
                    iconResId = R.mipmap.app_xigua;
                    break;
                case 4:
                    iconResId = R.mipmap.app_douyin;
                    break;
                case 5:
                    iconResId = R.mipmap.app_taobao;
                    break;
                case 6:
                    iconResId = R.mipmap.app_wps;
                    break;
                case 0:
                case 1:
                case 2:
                default:
                    iconResId = R.drawable.ic_stat_message;
                    break;
            }

            updateNotificationIcon(context, iconResId, title, content);
        } catch (Exception e) {
            Log.e(TAG, "构建伪装通知异常", e);
        }
    }

    /**
     * 更新带有自定义彩色大图标的通知
     */
    public static void updateNotificationIcon(Context context, int newIconResId, String title, String content) {
        postNotification(context, title, content, newIconResId, 0);
    }

    /**
     * 新消息提醒并显示指定未读角标数
     *
     * @param unreadCount 未读消息总数
     */
    public static void NewMessageWithCount(int unreadCount) {
        postNotification(App.AppContext, "信息提醒", "请及时查看系统信息", 0, unreadCount);
    }

    /**
     * 移除所有已发送的消息通知
     */
    public static void RemoveMessage() {
        if (notificationManager != null) {
            try {
                notificationManager.cancelAll();
            } catch (Exception e) {
                Log.e(TAG, "取消所有通知失败", e);
            }
        }
    }
}