package Utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.webkit.WebView;

import com.qapp.midian.App;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetUtils {
    private static final String TAG = "NetUtils";

    // volatile 保证多线程可见性
    public static volatile int netState = 0;

    /**
     * 判断字符串是否为合法 URL
     */
    public static boolean isHttpUrl(String str) {
        if (TextUtils.isEmpty(str)) return false;
        return Patterns.WEB_URL.matcher(str).matches();
    }

    /**
     * 邮箱验证
     */
    public static boolean isEmail(String strEmail) {
        if (TextUtils.isEmpty(strEmail)) return false;
        return Patterns.EMAIL_ADDRESS.matcher(strEmail).matches();
    }

    /**
     * 获取当前的网络状态：没有网络0：WIFI网络1：移动网络2
     */
    public static int getAPNType() {
        ConnectivityManager connMgr = (ConnectivityManager) App.AppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) return 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connMgr.getActiveNetwork();
            if (network == null) return 0;
            NetworkCapabilities capabilities = connMgr.getNetworkCapabilities(network);
            if (capabilities == null) return 0;

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return 1; // WIFI
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return 2; // 移动网络
            }
        } else {
            // 兼容低版本
            @SuppressWarnings("deprecation")
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) return 0;

            @SuppressWarnings("deprecation")
            int type = networkInfo.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                return 1;
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                return 2;
            }
        }
        return 0;
    }

    public static String getDomain(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            Log.e(TAG, "域名解析失败", e);
            return null;
        }
    }

    /**
     * ⚠️ 注意：此方法必须在主线程调用，否则引发 WebView 崩溃
     */
    public static String getAgent(WebView webView) {
        if (webView == null) return "";
        return webView.getSettings().getUserAgentString();
    }

    /**
     * 获取网络图片并存入缓存（⚠️ 这是一个同步方法，必须在后台线程调用，严禁在主线程使用）
     */
    public static Bitmap GetImageInputStream(String imageUrl, boolean save) {
        if (TextUtils.isEmpty(imageUrl)) return null;

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(imageUrl).build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                byte[] pictureBytes = response.body().bytes();
                Bitmap bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.length);

                if (save && bitmap != null) {
                    File dir = new File(App.Folder + "/capture");
                    if (!dir.exists()) dir.mkdirs();

                    String targetPath = App.Folder + "/capture/" + MD5Utils.stringToMD5(imageUrl) + ".jpg";
                    File file = new File(targetPath);
                    if (file.exists()) file.delete();

                    ImageUtils.saveBitmap(bitmap, targetPath);
                }
                return bitmap;
            }
        } catch (IOException e) {
            Log.e(TAG, "同步获取图片失败: " + imageUrl, e);
        }
        return null;
    }

    /**
     * 安全读取 Asset 下的 JS 文件
     */
    public static String Jquery(Context context) {
        try (InputStream in = context.getAssets().open("jquery.1.9.min.js");
             ByteArrayOutputStream fromFile = new ByteArrayOutputStream()) {
            byte[] buff = new byte[4096];
            int numRead;
            while ((numRead = in.read(buff)) > 0) {
                fromFile.write(buff, 0, numRead);
            }
            return fromFile.toString();
        } catch (IOException e) {
            Log.e(TAG, "读取 Jquery 异常", e);
            return "";
        }
    }

    public static boolean ipCheck(String text) {
        if (TextUtils.isEmpty(text)) return false;
        return Patterns.IP_ADDRESS.matcher(text).matches();
    }

    /**
     * URL 汉字安全编码
     */
    public static String urlEncodeChinese(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]").matcher(url);
            String tmp;
            while (matcher.find()) {
                tmp = matcher.group();
                url = url.replaceAll(tmp, URLEncoder.encode(tmp, "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "URL 编码异常", e);
        }
        return url.replace(" ", "%20");
    }

    /**
     * 解析 URL 参数为 Map (更加安全规范)
     */
    public static Map<String, String> getQueryVariable(String url) {
        Map<String, String> mapRequest = new HashMap<>();
        if (TextUtils.isEmpty(url)) return mapRequest;

        try {
            Uri uri = Uri.parse(url);
            for (String paramName : uri.getQueryParameterNames()) {
                String paramValue = uri.getQueryParameter(paramName);
                mapRequest.put(paramName, paramValue != null ? paramValue : "");
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 URL 参数异常: " + url, e);
        }
        return mapRequest;
    }

    public static void getNewVer(Activity activity, int type) {
        if (activity == null || activity.isFinishing()) return;

        Calendar c = Calendar.getInstance();
        SharedPreferences sp = activity.getSharedPreferences("setting", Context.MODE_PRIVATE);
        int verDay = type == 1 ? 0 : sp.getInt("verday", 0);

        if (verDay != c.get(Calendar.DATE)) {
            String url = "https://app.am930.cn/?data=content&file=music&func=get_new_ver";
            Request request = new Request.Builder()
                    .url(url)
                    .post(new FormBody.Builder().build())
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "检查更新网络异常: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    try {
                        String jsonVal = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonVal);
                        int newVer = jsonObject.optInt("new_version", 0);
                        int currVersionCode = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;

                        if (newVer > currVersionCode) {
                            String newContent = jsonObject.optString("new_content", "");

                            sp.edit().putInt("verday", c.get(Calendar.DATE)).apply();

                            // 如果需要弹出升级提示，请在此处通过 activity.runOnUiThread( () -> {...} ) 处理
                            // Log.i(TAG, "发现新版本: " + newVer + "，更新内容: " + newContent);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "更新数据解析异常", e);
                    }
                }
            });
        }
    }

    /**
     * 测试外网连通性
     */
    public static void getWState() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url("https://www.youtube.com").build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                netState = 0;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 200) {
                    netState = 1;
                } else {
                    netState = 0;
                }
                response.close();
            }
        });
    }
}