package com.qapp.midian;

import static Utils.WindowUtils.setupTransparentStatusBarWithBlackText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import Utils.SPUtils;
import Utils.WebBridge;
import Utils.FBWebView;
import socket.AutoReconnectWebSocket;

public class activity_web extends AppCompatActivity {
    public FBWebView webView;
    public static activity_web that;
    private ValueCallback<Uri[]> mFilePathCallback;
    private static final int REQUEST_SELECT_FILE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        setupTransparentStatusBarWithBlackText(this); // 状态栏透明

        webView = findViewById(R.id.myWebView);
        ProgressBar progressBar = findViewById(R.id.webViewProgressBar);

        // 绑定进度条
        webView.attachProgressBar(progressBar);

        // 注入桥接对象，命名为 "AndroidBridge"
        webView.addJavascriptInterface(new WebBridge(), "AndroidBridge");

        // 设置文件选择监听
        webView.setFileChooserListener((callback, params) -> {
            mFilePathCallback = callback;
            Intent intent = params.createIntent();
            try {
                startActivityForResult(intent, REQUEST_SELECT_FILE);
            } catch (Exception e) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                    mFilePathCallback = null;
                }
            }
        });

        // ==================== 核心修复：注册现代 Android 返回键监听拦截器 ====================
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null) {
                    // 调用 JS 暴露出来的全局方法
                    webView.evaluateJavascript("window.onAndroidBackKey ? window.onAndroidBackKey() : false;", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            Log.d("WebViewBackKey", "H5 返回值 JSON 字符串: " + value);

                            // 当返回值为 "true" 时表明网页内部有弹窗被关闭了，Android 壳子直接拦截，不执行任何后退
                            if ("true".equals(value)) {
                                Log.d("WebView", "返回键已被网页弹窗拦截并成功关闭");
                            } else {
                                // H5 没有弹窗（返回 "false"、"null" 或 "undefined"），走 Android 默认的流转逻辑
                                triggerDefaultBackLogic();
                            }
                        }
                    });
                } else {
                    // webView 为空时安全退出 Activity
                    setEnabled(false); // 暂时禁用此 Callback
                    getOnBackPressedDispatcher().onBackPressed(); // 调用系统默认退出
                }
            }
        });
        // ==================================================================================

        // 加载网页
        App.UID = SPUtils.getInstance().get("uid", 0);

            Intent intent = getIntent();
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String url = bundle.getString("url");
                if (url != null) Log.e("TBA", url);
                webView.loadUrl(url);
            } else {
                FBWebView.toAppPage("site_item");
            }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_FILE) {
            if (mFilePathCallback == null) return;
            // 将选中的图片 URI 回传给 WebView
            mFilePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            mFilePathCallback = null;
        }
    }

    // 默认的后退流转逻辑
    private void triggerDefaultBackLogic() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack(); // 网页历史记录后退
        } else {
            finish(); // 直接销毁当前整个 Activity 界面，防止陷入 onBackPressed() 死循环
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        that = this;
        if (App.UID > 0) {
            AutoReconnectWebSocket.startSocket();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 修复潜在的内存泄漏风险
        if (that == this) {
            that = null;
        }
    }
}