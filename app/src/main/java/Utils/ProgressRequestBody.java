package Utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ProgressRequestBody extends RequestBody {

    public interface UploadProgressListener {
        /**
         * 上传进度回调（已自动切换至主线程）
         *
         * @param bytesWritten  已上传字节数
         * @param contentLength 文件总字节数（如果未知则为 -1）
         */
        void onProgress(long bytesWritten, long contentLength);
    }

    private final RequestBody delegate;
    private final UploadProgressListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProgressRequestBody(@NonNull RequestBody delegate, @Nullable UploadProgressListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        // 每次由 OkHttp 调用 writeTo 时都基于当前的 sink 进行包装，兼容重定向和日志拦截器
        CountingSink countingSink = new CountingSink(sink);
        BufferedSink bufferedSink = Okio.buffer(countingSink);

        delegate.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    private class CountingSink extends ForwardingSink {
        private long bytesWritten = 0L;
        private long totalLength = 0L;

        CountingSink(Sink delegateSink) {
            super(delegateSink);
        }

        @Override
        public void write(@NonNull Buffer source, long byteCount) throws IOException {
            super.write(source, byteCount);
            bytesWritten += byteCount;

            if (totalLength == 0L) {
                totalLength = contentLength();
            }

            if (listener != null) {
                final long written = bytesWritten;
                final long total = totalLength;
                // 调度回主线程回调，保障前端 UI 操作安全
                mainHandler.post(() -> listener.onProgress(written, total));
            }
        }
    }
}