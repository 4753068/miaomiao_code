package Utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileManage {
    private static final String TAG = "FileManage";

    public static void MkDir(Context context, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "创建目录失败: " + path, e);
        }
    }

    /**
     * 递归删除目录下的所有文件及子目录下所有文件
     */
    public static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return true;

        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean success = deleteDir(child);
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }

    public static void WriteLog(String log) {
        try {
            File path = new File(App.Folder + "/log");
            if (!path.exists()) {
                path.mkdirs();
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault());
            SimpleDateFormat simpleDateFormatFile = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = new Date(System.currentTimeMillis());
            String filename = App.Folder + "/log/" + simpleDateFormatFile.format(date) + ".txt";

            try (FileOutputStream fos = new FileOutputStream(filename, true)) {
                String info = simpleDateFormat.format(date);
                fos.write(info.getBytes());
                fos.write(": ".getBytes());
                fos.write(log.getBytes());
                fos.write("\r\n".getBytes());
                fos.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "写入日志失败", e);
        }
    }

    public static byte[] readFileToBytes(File file) throws IOException {
        if (file == null || !file.exists()) {
            return new byte[0];
        }
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }

    public static byte[] readEncryptedFile(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            return new byte[0];
        }
        return readFileToBytes(new File(filePath));
    }

    public static void getMedia(String path) {
        new Thread(() -> {
            try {
                byte[] enc = readEncryptedFile(path);
                if (enc.length == 0) return;

                byte[] dec = AESUtils.decrypt_byte(App.AppContext, enc);
                String fileType = getMimeType(dec);

                String fullName = path.substring(path.lastIndexOf('/') + 1);
                String baseName = fullName.contains(".")
                        ? fullName.substring(0, fullName.lastIndexOf('.'))
                        : fullName;

                String ext = "bin";
                if ("image/jpeg".equals(fileType)) ext = "jpg";
                else if ("image/png".equals(fileType)) ext = "png";
                else if ("image/gif".equals(fileType)) ext = "gif";
                else if ("image/webp".equals(fileType)) ext = "webp";
                else if ("video/mp4".equals(fileType)) ext = "mp4";
                else if ("video/webm".equals(fileType)) ext = "webm";
                else if ("audio/mpeg".equals(fileType)) ext = "mp3";

                String tempFile = App.Folder + "/Cache/" + baseName + "." + ext;
                File toFile = new File(tempFile);

                try (FileOutputStream fos = new FileOutputStream(toFile)) {
                    fos.write(dec);
                    fos.flush();
                }

            } catch (Exception e) {
                Log.e(TAG, "解密并保存媒体文件失败: " + path, e);
            }
        }).start();
    }

    public static String getMimeType(byte[] data) {
        if (data == null || data.length < 4) return "application/octet-stream";

        // 1. JPEG: FF D8 FF
        if (data.length >= 3 &&
                (data[0] & 0xFF) == 0xFF &&
                (data[1] & 0xFF) == 0xD8 &&
                (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // 2. PNG: 89 50 4E 47
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x89 &&
                (data[1] & 0xFF) == 0x50 &&
                (data[2] & 0xFF) == 0x4E &&
                (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // 3. GIF: 47 49 46 38 -> "GIF8"
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x47 &&
                (data[1] & 0xFF) == 0x49 &&
                (data[2] & 0xFF) == 0x46 &&
                (data[3] & 0xFF) == 0x38) {
            return "image/gif";
        }

        // 4. WEBP: RIFF + WEBP
        if (data.length >= 12 &&
                (data[0] & 0xFF) == 0x52 &&
                (data[1] & 0xFF) == 0x49 &&
                (data[2] & 0xFF) == 0x46 &&
                (data[3] & 0xFF) == 0x46 &&
                (data[8] & 0xFF) == 0x57 &&
                (data[9] & 0xFF) == 0x45 &&
                (data[10] & 0xFF) == 0x42 &&
                (data[11] & 0xFF) == 0x50) {
            return "image/webp";
        }

        // 5. MP4: ftyp
        if (data.length >= 12) {
            for (int i = 0; i <= 8; i++) {
                if (i + 11 < data.length &&
                        (data[i + 4] & 0xFF) == 0x66 &&
                        (data[i + 5] & 0xFF) == 0x74 &&
                        (data[i + 6] & 0xFF) == 0x79 &&
                        (data[i + 7] & 0xFF) == 0x70) {
                    return "video/mp4";
                }
            }
        }

        // 6. MP3
        if (data.length >= 3 &&
                (data[0] & 0xFF) == 0x49 &&
                (data[1] & 0xFF) == 0x44 &&
                (data[2] & 0xFF) == 0x33) {
            return "audio/mpeg";
        }
        if (data.length >= 2 &&
                (data[0] & 0xFF) == 0xFF &&
                ((data[1] & 0xF0) == 0xF0)) {
            return "audio/mpeg";
        }

        // 7. WebM
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x1A &&
                (data[1] & 0xFF) == 0x45 &&
                (data[2] & 0xFF) == 0xDF &&
                (data[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }

        // BitmapFactory 只读头盲测兜底
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outMimeType != null && !options.outMimeType.isEmpty()) {
                return options.outMimeType;
            }
        } catch (Exception ignored) {}

        return "application/octet-stream";
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }
}