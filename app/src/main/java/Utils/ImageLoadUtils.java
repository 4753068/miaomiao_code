package Utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import jp.wasabeef.glide.transformations.BlurTransformation;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImageLoadUtils {
    private static final String TAG = "ImageLoadUtils";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * 加载加密的缩略图/图片
     *
     * @param context    上下文
     * @param url        服务器上的加密图片地址
     * @param imageView  目标 ImageView
     * @param thumbFile  本地缓存文件名或相对路径
     */
    public static void loadEncryptedImage(Context context, String url, ImageView imageView, String thumbFile) {
        if (context == null || imageView == null || url == null || url.isEmpty() || thumbFile == null || thumbFile.isEmpty()) {
            return;
        }

        File decryptedFile = new File(App.Folder + "/" + thumbFile);

        // 1. 本地缓存已存在且有效，直接加载
        if (decryptedFile.exists() && decryptedFile.length() > 0) {
            if (isContextValid(context)) {
                Glide.with(context)
                        .load(decryptedFile)
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3)))
                        .into(imageView);
            }
            return;
        }

        // 2. 异步下载并解密
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "下载加密图片失败: " + url, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                try {
                    byte[] encryptedBytes = response.body().bytes();
                    // 调用 AAR 闭源解密方法
                    byte[] decryptedBytes = AESUtils.decrypt_byte(App.AppContext, encryptedBytes);

                    File parent = decryptedFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    // 使用临时文件写入后重命名，防止半写入文件被读取
                    File tempFile = new File(decryptedFile.getAbsolutePath() + ".tmp");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(decryptedBytes);
                        fos.flush();
                    }

                    if (tempFile.renameTo(decryptedFile) || decryptedFile.exists()) {
                        // 切回主线程加载图片
                        if (context instanceof Activity) {
                            Activity activity = (Activity) context;
                            if (isContextValid(activity)) {
                                activity.runOnUiThread(() -> {
                                    if (isContextValid(activity)) {
                                        Glide.with(activity)
                                                .load(decryptedFile)
                                                .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3)))
                                                .into(imageView);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解密或保存图片异常: " + thumbFile, e);
                }
            }
        });
    }

    private static boolean isContextValid(Context context) {
        if (context == null) return false;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return true;
    }
}