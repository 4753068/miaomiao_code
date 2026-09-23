package Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaExtractor;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoFrameExtractor {

    private static final String TAG = "VideoExt";

    /**
     * 获取视频时长并截取第一帧保存为本地图片（高容错版，完美支持 webm / mp4）
     *
     * @param context      上下文（用于 RenderScript）
     * @param videoFile    视频源文件
     * @param thumbOutFile 截图生成的图片输出目标文件
     * @return 视频的时长（毫秒），如果解析失败返回 0
     */
    public static long getVideoDurationAndScreenshot(Context context, File videoFile, File thumbOutFile) {
        if (videoFile == null || !videoFile.exists()) return 0;

        long duration = 0;

        // ================== 【第一轨】先获取时长 ==================
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                duration = Long.parseLong(durationStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "Retriever 获取时长失败，尝试通过 Extractor 获取", e);
        }

        // ================== 【第二轨】尝试传统快速截图 ==================
        boolean screenshotSuccess = false;
        try {
            Bitmap frameBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frameBitmap != null) {
                screenshotSuccess = saveBitmapToFile(context, frameBitmap, thumbOutFile);
                frameBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "传统高级截图失败（常见于 webm 格式），准备启动软解强抽硬切流...", e);
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }

        // ================== 【第三轨】兜底控流：如果传统截图失败（如 webm），启动底层手工抽帧 ==================
        if (!screenshotSuccess) {
            Log.w(TAG, "👉 传统方法失效，正在启动 MediaExtractor 兜底强抽视频帧...");
            screenshotSuccess = decodeFirstFrameFrameFallback(context, videoFile, thumbOutFile);
        }

        // ================== 【第四轨】极端兜底：如果都失败了，放一张默认视频黑底占位 ==================
        if (!screenshotSuccess && thumbOutFile != null) {
            Log.e(TAG, "❌ 视频流完全损毁或无可用解码器，创建空白占位图兜底。");
            createEmptyPlaceholder(thumbOutFile);
        }

        return duration;
    }

    /**
     * 底层硬核抽帧：利用 MediaExtractor 抽取第一个关键帧
     */
    private static boolean decodeFirstFrameFrameFallback(Context context, File videoFile, File thumbOutFile) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(videoFile.getAbsolutePath());
            int trackIndex = -1;
            String mime = null;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    trackIndex = i;
                    break;
                }
            }

            if (trackIndex == -1) return false;
            extractor.selectTrack(trackIndex);

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isFinished = false;
            long timeoutUs = 10000;
            Bitmap bitmap = null;

            while (!isFinished) {
                int inputIndex = decoder.dequeueInputBuffer(timeoutUs);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, timeoutUs);
                if (outputIndex >= 0) {
                    Image image = decoder.getOutputImage(outputIndex);
                    if (image != null) {
                        // ✨【核心修改点】：调用本地重载的转换方法
                        bitmap = getBitmapFromImage(image);
                        image.close();
                    }
                    decoder.releaseOutputBuffer(outputIndex, false);
                    if (bitmap != null) {
                        boolean success = saveBitmapToFile(context, bitmap, thumbOutFile);
                        bitmap.recycle();
                        return success;
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 格式变化，继续循环
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "手工强抽视频帧失败", e);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); decoder.release(); } catch (Exception ignored) {}
            }
            extractor.release();
        }
        return false;
    }

    /**
     * ✨【自主实现方法】：完美将 MediaCodec 解码出来的 YUV_420_888 Image 转换为渲染 Bitmap
     */
    private static Bitmap getBitmapFromImage(Image image) {
        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }

        Rect crop = image.getCropRect();
        int width = crop.width();
        int height = crop.height();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uRowStride = planes[1].getRowStride();
        int vRowStride = planes[2].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // 申请 ARGB 像素数组
        int[] argb = new int[width * height];

        // 高性能逐像素转换矩阵 YUV -> RGB
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yIndex = (y + crop.top) * yRowStride + (x + crop.left);
                int uvX = (x + crop.left) / 2;
                int uvY = (y + crop.top) / 2;
                int uIndex = uvY * uRowStride + uvX * uvPixelStride;
                int vIndex = uvY * vRowStride + uvX * uvPixelStride;

                int Y = yBuffer.get(yIndex) & 0xff;
                int U = uBuffer.get(uIndex) & 0xff;
                int V = vBuffer.get(vIndex) & 0xff;

                // 移除 YUV 偏移量
                Y = Math.max(0, Y - 16);
                U -= 128;
                V -= 128;

                // 标准 YUV 转 RGB 矩阵公式
                int r = (int) (1.164 * Y + 1.596 * V);
                int g = (int) (1.164 * Y - 0.392 * U - 0.813 * V);
                int b = (int) (1.164 * Y + 2.017 * U);

                // 边界数值防溢出拦截
                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));

                argb[y * width + x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        // 创建无缝渲染 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(argb, 0, width, 0, 0, width, height);
        return bitmap;
    }

    /**
     * 将 Bitmap 保存为文件，可选择是否加上模糊效果
     *
     * @param context      上下文（用于 RenderScript）
     * @param bitmap       要保存的 Bitmap
     * @param outFile      输出文件
     * @param blurRadius   模糊半径，如果小于等于 0 则不进行模糊处理
     * @return true 如果保存成功，false 如果失败
     */
    private static boolean saveBitmapToFile(Context context, Bitmap bitmap, File outFile, float blurRadius) {
        if (bitmap == null || outFile == null) return false;
        Bitmap processedBitmap = bitmap;
        if (blurRadius > 0) {
            processedBitmap = blurBitmap(context, bitmap, blurRadius);
        }
        try {
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "写入图片文件异常", e);
        } finally {
            if (processedBitmap != null && processedBitmap != bitmap) {
                processedBitmap.recycle();
            }
        }
        return false;
    }

    /**
     * 重载 saveBitmapToFile 方法，默认不模糊
     */
    private static boolean saveBitmapToFile(Context context, Bitmap bitmap, File outFile) {
        return saveBitmapToFile(context, bitmap, outFile, 0);
    }

    /**
     * 使用 RenderScript 对 Bitmap 进行高斯模糊（增强版：配合缩放实现强模糊）
     *
     * @param context Context 上下文
     * @param bitmap 要模糊的 Bitmap
     * @param radius 模糊半径，取值范围为 (0, 25]
     * @return 模糊后的 Bitmap
     */
    private static Bitmap blurBitmap(Context context, Bitmap bitmap, float radius) {
        if (bitmap == null || radius <= 0) {
            return bitmap;
        }

        // --- 核心优化：先缩小图片 ---
        // 缩放比例，0.1f 表示缩小到原来的 1/10。值越小，最终的模糊效果越强烈，且处理速度越快。
        // 你可以根据需要的模糊程度调整这个值（例如 0.2f, 0.1f, 0.05f）
        float scaleFactor = 0.1f;

        int scaledWidth = Math.round(bitmap.getWidth() * scaleFactor);
        int scaledHeight = Math.round(bitmap.getHeight() * scaleFactor);

        // 防止极端情况下尺寸被算成 0
        scaledWidth = Math.max(1, scaledWidth);
        scaledHeight = Math.max(1, scaledHeight);

        // 创建缩小后的 Bitmap
        Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        // --- 执行模糊操作 ---
        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur scriptIntrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);

        // 确保 radius 在 RenderScript 允许的合法范围内 (0 < radius <= 25)
        float safeRadius = Math.min(25f, Math.max(1f, radius));
        scriptIntrinsicBlur.setRadius(safeRadius);

        scriptIntrinsicBlur.setInput(tmpIn);
        scriptIntrinsicBlur.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        // 释放 RenderScript 资源
        rs.destroy();
        tmpIn.destroy();
        tmpOut.destroy();
        scriptIntrinsicBlur.destroy();

        // --- 核心优化：将模糊后的图片放大回原尺寸 ---
        // 放大时传入 true 使用双线性插值，这本身也会带来一定的平滑模糊效果
        Bitmap finalBitmap = Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);

        // 回收中间产生的临时 Bitmap 释放内存
        if (inputBitmap != bitmap) {
            inputBitmap.recycle();
        }
        if (outputBitmap != finalBitmap) {
            outputBitmap.recycle();
        }

        return finalBitmap;
    }

    private static void createEmptyPlaceholder(File outFile) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLACK);
            saveBitmapToFile(null, bitmap, outFile);
            bitmap.recycle();
        } catch (Exception ignored) {}
    }


}