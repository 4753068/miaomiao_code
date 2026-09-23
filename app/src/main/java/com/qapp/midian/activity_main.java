package com.qapp.midian;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.amap.api.location.AMapLocation;

import Data.ChatUtils;
import Data.CheckUserUtil;
import Data.FriendUtils;
import Data.MemberBillingBridge;
import Data.MemberUtils;
import Utils.FlipDetector;
import Utils.FloatButton;
import Utils.LocationUtils;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import Utils.WebBridge;
import Utils.FBWebView;
import socket.AutoReconnectWebSocket;

public class activity_main extends AppCompatActivity {
    public static FBWebView webView;
    public static activity_main that = null;


    // 【核心改动 1】将弹窗和复选框提升为全局变量，以便在 onResume 中动态刷新它们的状态
    private AlertDialog permissionDialog;
    private CheckBox cbNotification;
    private CheckBox cbBattery;
    private int loadedSiteItem = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        that = this;
        StatusBarUtil.setStatusBarMode(this, false, R.color.black);

        CheckUserUtil.Biombet();//检查指纹模块


        if (App.UID > 0 && MemberBillingBridge.get() != null) {
            MemberBillingBridge.get().handleUserAuth("startup", App.UID);
        }

        FloatButton.floatButton(this);

        LocationUtils.getInstance().initPrivacy(this);
        LocationUtils.getInstance().startSingleLocation(this, new LocationUtils.OnLocationResultListener() {
            @Override
            public void onSuccess(AMapLocation location) {
                double latitude = location.getLatitude();     // 纬度
                double longitude = location.getLongitude();   // 经度
                String address = location.getAddress();       // 地址描述
                String city = location.getCity();             // 城市
                Log.e("GAODE", city);
            }

            @Override
            public void onFailure(int errorCode, String errorInfo) {
                // 定位失败处理，如根据 errorCode 判断是否是 GPS 没开或网络错误
            }
        });

        webView = findViewById(R.id.myWebView);
        handlePushUrl(getIntent()); // 【新增】如果 App 从后台完全冷启动唤醒，检查是否有推送过来的 URL

        ProgressBar progressBar = findViewById(R.id.webViewProgressBar);

        // 绑定进度条
        webView.attachProgressBar(progressBar);
        // 1. 必须启用 JavaScript 支持
        webView.getSettings().setJavaScriptEnabled(true);

        // 2. 注入桥接对象，命名为 "AndroidBridge" (这个名字在前端 HTML 中会用到)
        webView.addJavascriptInterface(new WebBridge(), "AndroidBridge");

        Intent intent = new Intent(this, service.foreground.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // 处理手机物理返回键
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        // 检查 WebView 是否可以正常调用 JS
        if (webView != null) {
            // 调用 JS 暴露出来的全局方法，并获取返回值（Vue 方法返回的 boolean）
            webView.evaluateJavascript("window.onAndroidBackKey ? window.onAndroidBackKey() : false;", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    // value 的值会是 "true" 或 "false" (注意是字符串包裹)
                    if ("true".equals(value)) {
                        // H5 内部成功消耗了这次点击（关闭了弹窗），Android 壳子什么都不用做
                        Log.d("WebView", "返回键已被网页弹窗拦截并关闭");
                    } else {
                        // H5 没有弹窗，走 Android 默认的返回逻辑（比如 WebView 后退或者关闭当前 Activity）
                        triggerDefaultBackLogic();
                    }
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    private void triggerDefaultBackLogic() {
        if (webView.canGoBack()) {
            webView.goBack(); // 网页历史记录后退
        } else {
            super.onBackPressed(); // 退出当前界面
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.activity = this;
        that = this;
        FloatButton.updateAlpha();
        App.UID = SPUtils.getInstance().get("uid", 0);
        Log.e("UID", App.UID + "");
        int currentSiteItem = SPUtils.getInstance().get("setting_appitem", 0);
        // 只有当尚未加载过（-1）或者站点发生切换时，才去刷新网页
        if (loadedSiteItem != currentSiteItem) {
            FBWebView.toAppPage("site_item");
            loadedSiteItem = currentSiteItem; // 更新记录
        }

        if (SPUtils.getInstance().contains("uid")) App.UID = (int) SPUtils.getInstance().get("uid", 0);
        boolean switch_apphomelock = SPUtils.getInstance().get("switch_apphomelock", false);
        if (switch_apphomelock) {
            App.FriendPass = false;
        }

        if (App.UID > 0) {
            AutoReconnectWebSocket.startSocket();
            if (!SPUtils.getInstance().contains("password")) {
                Intent intent = new Intent(this, activity_password.class);
                intent.putExtra("type", 1);
                startActivity(intent);
            }

            String sql = "select * from friend where UID=" + App.UID;
            Cursor cursor = App.db.rawQuery(sql, null);
            if (cursor.getCount() == 0) {
                FriendUtils.GET_FRIEND();
            }
            cursor.close();
            ChatUtils.GET_NUMBER();
        } else {
            Intent it = new Intent();
            it.setClass(App.AppContext, activity_login.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.AppContext.startActivity(it);
            that.finish();
        }

        checkAndShowPermissionDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        that = null;
        boolean _switch_homelock = SPUtils.getInstance().get("switch_homelock", false);
        if (_switch_homelock) {
            App.FriendPass = false;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (permissionDialog != null && permissionDialog.isShowing()) {
            permissionDialog.dismiss();
            permissionDialog = null;
        }
    }

    /**
     * 【核心改动 3】智能检测权限并动态刷新弹窗
     */
    private void checkAndShowPermissionDialog() {
        // 0. 检查用户是否已经勾选过“不再显示”
        boolean isNeverShowAgain = SPUtils.getInstance().get("hide_permission_dialog", false);

        // 如果用户选择过不再显示，直接 return，不执行后续的弹窗逻辑
        if (isNeverShowAgain) {
            return;
        }

        // 1. 实时获取最新的权限状态
        boolean isNotificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        boolean isIgnoringBattery;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(getPackageName());
            } else {
                isIgnoringBattery = true;
            }
        } else {
            isIgnoringBattery = true;
        }

        // 2. 如果两个权限都已经开启：
        if (isNotificationEnabled && isIgnoringBattery) {
            // 如果弹窗开着，直接帮用户关掉它，体验极其丝滑
            if (permissionDialog != null && permissionDialog.isShowing()) {
                permissionDialog.dismiss();
            }
            return;
        }

        // 3. 如果弹窗【已经】显示在屏幕上（即用户刚从设置页面返回）：
        if (permissionDialog != null && permissionDialog.isShowing()) {
            // 直接更新 UI 状态，给成功开启的权限打上绿色的勾并置灰
            if (cbNotification != null) {
                cbNotification.setChecked(isNotificationEnabled);
                cbNotification.setEnabled(!isNotificationEnabled);
            }
            if (cbBattery != null) {
                cbBattery.setChecked(isIgnoringBattery);
                cbBattery.setEnabled(!isIgnoringBattery);
            }
            return; // 更新完毕，直接结束，不重复创建弹窗
        }

        // 4. 如果弹窗【没有】显示，则创建并显示它
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_request, null);

        cbNotification = dialogView.findViewById(R.id.cbNotification);
        cbBattery = dialogView.findViewById(R.id.cbBattery);
        CheckBox cbNeverShowAgain = dialogView.findViewById(R.id.cbNeverShowAgain); // 新增的不提示CheckBox
        Button btnPermissionConfirm = dialogView.findViewById(R.id.btnPermissionConfirm);

        // 初始化 CheckBox 状态
        cbNotification.setChecked(isNotificationEnabled);
        cbNotification.setEnabled(!isNotificationEnabled);

        cbBattery.setChecked(isIgnoringBattery);
        cbBattery.setEnabled(!isIgnoringBattery);

        // 初始化“不再提示”的勾选状态（默认为 false）
        cbNeverShowAgain.setChecked(false);

        permissionDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true) // 允许点击外部取消
                .create();

        // 将系统 AlertDialog 默认的白色直角背景设为透明，展露圆角
        if (permissionDialog.getWindow() != null) {
            permissionDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 监听“下次不再显示”状态变化，实时保存（防止用户点击空白处取消弹窗导致没保存）
        cbNeverShowAgain.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SPUtils.getInstance().put("hide_permission_dialog", true);
        });

        // 监听“知道了”按钮
        btnPermissionConfirm.setOnClickListener(v -> permissionDialog.dismiss());

        // 监听通知权限点击
        cbNotification.setOnClickListener(v -> {
            if (!isNotificationEnabled) {
                cbNotification.setChecked(false); // 先假装没点中，等用户从设置回来 onResume 再真实打勾
                try {
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("app_package", getPackageName());
                        intent.putExtra("app_uid", getApplicationInfo().uid);
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 监听电池白名单点击
        cbBattery.setOnClickListener(v -> {
            if (!isIgnoringBattery) {
                cbBattery.setChecked(false); // 先假装没点中，等用户从设置回来 onResume 再真实打勾
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        @SuppressLint("BatteryLife")
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        permissionDialog.show();
    }

    private void handlePushUrl(Intent intent) {
        if (intent != null && intent.hasExtra("target_url")) {
            String targetUrl = intent.getStringExtra("target_url");
            if (targetUrl != null && !targetUrl.isEmpty()) {
                if (webView != null) {
                    webView.post(() -> webView.loadUrl(targetUrl));
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePushUrl(intent);
    }

    // 执行退回桌面逻辑

}