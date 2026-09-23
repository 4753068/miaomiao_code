package Utils;

import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseIntArray;

import com.qapp.midian.App;

public class RingUtils {

    private static final String TAG = "RingUtils";
    private static SoundPool mSoundPool;
    // 映射关系：resId -> soundId (sampleId)
    private static final SparseIntArray mSoundMap = new SparseIntArray();
    // 映射关系：soundId -> resId (用于反查)
    private static final SparseIntArray mSampleToResMap = new SparseIntArray();
    private static int mCurrentStreamId = 0; // 记录当前正在播放的音频流ID
    private static int mPendingResId = 0; // 正在等待加载并播放的资源ID

    // raw 目录下的铃声资源 ID
    private static final int[] RINGTONE_RES_IDS = {
            com.qapp.midian.R.raw.message,
            com.qapp.midian.R.raw.ddddd,
            com.qapp.midian.R.raw.didudu,
            com.qapp.midian.R.raw.dingdong,
            com.qapp.midian.R.raw.dongdong,
            com.qapp.midian.R.raw.dddong
    };

    /**
     * 初始化 SoundPool 并配置全局唯一监听器
     */
    private static synchronized void initSoundPool() {
        if (mSoundPool == null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mSoundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audioAttributes)
                    .build();

            // 全局只注册一次完成监听，避免并发多次覆盖
            mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                if (status == 0) { // 加载成功
                    int targetResId = mSampleToResMap.get(sampleId);
                    if (targetResId != 0) {
                        mSoundMap.put(targetResId, sampleId);
                    }
                    // 仅当加载完成的文件是用户最新期望播放的铃声时才出声
                    if (targetResId == mPendingResId) {
                        stopRing();
                        mCurrentStreamId = soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                        mPendingResId = 0;
                    }
                } else {
                    Log.e(TAG, "SoundPool 音频样本加载失败: sampleId=" + sampleId + ", status=" + status);
                }
            });
        }
    }

    /**
     * 播放指定编号的铃声（从 1 开始对应数组序列）
     */
    public static synchronized void PlayRing(int which) {
        if (which < 1 || which > RINGTONE_RES_IDS.length) {
            Log.e(TAG, "铃声序号越界: " + which);
            return;
        }

        initSoundPool();
        stopRing();

        int resId = RINGTONE_RES_IDS[which - 1];
        int soundId = mSoundMap.get(resId, 0);

        if (soundId != 0) {
            // 内存缓存中已存在，直接播放
            mPendingResId = 0;
            mCurrentStreamId = mSoundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            // 记录当前正在等待加载的资源，发起异步加载
            mPendingResId = resId;
            int newSampleId = mSoundPool.load(App.AppContext, resId, 1);
            mSampleToResMap.put(newSampleId, resId);
        }
    }

    /**
     * 停止当前正在播放的声音
     */
    public static synchronized void stopRing() {
        if (mSoundPool != null && mCurrentStreamId != 0) {
            mSoundPool.stop(mCurrentStreamId);
            mCurrentStreamId = 0;
        }
    }

    /**
     * 完全释放 SoundPool 实例及 Native 资源
     */
    public static synchronized void release() {
        stopRing();
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
            mSoundMap.clear();
            mSampleToResMap.clear();
            mPendingResId = 0;
        }
    }
}