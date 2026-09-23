package Utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class ProgressResponseBody_Upload extends ResponseBody {

    public interface ProgressListener {
        /**
         * 下载/读取响应体进度回调（已调度至主线程）
         *
         * @param percent         下载进度百分比（0-100，若总大小未知则为 -1）
         * @param downloadedBytes 已接收字节数
         * @param totalBytes      文件总字节数（未知则为 -1）
         */
        void onProgress(int percent, long downloadedBytes, long totalBytes);
    }

    private final ResponseBody originalResponseBody;
    private final ProgressListener listener;
    private final Handler mainHandler;
    private BufferedSource bufferedSource;

    public ProgressResponseBody_Upload(@NonNull ResponseBody originalResponseBody, @Nullable ProgressListener listener) {
        this.originalResponseBody = originalResponseBody;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return originalResponseBody.contentType();
    }

    @Override
    public long contentLength() {
        return originalResponseBody.contentLength();
    }

    @NonNull
    @Override
    public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(originalResponseBody.source()));
        }
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            private long totalBytesRead = 0L;
            private int lastPercent = -1;

            @Override
            public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                totalBytesRead += (bytesRead != -1 ? bytesRead : 0);

                if (listener != null) {
                    final long currentRead = totalBytesRead;
                    final long totalBytes = originalResponseBody.contentLength();

                    int progress = -1;
                    if (totalBytes > 0) {
                        progress = (int) ((100 * currentRead) / totalBytes);
                    }

                    // 进度发生跳变或是最后一个数据包时回调主线程，降低高频分片对主线程消息队列的冲刷
                    if (progress != lastPercent || bytesRead == -1) {
                        lastPercent = progress;
                        final int finalProgress = progress;
                        mainHandler.post(() -> listener.onProgress(finalProgress, currentRead, totalBytes));
                    }
                }

                return bytesRead;
            }
        };
    }
}