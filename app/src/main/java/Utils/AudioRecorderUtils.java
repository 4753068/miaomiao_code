package Utils;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.blankj.utilcode.util.EncryptUtils;
import com.qapp.commercial_auth.AESUtils; // 🌟 请确保这里的包名指向你已迁入 AAR 的 AESUtils 路径
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import Data.ChatUtils;
import socket.AutoReconnectWebSocket;

public class AudioRecorderUtils {
    public static final int MAX_LENGTH = 1000 * 60; // 60秒钟最大时长
    public static MediaRecorder mMediaRecorder;
    public static String audioPath;
    public static String ShortFilename = System.currentTimeMillis() + ".mp3";

    public static void startRecord() {
        ShortFilename = System.currentTimeMillis() + ".mp3";
        audioPath = App.Folder + "/Cache/" + ShortFilename;

        mMediaRecorder = new MediaRecorder();
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB); // 选择低码率格式节省空间
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setOutputFile(audioPath);
            mMediaRecorder.setMaxDuration(MAX_LENGTH); // 设置最大时长
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (Exception e) {
            Log.e("AudioRecorder", "开始录音失败", e);
            releaseRecorder();
        }
    }

    public static void stopRecord() {
        // 1. 立即停止录音释放麦克风，保证 UI 响应极速
        try {
            if (mMediaRecorder != null) {
                mMediaRecorder.stop();
            }
        } catch (Exception e) {
            Log.e("AudioRecorder", "停止录音异常", e);
        } finally {
            releaseRecorder();
        }

        // 🌟 核心优化：彻底解决原代码遗留的 “需处理同步或异步问题”
        // 将 MediaPlayer 时长解析、AES高强度加密、文件 IO 全部丢进后台线程，杜绝主线程阻塞与 ANR
        new Thread(() -> {
            try {
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(audioPath);
                mediaPlayer.prepare(); // 此时在异步线程安全阻塞，不会卡顿 UI
                int duration = mediaPlayer.getDuration();
                activity_chat.RecorderDuration = duration; // 单位：毫秒
                mediaPlayer.release();

                Log.e("TBA", (duration / 1000) + "=秒");

                File audioFile = new File(audioPath);

                // 录音太短的逻辑处理
                if (duration < 1000) {
                    // 切回主线程弹 Toast
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity_chat.that != null) {
                            FBMessage.Show(activity_chat.that, "语音太短无法发送");
                        }
                    });
                    if (audioFile.exists()) {
                        audioFile.delete();
                    }
                    return;
                }

                // 文件哈希计算与路径生成
                String fileMd5 = EncryptUtils.encryptMD5File2String(audioFile).toLowerCase();
                String newFilename = fileMd5 + ".enc";
                File newFile = new File(App.Folder + "/Cache/", newFilename);

                // 1. 读取原始文件为 byte[]
                byte[] fileBytes = FileManage.readFileToBytes(audioFile.getAbsoluteFile());

                // 2. 调用 AAR 底层 AES 加密文件内容
                byte[] encryptedBytes = AESUtils.encrypt_byte(App.AppContext, fileBytes);

                // 3. 🌟 使用 try-with-resources 自动释放流，写入加密内容到新文件，杜绝文件锁死
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    fos.write(encryptedBytes);
                }

                // 清理未加密的明文临时音频
                if (audioFile.exists()) {
                    audioFile.delete();
                }

                // 构造网络通信消息
                long fileSizeInBytes = newFile.length();
                JSONObject jo = new JSONObject();
                jo.put("type", "AUDIO");
                jo.put("message", "");
                jo.put("path", "Cache/" + newFilename);
                jo.put("duration", duration);
                jo.put("filesize", fileSizeInBytes);

                activity_chat.CurrMediaFile = newFile;

                long time = System.currentTimeMillis();
                String SendMessageId = App.UID + "" + time;

                if (AutoReconnectWebSocket.socket != null) {
                    AutoReconnectWebSocket.socket.uploadFile(newFile, SendMessageId);
                }

                String MessageEnc = AESUtils.encrypt(App.AppContext, "[/MEDIA]");

                // 🌟 参数化查询防 SQL 注入，同样在异步线程执行保护主线程
                App.db.execSQL(
                        "INSERT INTO chat(MessageId, ToUid, FromUID, Message, Inputtime, IsRead, Yingyong, Mediaurl) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                        new Object[]{SendMessageId, activity_chat.Friend_UID, App.UID, "", time, "", jo.toString()}
                );

                // 最后：只有需要刷新消息列表 UI 时，才切换回主线程
                new Handler(Looper.getMainLooper()).post(() -> {
                    ChatUtils.LocaMessage(MessageEnc, "", activity_chat.Friend_UID, jo.toString(), true, false, newFile.getAbsolutePath(), time);
                });

            } catch (Exception e) {
                Log.e("AudioRecorder", "处理音频文件及加密失败", e);
                // 发生异常时兜底清理残留文件
                try {
                    File f = new File(audioPath);
                    if (f.exists()) f.delete();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static void releaseRecorder() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.release();
            } catch (Exception ignored) {}
            mMediaRecorder = null;
        }
    }

    // 🌟 优化：使用 try-with-resources 杜绝内存泄漏和文件句柄未回收
    public static byte[] readEncryptedFile(String filePath) throws IOException {
        File file = new File(filePath);
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
}