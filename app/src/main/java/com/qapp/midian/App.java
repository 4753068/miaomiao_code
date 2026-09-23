package com.qapp.midian;

import android.app.Application;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.blankj.utilcode.util.ActivityUtils;
import com.qapp.commercial_auth.CommercialBillingImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import Data.MemberBillingBridge;
import Receiver.NetworkChangeReceiver;
import Utils.Database;
import Utils.FBMessage;
import Utils.FileManage;
import Utils.HomeWatcher;
import Utils.SPUtils;
import Utils.ScreenListener;
import io.reactivex.annotations.NonNull;

public class App extends Application {
    // 静态服务地址将在 onCreate() 中从 strings.xml 统一读取初始化
    public static final String DataServiceUrl = "https://member.am930.cn";
    public static final String CosUrl = "https://cos.am930.cn";

    public static int PID = 8803;
    public static Notification notification = null;

    public static String Folder = null;

    private ScreenListener screenListener = null;
    public static LinkedList<String> recentEmojis = new LinkedList<>();

    // 数据库与上下文
    public static Database database = null;
    public static SQLiteDatabase db = null;
    public static Context AppContext = null;
    public static int UID = 0;
    public static boolean isErrorPassword = false; // 输错密码
    public static List<String> ShareFileType = new ArrayList<>();
    public static boolean FriendPass = false;
    public static AppCompatActivity activity = null;
    public HomeWatcher mHomeWatcher = null;

    // 分享与版本
    public static List<Uri> ShareUris = new ArrayList<>();
    public static String ShareText = "";
    public static String UpdateUrl = "";

    // 全局变量：应用是否在后台
    private static boolean isAppInBackground = false;
    public static int currHour = 0; // 定时删除时间

    @Override
    public void onCreate() {
        super.onCreate();
        AppContext = getApplicationContext();

        Data.MemberBillingBridge.register(CommercialBillingImpl.createInstance());

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // 应用进入前台时静默校验一次
                if (App.UID > 0 && MemberBillingBridge.get() != null) {
                    MemberBillingBridge.get().handleUserAuth("foreground", App.UID);
                }
            }
        });

        Folder = this.getFilesDir() + "/";

        // 初始化 SPUtils
        SPUtils.init(this);
        if (SPUtils.getInstance().contains("uid")) {
            UID = (int) SPUtils.getInstance().get("uid", 0);
        }

        registerHomeListener();
        initScreenListener();

        // 注册网络状态监听
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(new NetworkChangeReceiver(), intentFilter);

        BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean connected = intent.getBooleanExtra(NetworkChangeReceiver.EXTRA_IS_CONNECTED, true);
                if (!connected) {
                    // 读取 strings.xml 提示文案
                    FBMessage.Show(context, context.getString(R.string.net_error_check));
                }
            }
        };

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(networkStatusReceiver, new IntentFilter(NetworkChangeReceiver.getAction()));

        File Folder_Cache = new File(App.Folder + "/Cache");
        if (!Folder_Cache.exists()) {
            FileManage.MkDir(AppContext, App.Folder + "/Cache");
        }

        // 连接数据库
        database = new Database((Context) getApplicationContext());
        db = database.getWritableDatabase();

        ShareFileType.add("jpeg");
        ShareFileType.add("jpg");
        ShareFileType.add("png");
        ShareFileType.add("bmp");
        ShareFileType.add("gif");
        ShareFileType.add("mp3");
        ShareFileType.add("mp4");
        ShareFileType.add("webm");
        ShareFileType.add("webp");

        String defaultSocketUrl = getString(R.string.default_socket_url);
        String defaultSocketPort = getString(R.string.default_socket_port);

        // 只有当渠道中提供了非空服务配置时才写入默认 SP
        if (!defaultSocketUrl.isEmpty() && !SPUtils.getInstance().contains("SocketUrl")) {
            SPUtils.getInstance().put("SocketName", getString(R.string.default_socket_name));
            SPUtils.getInstance().put("SocketUrl", defaultSocketUrl);
            SPUtils.getInstance().put("SocketPort", defaultSocketPort);
        }

        if (!SPUtils.getInstance().contains("setting_appitem")) {
            SPUtils.getInstance().put("setting_appitem", 0);
        }

        if (!SPUtils.getInstance().contains("OpenSound")) {
            SPUtils.getInstance().put("OpenSound", true);
        }

        if (!SPUtils.getInstance().contains("OpenVibrate")) {
            SPUtils.getInstance().put("OpenVibrate", true);
        }

        if (!SPUtils.getInstance().contains("setting_yanzhengmode")) {
            SPUtils.getInstance().put("setting_yanzhengmode", 1);
        }

        if (!SPUtils.getInstance().contains("setting_suopingmode")) {
            SPUtils.getInstance().put("setting_suopingmode", 2);
        }

        if (!SPUtils.getInstance().contains("setting_clickmkey")) {
            SPUtils.getInstance().put("setting_clickmkey", 0);
        }

        if (!SPUtils.getInstance().contains("switch_hidechat")) {
            SPUtils.getInstance().put("switch_hidechat", true);
        }

        if (!SPUtils.getInstance().contains("enterkey_sendmessage")) {
            SPUtils.getInstance().put("enterkey_sendmessage", true);
        }

        if (!SPUtils.getInstance().contains("switch_apphomelock")) {
            SPUtils.getInstance().put("switch_apphomelock", true);
        }

        if (!SPUtils.getInstance().contains("switch_homelock")) {
            SPUtils.getInstance().put("switch_homelock", true);
        }

        long time = (int) (System.currentTimeMillis() / 1000);
        String sql = "select * from em";
        Cursor cursor = App.db.rawQuery(sql, null);
        if (cursor.getCount() == 0) {
            App.db.execSQL("insert into em(Em,Updatetime) values ('[坏笑]'," + time + ")");
            App.db.execSQL("insert into em(Em,Updatetime) values ('[卖萌]'," + time + ")");
            App.db.execSQL("insert into em(Em,Updatetime) values ('[贴贴]'," + time + ")");
            App.db.execSQL("insert into em(Em,Updatetime) values ('[吃瓜]'," + time + ")");
            App.db.execSQL("insert into em(Em,Updatetime) values ('[捂脸]'," + time + ")");
            App.db.execSQL("insert into em(Em,Updatetime) values ('[可怜]'," + time + ")");
        }
        cursor.close();
    }

    private void registerHomeListener() {
        mHomeWatcher = new HomeWatcher(this);
        mHomeWatcher.setOnHomePressedListener(new HomeWatcher.OnHomePressedListener() {
            @Override
            public void onHomePressed() {
                boolean _switch_homelock = SPUtils.getInstance().get("switch_homelock", false);
                if (_switch_homelock) {
                    App.FriendPass = false;
                }
                if (activity_friend.that != null) activity_friend.that.finish();
                if (activity_chat.that != null) {
                    if (activity_friend.that != null) {
                        activity_friend.that.finish();
                    }
                    activity_chat.that.finish();
                }
            }

            @Override
            public void onHomeLongPressed() {
                boolean _switch_homelock = SPUtils.getInstance().get("switch_homelock", false);
                if (_switch_homelock) {
                    App.FriendPass = false;
                }
                backToMainActivity();
            }
        });
        mHomeWatcher.startWatch();
    }

    private void initScreenListener() {
        screenListener = new ScreenListener(this);
        screenListener.begin(new ScreenListener.ScreenStateListener() {
            @Override
            public void onScreenOn() {
                Log.e("TBA", "屏幕打开了");
            }

            @Override
            public void onScreenOff() {
                App.FriendPass = false;
                int SUOPINGID = SPUtils.getInstance().get("setting_suopingmode", 0);
                Log.e("TBA", "屏幕关闭了，执行锁屏策略：" + SUOPINGID);
                if (SUOPINGID == 1) {
                    backToMainActivity();
                } else if (SUOPINGID == 2) {
                    GoHome();
                } else if (SUOPINGID == 3) {
                    App.db.execSQL("DELETE FROM chat");
                    backToMainActivity();
                } else if (SUOPINGID == 4) {
                    App.db.execSQL("DELETE FROM chat");
                    GoHome();
                }
            }

            @Override
            public void onUserPresent() {}
        });
    }

    /**
     * 无论在第几级页面，强行拉起 activity_main 并清空之上的所有子页面
     */
    private void backToMainActivity() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());

            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            } else {
                Intent backupIntent = new Intent(this, activity_main.class);
                backupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(backupIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (activity_main.that != null) {
                ActivityUtils.finishToActivity(activity_main.that, false);
            }
        }
    }

    private void GoHome() {
        Intent it_home = new Intent(Intent.ACTION_MAIN, null);
        it_home.putExtra("GOHOME", "GOHOME");
        it_home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        it_home.addCategory(Intent.CATEGORY_HOME);
        startActivity(it_home);
    }
}