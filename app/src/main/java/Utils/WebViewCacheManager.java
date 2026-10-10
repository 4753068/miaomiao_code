package Utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

public class WebViewCacheManager {

    private static final String TAG = "WebViewCache";
    private static final String KEY_LAST_CLEAN_TIME = "key_webview_last_clean_time";
    // 6 小时 = 6 * 60 * 60 * 1000 毫秒
    private static final long CLEAN_INTERVAL = 6 * 60 * 60 * 1000L;

    /**
     * 检查距离上次清理是否已满 6 小时，若满则执行清理
     */
    public static void checkAndCleanIfExpired(Context context, WebView webView) {
        long lastCleanTime = SPUtils.getInstance().get(KEY_LAST_CLEAN_TIME, 0L);
        long now = System.currentTimeMillis();

        if (now - lastCleanTime >= CLEAN_INTERVAL) {
            clearWebViewCache(context, webView);
            SPUtils.getInstance().put(KEY_LAST_CLEAN_TIME, now);
            Log.i(TAG, "⏰ 已达到 6 小时间隔，WebView 缓存已自动清理");
        }
    }

    /**
     * 执行实际的 WebView 缓存清理（强制在主线程执行）
     */
    public static void clearWebViewCache(Context context, WebView webView) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (webView != null) {
                    // 1. 清除当前 WebView 实例的资源与内存缓存
                    webView.clearCache(true);
                    webView.clearHistory();
                    webView.clearFormData();
                }

                // 2. 清除 HTML5 Web SQL / IndexedDB / LocalStorage 缓存
                WebStorage.getInstance().deleteAllData();

                // 3. （可选）如果不需要保留登录态 Session，可解开下行清理 Cookie
                // CookieManager.getInstance().removeAllCookies(null);
                // CookieManager.getInstance().flush();

                Log.i(TAG, "✅ WebView 缓存清理成功！");
            } catch (Exception e) {
                Log.e(TAG, "清理 WebView 缓存失败", e);
            }
        });
    }
}