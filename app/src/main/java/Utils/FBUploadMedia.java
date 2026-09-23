package Utils;

import static com.blankj.utilcode.util.ViewUtils.runOnUiThread;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.blankj.utilcode.util.EncryptUtils;
import com.blankj.utilcode.util.FileUtils;
import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_chat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import Data.ChatUtils;
import socket.AutoReconnectWebSocket;

import com.abedelazizshe.lightcompressorlibrary.CompressionListener;
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor;
import com.abedelazizshe.lightcompressorlibrary.VideoQuality;
import com.abedelazizshe.lightcompressorlibrary.config.Configuration;
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation;
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration;

public class FBUploadMedia {
    private static final String TAG = "FBUploadMedia";

    public static void UploadMedia(Context that, Uri uri) {
        if (that == null || uri == null) return;

        String temporaryName = "upload_" + System.currentTimeMillis();
        long originalSize = 0;

        try (Cursor cursor = that.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String name = cursor.getString(nameIndex);
                    if (name != null && !name.isEmpty()) temporaryName = name;
                }
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    originalSize = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件元数据异常", e);
        }

        final String fileName = temporaryName;

        // OOM 防护拦截：限制最大 50MB
        long maxVideoSize = 50 * 1024 * 1024;
        if (originalSize > maxVideoSize) {
            runOnUiThread(() -> new AlertDialog.Builder(that, R.style.AlertDialog)
                    .setTitle("文件过大")
                    .setMessage("为了保证传输稳定，选择的文件大小不能超过 50MB。")
                    .setPositiveButton("我知道了", null)
                    .show());
            return;
        }

        // 异步读取与处理流，避免大文件阻塞主线程引发 ANR
        new Thread(() -> {
            try {
                // 1. 读取原始文件流
                byte[] fileBytes;
                try (InputStream is = that.getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    if (is == null) {
                        runOnUiThread(() -> FBMessage.Show(that, "无法打开读取文件流"));
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    fileBytes = bos.toByteArray();
                }

                // 2. 获取 MimeType 决定压缩策略
                String mimeType = FileManage.getMimeType(fileBytes);
                if (!mimeType.startsWith("image") && !mimeType.startsWith("video") && !mimeType.startsWith("audio")) {
                    runOnUiThread(() -> FBMessage.Show(that, "该文件类型不支持分享给好友"));
                    return;
                }

                String rawFileMd5 = EncryptUtils.encryptMD5ToString(fileBytes).toLowerCase();
                long time = System.currentTimeMillis();
                String shareMessageId = App.UID + "" + time;
                String messageEnc = AESUtils.encrypt(App.AppContext, "[/MEDIA]");

                // 后缀格式提取
                String ext = "jpg";
                if (mimeType != null) {
                    ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                }
                if (ext == null || ext.isEmpty()) {
                    ext = FileUtils.getFileExtension(fileName);
                }

                if (mimeType.startsWith("video")) {
                    ext = mimeType.toLowerCase().contains("webm") ? "webm" : "mp4";
                } else if (mimeType.startsWith("audio")) {
                    ext = "mp3";
                } else if ("image/gif".equals(mimeType)) {
                    ext = "gif";
                } else if ("image/png".equals(mimeType)) {
                    ext = "png";
                } else if ("image/webp".equals(mimeType)) {
                    ext = "webp";
                }

                // 落盘未加密的解压副本
                File cacheCopyFile = new File(App.Folder + "/Cache/" + rawFileMd5 + "." + ext);
                try (FileOutputStream fosCopy = new FileOutputStream(cacheCopyFile)) {
                    fosCopy.write(fileBytes);
                }

                // 立即生成缩略图
                String thumbFilename = "Cache/" + rawFileMd5 + "_thumb.jpg";
                File thumbFile = new File(App.Folder + "/" + thumbFilename);
                long duration = 0L;

                if (!mimeType.startsWith("image")) {
                    duration = Utils.VideoFrameExtractor.getVideoDurationAndScreenshot(that, cacheCopyFile, thumbFile);
                    Bitmap bitmap = ImageUtils.reset_image_size(App.Folder + "/" + thumbFilename, 200, 0);
                    if (bitmap != null) {
                        ImageUtils.saveBitmap(bitmap, App.Folder + "/" + thumbFilename);
                    }
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length, options);
                    if (bitmap != null) {
                        double ratio = (double) 200 / bitmap.getWidth();
                        int reqHeight = (int) (bitmap.getHeight() * ratio);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, reqHeight, true);
                        ImageUtils.saveBitmap(scaledBitmap, App.Folder + "/" + thumbFilename);
                        if (scaledBitmap != bitmap) bitmap.recycle();
                        bitmap.recycle();
                    }
                }

                // 立即加密缩略图文件
                String newThumbFilename = rawFileMd5 + ".img";
                File newThumbFile = new File(App.Folder + "/Cache", newThumbFilename);
                if (thumbFile.exists()) {
                    byte[] fileBytes_thumb = FileManage.readFileToBytes(thumbFile);
                    byte[] encryptedBytes_thumb = AESUtils.encrypt_byte(App.AppContext, fileBytes_thumb);
                    try (FileOutputStream fos_thumb = new FileOutputStream(newThumbFile)) {
                        fos_thumb.write(encryptedBytes_thumb);
                    }
                }

                // 建立加密占位文件
                String newFilename = rawFileMd5 + ".enc";
                File newFile = new File(App.Folder + "/Cache", newFilename);
                byte[] initialEncryptedBytes = AESUtils.encrypt_byte(App.AppContext, fileBytes);
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    fos.write(initialEncryptedBytes);
                }

                // 组装媒体 JSON 元数据
                JSONObject jo = new JSONObject();
                if (mimeType.startsWith("video")) {
                    jo.put("type", "VIDEO");
                    activity_chat.RecorderDuration = (int) duration;
                } else if ("image/gif".equals(mimeType)) {
                    jo.put("type", "GIF");
                } else if (mimeType.startsWith("audio")) {
                    jo.put("type", "AUDIO");
                } else {
                    jo.put("type", "IMAGE");
                }
                jo.put("path", "Cache/" + newFilename);
                jo.put("url", "");
                jo.put("duration", duration);
                jo.put("thumb", "Cache/" + newThumbFilename);
                jo.put("thumb_path", thumbFilename);
                jo.put("filesize", fileBytes.length);

                // 参数化 SQL 写入本地数据库
                String insertSql = "INSERT INTO chat(MessageId, ToUid, FromUID, Message, Inputtime, IsRead, Yingyong, Mediaurl) VALUES (?, ?, ?, '', ?, 0, '', ?)";
                App.db.execSQL(insertSql, new Object[]{shareMessageId, activity_chat.Friend_UID, App.UID, time, jo.toString()});

                // UI 上屏与进度展示
                final long finalDuration = duration;
                runOnUiThread(() -> {
                    ChatUtils.LocaMessage(messageEnc, "", activity_chat.Friend_UID, jo.toString(), true, false, newFile.getAbsolutePath(), time);
                    activity_chat.updateUploadProgress(shareMessageId, 0, true);
                });

                final byte[] finalFileBytes = fileBytes;
                final String finalExt = ext;

                // 压缩流水线分支处理
                if (mimeType.startsWith("image") && !"image/gif".equals(mimeType)) {
                    new Thread(() -> {
                        byte[] processedBytes = finalFileBytes;
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        Bitmap originalBitmap = BitmapFactory.decodeByteArray(finalFileBytes, 0, finalFileBytes.length, opts);

                        if (originalBitmap != null) {
                            int maxSide = 1920;
                            int width = originalBitmap.getWidth();
                            int height = originalBitmap.getHeight();
                            Bitmap scaledBitmap = originalBitmap;

                            if (width > maxSide || height > maxSide) {
                                float ratio = Math.min((float) maxSide / width, (float) maxSide / height);
                                scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (int) (width * ratio), (int) (height * ratio), true);
                            }

                            ByteArrayOutputStream compressedBos = new ByteArrayOutputStream();
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, compressedBos);
                            processedBytes = compressedBos.toByteArray();

                            if (scaledBitmap != originalBitmap) scaledBitmap.recycle();
                            originalBitmap.recycle();
                        }
                        executeUploadPipeline(that, processedBytes, mimeType, shareMessageId, rawFileMd5, finalExt, finalDuration, newFile, newThumbFile);
                    }).start();

                } else if (mimeType.startsWith("video") && !mimeType.toLowerCase().contains("webm")) {
                    List<Uri> uris = new ArrayList<>();
                    uris.add(uri);

                    List<String> videoNames = new ArrayList<>();
                    videoNames.add(fileName);

                    Configuration configuration = new Configuration(
                            VideoQuality.MEDIUM,
                            true,
                            null,
                            false,
                            false,
                            null,
                            null,
                            videoNames
                    );

                    SharedStorageConfiguration storageConfig = new SharedStorageConfiguration(
                            SaveLocation.movies,
                            "MidianChat"
                    );

                    VideoCompressor.start(
                            that,
                            uris,
                            false,
                            storageConfig,
                            configuration,
                            new CompressionListener() {
                                @Override
                                public void onStart(int index) {}

                                @Override
                                public void onSuccess(int index, long size, String path) {
                                    new Thread(() -> {
                                        try {
                                            File compressedVideoFile = new File(path);
                                            byte[] compressedBytes = FileManage.readFileToBytes(compressedVideoFile);
                                            executeUploadPipeline(that, compressedBytes, mimeType, shareMessageId, rawFileMd5, finalExt, finalDuration, newFile, newThumbFile);
                                        } catch (Exception e) {
                                            Log.e(TAG, "读取压缩视频失败，回退原文件上传", e);
                                            executeUploadPipeline(that, finalFileBytes, mimeType, shareMessageId, rawFileMd5, finalExt, finalDuration, newFile, newThumbFile);
                                        }
                                    }).start();
                                }

                                @Override
                                public void onFailure(int index, String failureMessage) {
                                    new Thread(() -> executeUploadPipeline(that, finalFileBytes, mimeType, shareMessageId, rawFileMd5, finalExt, finalDuration, newFile, newThumbFile)).start();
                                }

                                @Override
                                public void onProgress(int index, float percent) {}

                                @Override
                                public void onCancelled(int index) {}
                            }
                    );

                } else {
                    new Thread(() -> executeUploadPipeline(that, finalFileBytes, mimeType, shareMessageId, rawFileMd5, finalExt, finalDuration, newFile, newThumbFile)).start();
                }

            } catch (Exception e) {
                Log.e(TAG, "UploadMedia 处理失败", e);
            }
        }).start();
    }

    private static void executeUploadPipeline(Context that, byte[] fileBytes, String mimeType, String shareMessageId, String fileMd5, String ext, long duration, File newFile, File newThumbFile) {
        try {
            long fileSize = fileBytes.length;

            // 1. 加密最终字节流覆盖占位文件
            byte[] encryptedBytes = AESUtils.encrypt_byte(App.AppContext, fileBytes);
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(encryptedBytes);
            }

            // 2. 覆盖未加密副本
            File cacheCopyFile = new File(App.Folder + "/Cache/" + fileMd5 + "." + ext);
            try (FileOutputStream fosCopy = new FileOutputStream(cacheCopyFile)) {
                fosCopy.write(fileBytes);
            }

            // 3. 组装最终媒体 JSON
            JSONObject jo = new JSONObject();
            if (mimeType.startsWith("video")) {
                jo.put("type", "VIDEO");
            } else if ("image/gif".equals(mimeType)) {
                jo.put("type", "GIF");
            } else if (mimeType.startsWith("audio")) {
                jo.put("type", "AUDIO");
            } else {
                jo.put("type", "IMAGE");
            }
            jo.put("path", "Cache/" + fileMd5 + ".enc");
            jo.put("url", "");
            jo.put("duration", duration);
            jo.put("thumb", "Cache/" + fileMd5 + ".img");
            jo.put("thumb_path", "Cache/" + fileMd5 + "_thumb.jpg");
            jo.put("filesize", fileSize);

            // 参数化 SQL 更新 chat 记录
            String updateSql = "UPDATE chat SET Mediaurl = ? WHERE MessageId = ?";
            App.db.execSQL(updateSql, new Object[]{jo.toString(), shareMessageId});

            // 4. 发起 WebSocket 上传
            runOnUiThread(() -> {
                if (AutoReconnectWebSocket.socket != null) {
                    if (newThumbFile.exists()) {
                        AutoReconnectWebSocket.socket.uploadFile(newThumbFile, "THUMB_" + shareMessageId);
                    }
                    AutoReconnectWebSocket.socket.uploadFile(newFile, shareMessageId);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "executeUploadPipeline 后台覆写发送失败", e);
        }
    }
}