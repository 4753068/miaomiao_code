package Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

public class HomeWatcher {

    private static final String TAG = "HomeWatcher";
    private final Context mContext;
    private OnHomePressedListener mListener;
    private InnerReceiver mReceiver;
    private boolean mIsWatching = false;

    // 回调接口
    public interface OnHomePressedListener {
        void onHomePressed();
        void onHomeLongPressed();
    }

    public HomeWatcher(Context context) {
        this.mContext = context.getApplicationContext();
    }

    /**
     * 设置监听
     */
    public void setOnHomePressedListener(OnHomePressedListener listener) {
        mListener = listener;
        if (mReceiver == null) {
            mReceiver = new InnerReceiver();
        }
    }

    /**
     * 开始监听，注册广播
     */
    public synchronized void startWatch() {
        if (mIsWatching || mContext == null) {
            return;
        }

        if (mReceiver == null) {
            mReceiver = new InnerReceiver();
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        try {
            // Android 13+ (API 33+) 显式声明 RECEIVER_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                mContext.registerReceiver(mReceiver, filter);
            }
            mIsWatching = true;
            Log.d(TAG, "HomeWatcher 广播注册成功");
        } catch (SecurityException se) {
            // 兼容 Android 12+ 对 ACTION_CLOSE_SYSTEM_DIALOGS 的系统级权限拦截限制
            Log.w(TAG, "系统限制监听 ACTION_CLOSE_SYSTEM_DIALOGS: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "注册 Home 键广播失败", e);
        }
    }

    /**
     * 停止监听，注销广播
     */
    public synchronized void stopWatch() {
        if (!mIsWatching || mContext == null || mReceiver == null) {
            return;
        }

        try {
            mContext.unregisterReceiver(mReceiver);
            Log.d(TAG, "HomeWatcher 广播注销成功");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "广播未注册或已被系统回收: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "注销广播异常", e);
        } finally {
            mIsWatching = false;
        }
    }

    /**
     * 广播接收者
     */
    private class InnerReceiver extends BroadcastReceiver {
        private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
        private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        private static final String SYSTEM_DIALOG_REASON_FS_GESTURE = "fs_gesture";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (reason != null && mListener != null) {
                    if (SYSTEM_DIALOG_REASON_HOME_KEY.equals(reason) || SYSTEM_DIALOG_REASON_FS_GESTURE.equals(reason)) {
                        // 短按 Home 键或全面屏手势返回桌面
                        mListener.onHomePressed();
                    } else if (SYSTEM_DIALOG_REASON_RECENT_APPS.equals(reason)) {
                        // 长按 Home 键或进入多任务界面
                        mListener.onHomeLongPressed();
                    }
                }
            }
        }
    }
}