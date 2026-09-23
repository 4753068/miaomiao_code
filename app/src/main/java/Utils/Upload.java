package Utils;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;
import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_media;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import socket.AutoReconnectWebSocket;

public class Upload {
    public static JSONObject FromJson = new JSONObject();
    public static int MessageId;
/*
    private static Handler handler = new Handler() {
        @SuppressLint("HandlerLeak")
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                if (ChatUtils.ReadCount==0)
                {
                    FloatingX.install(MainActivity.helper).hide();
                    FloatingX.install(MainActivity.helper_message).hide();
                    FloatingX.install(MainActivity.helper_nonet).show();
                }
            }

        }
    };

 */
    public Upload(int messageId)
    {
        MessageId=messageId;
    }
    public static void Download_IMG(String Url,String _MessagId)
    {
        String[] parts = Url.split("/");

        File file=new File(App.Folder+"/Cache/"+parts[parts.length-1]);
        if (!file.exists())
        {
            //Log.e("TBA","开始下载文件"+Url);
            // 进度回调实现
            ProgressResponseBody_Upload.ProgressListener progressListener = new ProgressResponseBody_Upload.ProgressListener() {
                @Override
                public void onProgress(int percent, long downloadedBytes, long totalBytes) {

                }
                private String formatSize(long bytes) {
                    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
                }
            };

            Upload.downloadImage(Url, App.Folder+"/Cache/"+parts[parts.length-1],progressListener, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        Upload.saveToFile(response.body().byteStream(),parts[parts.length-1]);

                        if (!parts[parts.length-1].contains(".mp3"))
                        {
                            if (activity_media.that!=null)
                            {
                                activity_media.Mediaurl=App.Folder+"/Cache/"+parts[parts.length-1];
                                //ShowMediaActivity.getMedia();
                            }

                        }

                    }
                }
            });

        }
    }
    // 异步下载（推荐）
    public static void downloadImage(String url, String fileName,
                                     ProgressResponseBody_Upload.ProgressListener listener, Callback callback) {

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept-Encoding","identity")
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody_Upload(originalResponse.body(), listener))
                            .build();
                })
                .build();
        client.newCall(request).enqueue(callback);
    }

    // 同步下载（需在子线程执行）
    public static void downloadImageSync(String url, String fileName) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                saveToFile(response.body().byteStream(), fileName);
            }
        }
    }

    public static void saveToFile(InputStream inputStream, String fileName) throws IOException {
        // 获取下载目录（可根据需求修改路径）

        File file = new File(App.Folder+"/Cache", fileName);
        try (OutputStream outputStream = new FileOutputStream(file);
             BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
            }
        }
    }

    public static String getMimeType(byte[] data) {
        if (data == null || data.length < 4) return "application/octet-stream";

        // 检查 JPEG: FF D8 FF
        if (data.length >= 3 &&
                (data[0] & 0xFF) == 0xFF &&
                (data[1] & 0xFF) == 0xD8 &&
                (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // 检查 PNG: 89 50 4E 47
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x89 &&
                (data[1] & 0xFF) == 0x50 &&
                (data[2] & 0xFF) == 0x4E &&
                (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // 检查 GIF: 47 49 46 38 -> "GIF8"
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x47 &&
                (data[1] & 0xFF) == 0x49 &&
                (data[2] & 0xFF) == 0x46 &&
                (data[3] & 0xFF) == 0x38) {
            return "image/gif";
        }

        // 检查 WEBP: RIFF + WEBP
        if (data.length >= 12 &&
                (data[0] & 0xFF) == 0x52 && // 'R'
                (data[1] & 0xFF) == 0x49 && // 'I'
                (data[2] & 0xFF) == 0x46 && // 'F'
                (data[3] & 0xFF) == 0x46 && // 'F'
                (data[8] & 0xFF) == 0x57 && // 'W'
                (data[9] & 0xFF) == 0x45 && // 'E'
                (data[10] & 0xFF) == 0x42 && // 'B'
                (data[11] & 0xFF) == 0x50) { // 'P'
            return "image/webp";
        }

        // 检查 MP4: 通常以 ftyp 开头（位置可能在偏移 4～8）
        // 格式: [size][ftype][...]
        if (data.length >= 12) {
            for (int i = 0; i <= 8; i++) {
                if (i + 11 < data.length &&
                        (data[i + 4] & 0xFF) == 0x66 && // 'f'
                        (data[i + 5] & 0xFF) == 0x74 && // 't'
                        (data[i + 6] & 0xFF) == 0x79 && // 'y'
                        (data[i + 7] & 0xFF) == 0x70) { // 'p'
                    return "video/mp4";
                }
            }
        }

        // 可继续添加其他类型：AVI, MKV, PDF 等

        return "application/octet-stream"; // 未知类型
    }

    public static byte[] readEncryptedFile(String filePath) throws IOException {
        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }
        fis.close();
        return bos.toByteArray();
    }

}
