package Utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.qapp.midian.App;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MultiDownloadHelper {

    private static final String TAG = "MultiDownloadHelper";
    private int threadCount;
    private String downloadUrl;
    private String downloadTitle;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;

    // 使用原子操作或同步锁累计总下载量
    private volatile long totalDownloaded = 0;

    public interface DownloadListener {
        void onProgress(long currentSize, long totalSize);
        void onSuccess(String filePath);
        void onFailure(String errorMsg);
    }

    public MultiDownloadHelper(int threadCount, String downloadUrl, String title) {
        this.threadCount = Math.max(1, threadCount);
        this.downloadUrl = downloadUrl;
        this.downloadTitle = title;
        this.httpClient = new OkHttpClient();
    }

    private String getLocalFilePath() throws Exception {
        String ext = "";
        try {
            String path = new URL(downloadUrl).getPath();
            if (path != null && path.contains(".")) {
                ext = path.substring(path.lastIndexOf("."));
            }
        } catch (Exception ignored) {}

        if (ext.isEmpty()) ext = ".tmp";

        // 确保父目录存在
        File dir = new File(App.Folder);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return App.Folder + "/" + downloadTitle + ext;
    }

    public void download(final DownloadListener listener) {
        Request request = new Request.Builder()
                .url(downloadUrl)
                // 仅请求头，快速获取文件总大小
                .head()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                notifyFailure(listener, "获取文件大小失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    notifyFailure(listener, "HTTP Error: " + response.code());
                    response.close();
                    return;
                }

                long fileLength = 0;
                String contentLength = response.header("Content-Length");
                if (contentLength != null) {
                    fileLength = Long.parseLong(contentLength);
                }
                response.close();

                if (fileLength <= 0) {
                    notifyFailure(listener, "无法获取文件总长度，不支持多线程分段下载");
                    return;
                }

                try {
                    String localPath = getLocalFilePath();
                    File file = new File(localPath);
                    if (file.exists()) {
                        file.delete();
                    }

                    // 预分配本地文件空间
                    try (RandomAccessFile raf = new RandomAccessFile(localPath, "rwd")) {
                        raf.setLength(fileLength);
                    }

                    long blockSize = fileLength / threadCount;
                    totalDownloaded = 0;

                    for (int i = 0; i < threadCount; i++) {
                        long startPos = i * blockSize;
                        long endPos = (i == threadCount - 1) ? fileLength - 1 : (startPos + blockSize - 1);
                        new DownloadThread(i, startPos, endPos, localPath, fileLength, listener).start();
                    }
                } catch (Exception e) {
                    notifyFailure(listener, "初始化本地文件异常: " + e.getMessage());
                }
            }
        });
    }

    private class DownloadThread extends Thread {
        private final int threadId;
        private final long startPos;
        private final long endPos;
        private final String localPath;
        private final long totalFileSize;
        private final DownloadListener listener;

        public DownloadThread(int threadId, long startPos, long endPos, String localPath, long totalFileSize, DownloadListener listener) {
            this.threadId = threadId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.localPath = localPath;
            this.totalFileSize = totalFileSize;
            this.listener = listener;
        }

        @Override
        public void run() {
            // 🌟 核心修复点：为 HTTP 请求加上 Range 头，真正的分片下载
            Request request = new Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Range", "bytes=" + startPos + "-" + endPos)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    notifyFailure(listener, "分片下载失败(线程" + threadId + "): " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.code() != 206 && response.code() != 200) { // 206 Partial Content
                        notifyFailure(listener, "分片请求失败: Code=" + response.code());
                        response.close();
                        return;
                    }

                    try (InputStream is = response.body().byteStream();
                         RandomAccessFile raf = new RandomAccessFile(localPath, "rwd")) {

                        raf.seek(startPos);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            raf.write(buffer, 0, len);

                            // 同步累加总进度并回调主线程
                            synchronized (MultiDownloadHelper.this) {
                                totalDownloaded += len;
                                if (listener != null) {
                                    long current = totalDownloaded;
                                    mainHandler.post(() -> listener.onProgress(current, totalFileSize));
                                }
                            }
                        }

                        // 如果全部线程下载完毕
                        if (totalDownloaded >= totalFileSize) {
                            // 广播通知系统相册扫描
                            Uri uri = Uri.fromFile(new File(localPath));
                            App.AppContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));

                            mainHandler.post(() -> {
                                if (listener != null) listener.onSuccess(localPath);
                            });
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "写入文件流异常: ", e);
                    } finally {
                        response.close();
                    }
                }
            });
        }
    }

    private void notifyFailure(DownloadListener listener, String errorMsg) {
        Log.e(TAG, errorMsg);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFailure(errorMsg);
            }
        });
    }
}