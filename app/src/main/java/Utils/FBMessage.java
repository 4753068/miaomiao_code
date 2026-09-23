package Utils;

import static com.qapp.midian.App.AppContext;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import com.qapp.midian.R;

public class FBMessage {

    // ✅ 使用主线程 Handler + WeakReference 避免泄漏
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void Show(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) return;

        MAIN_HANDLER.post(() -> {
            // Android 12+ 自定义View已废弃，统一使用标准Toast
            // 如需自定义样式，必须改用 Snackbar/PopupWindow
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

}