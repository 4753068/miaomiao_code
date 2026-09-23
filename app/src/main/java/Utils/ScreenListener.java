package Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class ScreenListener {
    private static final String TAG = "ScreenListener";

    private final Context mContext;
    private final ScreenBroadcastReceiver mScreenReceiver;
    private ScreenStateListener mScreenStateListener;
    private boolean mIsRegistered = false;

    public ScreenListener(Context context) {
        // 强制使用 ApplicationContext，彻底切断对 Activity 的强引用
        this.mContext = context.getApplicationContext();
        this.mScreenReceiver = new ScreenBroadcastReceiver();
    }

    /**
     * 屏幕状态广播接收者
     */
    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mScreenStateListener == null) {
                return;
            }

            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenStateListener.onScreenOn();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenStateListener.onScreenOff();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mScreenStateListener.onUserPresent();
            }
        }
    }

    /**
     * 开始监听屏幕状态
     */
    public synchronized void begin(ScreenStateListener listener) {
        this.mScreenStateListener = listener;
        registerListener();
        checkCurrentScreenState();
    }

    /**
     * 获取当前屏幕亮灭状态
     */
    private void checkCurrentScreenState() {
        if (mContext == null) return;

        PowerManager manager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (manager == null) return;

        boolean isScreenActive;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            isScreenActive = manager.isInteractive();
        } else {
            //noinspection deprecation
            isScreenActive = manager.isScreenOn();
        }

        if (mScreenStateListener != null) {
            if (isScreenActive) {
                mScreenStateListener.onScreenOn();
            } else {
                mScreenStateListener.onScreenOff();
            }
        }
    }

    /**
     * 停止屏幕状态监听
     */
    public synchronized void unregisterListener() {
        if (!mIsRegistered || mContext == null) {
            return;
        }

        try {
            mContext.unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "广播未注册或已被系统释放: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "注销屏幕监听异常", e);
        } finally {
            mIsRegistered = false;
            mScreenStateListener = null;
        }
    }

    /**
     * 注册屏幕广播接收器（防重入）
     */
    private synchronized void registerListener() {
        if (mIsRegistered || mContext == null) {
            return;
        }

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);

            // 屏幕事件为系统保护广播，无需指定 RECEIVER_EXPORTED
            mContext.registerReceiver(mScreenReceiver, filter);
            mIsRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "注册屏幕状态广播异常", e);
        }
    }

    public interface ScreenStateListener {
        void onScreenOn();
        void onScreenOff();
        void onUserPresent();
    }
}