package com.qapp.midian;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import Utils.FBMessage;
import Utils.MultiDownloadHelper;
import Utils.SPUtils;
import Utils.StatusBarUtil;

public class activity_update extends AppCompatActivity {
    public static activity_update that;
    public static TextView btnUpdate = null;
    public static TextView txtVer = null, txtContent = null;

    private static String DownloadFile = null, itemFile = null;
    private static boolean isDownloading = false;

    // 最新版本参数缓存
    private boolean hasAppUpdate = false;
    private int newVersionCode = 0;
    private String newVersionName = "";
    private String updateUrl = "";
    private String updateContent = "";

    private static Handler downloadHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 101:
                    int inputNum = msg.getData().getInt("pgValue");
                    if (btnUpdate != null) {
                        btnUpdate.setText("正在下载 " + inputNum + "%");
                    }
                    if (inputNum >= 100) {
                        new Thread(() -> {
                            isDownloading = false;
                            Uri uri = Uri.fromFile(new File(itemFile));
                            App.AppContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
                            Message msgSetView = new Message();
                            msgSetView.what = 102;
                            downloadHandler.sendMessage(msgSetView);
                        }).start();
                    }
                    break;
                case 102:
                    if (btnUpdate != null) {
                        btnUpdate.setText("下载完成，点击安装");
                    }
                    try {
                        Path source = Paths.get(itemFile + ".apk");
                        Path target = Paths.get(DownloadFile);
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

                        File f = new File(DownloadFile);
                        if (f.exists()) {
                            AppUtils.installApp(f);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        that = this;
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        txtVer = findViewById(R.id.txtVer);
        txtContent = findViewById(R.id.txtContent);
        btnUpdate = findViewById(R.id.btnUpdate);

        // 1. 从 app_config 获取由 handleUserAuth(verify_token) 统一落盘的版本信息
        initVersionData();

        // 2. 绑定下载或安装按钮事件
        btnUpdate.setOnClickListener(v -> handleUpdateClick());

        // 3. 处理桌面主图标显示/隐藏切换
        setupIconControl();
    }

    /**
     * 读取闭源 AAR 验签时落盘的版本字段并渲染界面
     */
    private void initVersionData() {
        String currentVersionName = "v1.0";
        int currentVersionCode = 0;
        try {
            currentVersionName = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedPreferences sp = getSharedPreferences("app_config", Context.MODE_PRIVATE);
        hasAppUpdate = sp.getBoolean("has_app_update", false);
        newVersionName = sp.getString("new_version_name", "");
        newVersionCode = sp.getInt("new_version_code", 0);
        updateUrl = sp.getString("update_url", "");
        updateContent = sp.getString("update_content", "修复已知问题，优化使用体验");

        if (hasAppUpdate && newVersionCode > currentVersionCode) {
            txtVer.setText("当前版本：" + currentVersionName + "  ->  发现新版本：v" + newVersionName);
            txtContent.setText(updateContent);

            DownloadFile = App.Folder + "/midian_" + newVersionCode + ".apk";
            itemFile = App.Folder + "/midian.download";

            // 判断新版本 APK 是否已存在于本地
            File apkFile = new File(DownloadFile);
            if (apkFile.exists() && apkFile.length() > 0) {
                btnUpdate.setText("立即安装新版本");
            } else {
                btnUpdate.setText("立即下载升级");
            }
            btnUpdate.setEnabled(true);
        } else {
            txtVer.setText("当前版本：" + currentVersionName);
            txtContent.setText("您当前使用的已是最新版本，无需更新。");
            btnUpdate.setText("已是最新版本");
            btnUpdate.setEnabled(false);
            btnUpdate.setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"));
        }
    }

    private void handleUpdateClick() {
        if (!hasAppUpdate || updateUrl.isEmpty()) {
            FBMessage.Show(that, "当前已是最新版本");
            return;
        }

        if (isDownloading) {
            FBMessage.Show(that, "正在下载，请稍候");
            return;
        }

        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + that.getPackageName()));
            that.startActivityForResult(intent, 1);
            return;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(that, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(that, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }

        // 本地已有完整安装包直接安装，否则启动多线程断点下载
        File f = new File(DownloadFile);
        if (f.exists() && f.length() > 0) {
            AppUtils.installApp(f);
        } else {
            File fItem = new File(itemFile);
            FileUtils.delete(fItem);
            Download(updateUrl, "midian.download");
            isDownloading = true;
        }
    }

    private void setupIconControl() {
        ComponentName componentName = new ComponentName(that, "com.qapp.midian.MainActivity");
        PackageManager packageManager = that.getPackageManager();

        TextView btnShowIcon = findViewById(R.id.btn_showicon);
        int itemIndex = SPUtils.getInstance().get("setting_appitem", 0);
        if (itemIndex > 0 && btnShowIcon != null) {
            btnShowIcon.setVisibility(View.VISIBLE);
            btnShowIcon.setOnClickListener(v -> {
                int state = packageManager.getComponentEnabledSetting(componentName);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    FBMessage.Show(that, "主图标已显示");
                } else {
                    packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                    FBMessage.Show(that, "主图标已隐藏");
                }
            });
        }
    }

    public void Download(String url, String filename) {
        MultiDownloadHelper multiDownloadHelper = new MultiDownloadHelper(1, url, filename);
        multiDownloadHelper.download(new MultiDownloadHelper.DownloadListener() {
            @Override
            public void onProgress(long currentSize, long totalSize) {
                isDownloading = true;
                float progress = ((float) currentSize / (float) totalSize) * 100;
                int pgValue = (int) progress;

                Message msg = new Message();
                msg.what = 101;
                Bundle bundle = new Bundle();
                bundle.putInt("pgValue", pgValue);
                bundle.putString("pgTitle", filename);
                msg.setData(bundle);
                downloadHandler.sendMessage(msg);
            }

            @Override
            public void onSuccess(String filePath) {
                isDownloading = false;
                // TODO: 下载完成后的逻辑（例如安装 APK 或更新 UI）
            }

            @Override
            public void onFailure(String errorMsg) {
                isDownloading = false;
                // TODO: 下载失败逻辑（例如弹窗提示失败原因）
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null;
    }
}