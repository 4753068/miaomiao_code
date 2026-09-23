package Utils;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.qapp.midian.App;

public class HomePageUtils {
    private static final String TAG = "HomePageUtils";

    // 规范化双引号标准 JSON 字符串
    public static final String Site = "[" +
            "{\"name\":\"喵喵\",\"url\":\"https://member.am930.cn/app/home/view/app_home.php\",\"pkg\":\"com.qapp.midian.MainActivity\"}," +
            "{\"name\":\"喵喵日历\",\"url\":\"https://member.am930.cn/app/monipage/rili.html\",\"pkg\":\"com.qapp.midian.Activity_Rili\"}," +
            "{\"name\":\"Flappy Bird\",\"url\":\"https://member.am930.cn/app/monipage/flappybird.html\",\"pkg\":\"com.qapp.midian.Activity_FlappyBird\"}," +
            "{\"name\":\"西瓜视频\",\"url\":\"https://member.am930.cn/app/monipage/xigua.html\",\"pkg\":\"com.qapp.midian.Activity_Xigua\"}," +
            "{\"name\":\"抖音\",\"url\":\"https://member.am930.cn/app/monipage/douyin.html\",\"pkg\":\"com.qapp.midian.Activity_Douyin\"}," +
            "{\"name\":\"淘宝\",\"url\":\"https://member.am930.cn/app/monipage/taobao.html\",\"pkg\":\"com.qapp.midian.Activity_Taobao\"}," +
            "{\"name\":\"WPS Office\",\"url\":\"https://member.am930.cn/app/monipage/wps.html\",\"pkg\":\"com.qapp.midian.Activity_WPS\"}" +
            "]";

    private static final String DEFAULT_COMPONENT_NAME = "com.qapp.midian.activity_main";

    private static final String[] ALIAS_NAMES = {
            "com.qapp.midian.Activity_Rili",
            "com.qapp.midian.Activity_FlappyBird",
            "com.qapp.midian.Activity_Xigua",
            "com.qapp.midian.Activity_Douyin",
            "com.qapp.midian.Activity_Taobao",
            "com.qapp.midian.Activity_WPS"
    };

    public static void changeLauncher(String name) {
        if (name == null || name.isEmpty() || App.AppContext == null) {
            return;
        }

        try {
            PackageManager packageManager = App.AppContext.getPackageManager();
            String packageName = App.AppContext.getPackageName();
            ComponentName defaultComponent = new ComponentName(packageName, DEFAULT_COMPONENT_NAME);
            ComponentName targetComponent = new ComponentName(packageName, name);

            // 1. 优先启用目标组件
            packageManager.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );

            // 2. 状态互斥处理：如果切换的目标不是主入口，则禁用主入口；反之则启用主入口
            if (!name.equals(DEFAULT_COMPONENT_NAME)) {
                packageManager.setComponentEnabledSetting(
                        defaultComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                );
            } else {
                packageManager.setComponentEnabledSetting(
                        defaultComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                );
            }

            // 3. 禁用其他所有非目标别名
            for (String alias : ALIAS_NAMES) {
                if (!alias.equals(name)) {
                    packageManager.setComponentEnabledSetting(
                            new ComponentName(packageName, alias),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                }
            }

            // 4. 重启应用刷新系统桌面图标缓存
            restartApp();

        } catch (Exception e) {
            Log.e(TAG, "切换桌面启动图标失败: " + name, e);
        }
    }

    public static String getCurrentLauncher() {
        if (App.AppContext == null) {
            return DEFAULT_COMPONENT_NAME;
        }

        PackageManager pm = App.AppContext.getPackageManager();
        String packageName = App.AppContext.getPackageName();

        for (String alias : ALIAS_NAMES) {
            try {
                ComponentName componentName = new ComponentName(packageName, alias);
                int enabledSetting = pm.getComponentEnabledSetting(componentName);

                if (enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return alias;
                }
            } catch (Exception ignored) {}
        }
        return DEFAULT_COMPONENT_NAME;
    }

    public static void restartApp() {
        if (App.AppContext == null) return;

        try {
            Intent intent = App.AppContext.getPackageManager()
                    .getLaunchIntentForPackage(App.AppContext.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                App.AppContext.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "重启应用失败", e);
        }
    }
}