package Utils;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.File;

public class MediaInfoProvider {

    private static final String TAG = "MediaInfoProvider";

    /**
     * 媒体文件信息实体类
     */
    public static class MediaInfo {
        public String mimeType = "";    // 文件的 MIME 类型 (例如: video/mp4, image/jpeg)
        public long fileSize = 0;       // 文件大小 (Bytes)
        public long duration = 0;       // 视频/音频长度 (毫秒)
        public int width = 0;           // 视频/图片宽度 (像素)
        public int height = 0;          // 视频/图片高度 (像素)

        @Override
        public String toString() {
            return "MediaInfo{" +
                    "mimeType='" + mimeType + '\'' +
                    ", fileSize=" + fileSize +
                    ", duration=" + duration +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

    /**
     * 核心方法：获取多媒体文件的综合信息
     *
     * @param file 目标媒体文件
     * @return MediaInfo 包含文件类型、大小、尺寸、时长等信息
     */
    public static MediaInfo getMediaInfo(File file) {
        MediaInfo info = new MediaInfo();

        if (file == null || !file.exists()) {
            return info;
        }

        // 1. 获取文件大小
        info.fileSize = file.length();

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());

            // 2. 获取 MIME 类型
            String mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mimeType != null) {
                info.mimeType = mimeType;
            }

            // 3. 获取时长 (音频和视频通用)
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null && !durationStr.isEmpty()) {
                info.duration = Long.parseLong(durationStr);
            }

            // 4. 获取视频尺寸 (如果是视频文件)
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            if (widthStr != null && !widthStr.isEmpty()) {
                info.width = Integer.parseInt(widthStr);
            }
            if (heightStr != null && !heightStr.isEmpty()) {
                info.height = Integer.parseInt(heightStr);
            }

            // 5. 针对部分特殊视频的旋转角度矫正
            String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotationStr != null && !rotationStr.isEmpty()) {
                int rotation = Integer.parseInt(rotationStr);
                if (rotation == 90 || rotation == 270) {
                    // 如果旋转了90度或270度，宽高对调才是真实的显示宽高
                    int temp = info.width;
                    info.width = info.height;
                    info.height = temp;
                }
            }

        } catch (NumberFormatException e) {
            Log.e(TAG, "解析媒体元数据数值异常: " + file.getName(), e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "不支持的媒体格式或文件已损坏: " + file.getName(), e);
        } catch (Exception e) {
            Log.e(TAG, "获取媒体信息异常", e);
        } finally {
            try {
                // 必须释放底层 Native 资源
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "释放 MediaMetadataRetriever 异常", e);
            }
        }

        return info;
    }
}