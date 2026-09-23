package Utils;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 这是一个通用的基类 Activity。
 */
public class WindowUtils extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 执行全屏操作
        setupFullScreen(this);
    }

    public static void setupFullScreen(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;

        Window window = activity.getWindow();

        // 1. 告诉系统：让内容延伸到状态栏和导航栏区域下方
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 2. 使用 WindowCompat 获取 Controller（内部处理了 Null 安全）
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            // 隐藏状态栏
            controller.hide(WindowInsetsCompat.Type.statusBars());
            // 如果想顺便隐藏底部导航栏，可以这样写：
            // controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());

            // 设置沉浸式手势行为
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // 3. 刘海屏适配（保持原样）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    public static void setupTransparentStatusBarWithBlackText(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;

        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.setAppearanceLightStatusBars(true);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        // ================= 新增代码：解决底部导航栏遮挡问题 =================
        // 获取 Activity 的根 View（android.R.id.content 对应你 setContentView 的外层）
        android.view.View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                // 获取导航栏（navigationBars）的高度
                Insets navigationBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

                // 仅仅为根 View 设置底部的 padding，顶部的 padding 保持 0（因为顶部我们要延伸到状态栏）
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navigationBarsInsets.bottom);

                return windowInsets;
            });
        }
    }
}