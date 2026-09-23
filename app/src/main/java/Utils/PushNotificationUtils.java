package Utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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

    // 复用单线程线程池，避免高频推送时重复创建线程池引发泄漏
    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 显示推送通知
     *
     * @param context   上下文
     * @param title     标题
     * @param content   内容
     * @param thumbUrl  缩略图/大图网络地址
     * @param targetUrl 点击通知后打开的目标链接
     */
    public static void showNotification(Context context, String title, String content, String thumbUrl, String targetUrl) {
        Context targetContext = (context != null ? context : App.AppContext);
        if (targetContext == null) return;

        // 使用 ApplicationContext 防止持有 Activity 导致内存泄漏
        final Context appContext = targetContext.getApplicationContext();

        // 检查通知权限是否被允许（兼容 Android 13+）
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.w(TAG, "应用通知权限未开启，取消弹窗通知展示");
            return;
        }

        NOTIFICATION_EXECUTOR.execute(() -> {
            Bitmap thumbBitmap = null;
            if (!TextUtils.isEmpty(thumbUrl)) {
                thumbBitmap = getBitmapFromUrl(thumbUrl);
            }
            sendNotification(appContext, title, content, thumbBitmap, targetUrl);
        });
    }

    private static void sendNotification(Context context, String title, String content, Bitmap thumbBitmap, String targetUrl) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.enableVibration(true);
                channel.setShowBadge(true);
                manager.createNotificationChannel(channel);
            }
        }

        // 构建跳转 Intent
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
                .setSmallIcon(R.drawable.ic_stat_message) // 状态栏使用单色透明小图标
                .setContentTitle(title)
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
    }

    private static Bitmap getBitmapFromUrl(String src) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(src);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream input = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(input);
                }
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