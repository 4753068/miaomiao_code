package Utils;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_main;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PushNotificationUtils {

    private static final String TAG = "PushNotificationUtils";
    private static final String CHANNEL_ID = "channel_server_push";
    private static final String CHANNEL_NAME = "系统推送通知";
    private static final String DEFAULT_BASE_URL = "https://member.am930.cn"; // 默认图片域名

    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Android 13+ 动态申请通知权限（在 activity_main 的 onCreate 中调用）
     */
    public static void checkAndRequestPermission(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    /**
     * 显示推送通知
     */
    public static void showNotification(Context context, String title, String content, String thumbUrl, String targetUrl) {
        Context targetContext = (context != null ? context : App.AppContext);
        if (targetContext == null) return;

        final Context appContext = targetContext.getApplicationContext();

        // 检查系统通知开关（Android 13+ 运行时权限未允许也会在此被拦截）
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.e(TAG, "❌ 应用通知权限未开启，无法弹出通知！请在系统设置或运行时允许通知权限");
            return;
        }

        // 补全相对路径 URL，防止 new URL() 崩溃
        final String fixedThumbUrl = formatImageUrl(thumbUrl);

        NOTIFICATION_EXECUTOR.execute(() -> {
            Bitmap thumbBitmap = null;
            if (!TextUtils.isEmpty(fixedThumbUrl)) {
                thumbBitmap = getBitmapFromUrl(fixedThumbUrl);
            }
            sendNotification(appContext, title, content, thumbBitmap, targetUrl);
        });
    }

    /**
     * 自动补齐图片相对路径
     */
    private static String formatImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return DEFAULT_BASE_URL + url;
        }
        return DEFAULT_BASE_URL + "/" + url;
    }

    private static void sendNotification(Context context, String title, String content, Bitmap thumbBitmap, String targetUrl) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // 适配 Android 8.0+ 渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                if (channel == null) {
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    channel.enableVibration(true);
                    channel.setShowBadge(true);
                    manager.createNotificationChannel(channel);
                }
            }

            Intent intent = new Intent(context, activity_main.class);
            intent.putExtra("target_url", targetUrl);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            int notificationId = (int) (System.currentTimeMillis() % 100000);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationId, intent, flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(TextUtils.isEmpty(title) ? "系统消息" : title)
                    .setContentText(content)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent);

            if (thumbBitmap != null) {
                builder.setLargeIcon(thumbBitmap);
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(thumbBitmap)
                        .bigLargeIcon((Bitmap) null)
                        .setSummaryText(content));
            } else if (!TextUtils.isEmpty(content)) {
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(content));
            }

            manager.notify(notificationId, builder.build());
            Log.i(TAG, "✅ 通知已成功发送至系统通知栏: " + title);
        } catch (Exception e) {
            Log.e(TAG, "构建并发送系统通知失败", e);
        }
    }

    private static Bitmap getBitmapFromUrl(String src) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(src);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream input = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(input);
                }
            } else {
                Log.w(TAG, "下载缩略图 HTTP 状态异常: " + connection.getResponseCode() + ", URL: " + src);
            }
        } catch (Exception e) {
            Log.e(TAG, "下载推送缩略图失败: " + src, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}