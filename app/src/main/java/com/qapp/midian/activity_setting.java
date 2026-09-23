package com.qapp.midian;

import static com.blankj.utilcode.util.ViewUtils.runOnUiThread;
import static Data.MemberUtils.ShowVipDialog;
import static Dialog.SettingDialog.showChatLockDialog;
import static Dialog.SettingDialog.showMkeyDialog;
import static Dialog.SettingDialog.showSecurityModeDialog;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.blankj.utilcode.util.FileUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Data.MemberUtils;
import Dialog.SettingDialog;
import Utils.DateTimeUtils;
import Utils.FBMessage;
import Utils.HomePageUtils;
import Utils.ImageUtils;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import socket.AutoReconnectWebSocket;

public class activity_setting extends AppCompatActivity implements View.OnClickListener {
    public static activity_setting that;

    // 界面控件
    private TextView tvNickname, tvUserid,tvVip, tvProfileVipBadge;
    private TextView tvCurrentBurn, tvCurrentStrategy, tvCurrentRemind, tvCurrentLockM, tvCurrentAuth, tvCurrentClickM, tvCurrentLockAction;
    private GridLayout appIconGrid;
    private ImageView user_thumb;

    // ✨ 新增：在线状态开关及对应文本
    private SwitchCompat switchOnlineStatus;
    private TextView tvOnlineStatusText;

    // 核心业务数据状态锁
    private long vipTimestamp = 0;
    private int selectedAppIconIndex = 0;

    // 模拟数据模型：自定义图标
    private List<AppIconBean> appIconList = new ArrayList<>();

    private ProgressBar PointProgressBar;
    private TextView strpoint;
    public static TextView tv_version;

    private SeekBar sbAlphaProgress;
    private TextView tvAlphaValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        that = this;
        StatusBarUtil.setStatusBarMode(this, true, R.color.white);
        initView();
        initData();
        initConfigFromLocal(); // 恢复还原核心链路
        initAlphaSetting();

    }

    @Override
    protected void onResume() {
        super.onResume();
        String image = SPUtils.getInstance().get("image", "");
        Object loadModel = image;

// 如果是 Base64 格式的头像，直接解码成字节数组喂给 Glide，避免文件名超长报错
        if (image.startsWith("data:image")) {
            try {
                String base64Data = image.split(",")[1];
                loadModel = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            } catch (Exception ignored) {}
        }

        Glide.with(this)
                .load(loadModel)
                .centerCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(new RoundedCorners(8))
                .into(user_thumb);

        checkAppVersionUI();
    }

    private void initView() {
        tvNickname = findViewById(R.id.tv_nickname);
        tvUserid = findViewById(R.id.tv_userid);
        tvVip = findViewById(R.id.tv_vip);
        tvProfileVipBadge = findViewById(R.id.tv_profile_vip_badge);
        tvCurrentBurn = findViewById(R.id.tv_current_burn);
        tvCurrentStrategy = findViewById(R.id.tv_current_strategy);
        tvCurrentRemind = findViewById(R.id.tv_current_remind);
        tvCurrentLockM = findViewById(R.id.tv_current_lock_m);
        tvCurrentAuth = findViewById(R.id.tv_current_auth);
        tvCurrentClickM = findViewById(R.id.tv_current_click_m);
        tvCurrentLockAction = findViewById(R.id.tv_current_lock_action);
        tv_version = findViewById(R.id.tv_version);
        appIconGrid = findViewById(R.id.app_icon_grid);
        user_thumb = findViewById(R.id.user_thumb);

        // ✨ 绑定在线状态控件
        switchOnlineStatus = findViewById(R.id.switch_online_status);
        tvOnlineStatusText = findViewById(R.id.tv_online_status_text);

        PointProgressBar = findViewById(R.id.PointProgressBar);
        strpoint = findViewById(R.id.strpoint);

        // 批量注册标准点击反射监听器
        findViewById(R.id.item_profile).setOnClickListener(this);
        findViewById(R.id.item_bind).setOnClickListener(this);
        findViewById(R.id.item_server).setOnClickListener(this);
        findViewById(R.id.item_burn).setOnClickListener(this);
        findViewById(R.id.item_strategy).setOnClickListener(this);
        findViewById(R.id.item_security_verify).setOnClickListener(this);
        findViewById(R.id.item_virtual_verify).setOnClickListener(this);
        findViewById(R.id.item_remind).setOnClickListener(this);
        findViewById(R.id.item_lock_mechanism).setOnClickListener(this);
        findViewById(R.id.item_auth_mode).setOnClickListener(this);
        findViewById(R.id.item_click_m).setOnClickListener(this);
        findViewById(R.id.item_lock_action).setOnClickListener(this);
        findViewById(R.id.item_update).setOnClickListener(this);
        findViewById(R.id.item_clean_cache).setOnClickListener(this);
        findViewById(R.id.item_exit).setOnClickListener(this);
        findViewById(R.id.item_backup_setting).setOnClickListener(this);
        findViewById(R.id.item_password).setOnClickListener(this);
        findViewById(R.id.item_backup_chat).setOnClickListener(this);

        tvNickname.setText(SPUtils.getInstance().get("nickname", "未登录"));
        String userid=SPUtils.getInstance().get("userid","");
        tvUserid.setText("账号："+userid);
        long _viptiime = SPUtils.getInstance().get("vip", 0L);
        if (_viptiime > System.currentTimeMillis() / 1000) {
            if (tvProfileVipBadge != null) {
                tvProfileVipBadge.setVisibility(View.VISIBLE);
            }
            tvVip.setText("生效中:" + DateTimeUtils.stampToDate(_viptiime));
        } else {
            if (tvProfileVipBadge != null) {
                tvProfileVipBadge.setVisibility(View.GONE);
            }
            tvVip.setText("已失效,请到分贝应用公众号订阅");
        }

        String image = SPUtils.getInstance().get("image", "");
        String[] parts = image.split("/");

        String newImageFilename = App.Folder + "/Image/" + parts[parts.length - 1];
        Glide.with(App.AppContext).load(image).centerCrop().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).transform(new RoundedCorners(8)).into(new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                ImageUtils.saveBitmap(bitmap, newImageFilename);
                user_thumb.setImageBitmap(bitmap);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
            }
        });

        // ✨ 初始化在线/隐身状态
        boolean isCurrentlyInvisible = SPUtils.getInstance().get("is_invisible", false);
        switchOnlineStatus.setChecked(!isCurrentlyInvisible);

        // ✨ 初始化文本及颜色
        tvOnlineStatusText.setText(!isCurrentlyInvisible ? "在线" : "免打扰");
        tvOnlineStatusText.setTextColor(!isCurrentlyInvisible ? android.graphics.Color.parseColor("#4CAF50") : android.graphics.Color.parseColor("#999999"));

        // ✨ 监听开关变化，联动 UI 与 WebSocket 指令
        switchOnlineStatus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean turnInvisible = !isChecked;

                // 实时联动文本及颜色
                tvOnlineStatusText.setText(isChecked ? "在线" : "免打扰");
                tvOnlineStatusText.setTextColor(isChecked ? android.graphics.Color.parseColor("#4CAF50") : android.graphics.Color.parseColor("#999999"));

                // 保存到本地
                SPUtils.getInstance().put("is_invisible", turnInvisible);

                // 通过长连接通知服务端切换状态
                if (AutoReconnectWebSocket.socket != null && AutoReconnectWebSocket.socketConnected.get()) {
                    try {
                        JSONObject packet = new JSONObject();
                        packet.put("type", "SetInvisibleStatus");

                        JSONObject data = new JSONObject();
                        data.put("invisible", turnInvisible);

                        packet.put("data", data);
                        AutoReconnectWebSocket.socket.sendRawMessage(packet.toString());

                    } catch (Exception e) {
                        e.printStackTrace();
                        FBMessage.Show(activity_setting.this, "状态切换包构造失败");
                    }
                } else {
                    FBMessage.Show(activity_setting.this, "网络未连接，无法同步状态到服务器");
                    // 如需严格保证一致性，断网时可回退开关状态：buttonView.setChecked(!isChecked);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null;
    }

    private void initAlphaSetting() {
        sbAlphaProgress = findViewById(R.id.sb_alpha_progress);
        tvAlphaValue = findViewById(R.id.tv_alpha_value);

        int savedAlpha = SPUtils.getInstance().get("Floatalpha", 5);

        if (savedAlpha < 1) savedAlpha = 1;
        if (savedAlpha > 10) savedAlpha = 10;

        tvAlphaValue.setText(String.valueOf(savedAlpha));
        sbAlphaProgress.setProgress(savedAlpha - 1);

        sbAlphaProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int currentAlpha = progress + 1;
                tvAlphaValue.setText(String.valueOf(currentAlpha));
                SPUtils.getInstance().put("Floatalpha", currentAlpha);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void initData() {
        appIconList.clear();
        appIconList.add(new AppIconBean("喵喵", R.drawable.logo));
        appIconList.add(new AppIconBean("日历", R.mipmap.app_rili));
        appIconList.add(new AppIconBean("FlappyBird", R.mipmap.flappybird));
        appIconList.add(new AppIconBean("西瓜视频", R.mipmap.app_xigua));
        appIconList.add(new AppIconBean("抖音", R.mipmap.app_douyin));
        appIconList.add(new AppIconBean("淘宝", R.mipmap.app_taobao));
        appIconList.add(new AppIconBean("WPS", R.mipmap.app_wps));

        renderIconGrid();

        int point = SPUtils.getInstance().get("Point", 0);
        int usepoint = SPUtils.getInstance().get("usePoint", 0);

        if (point <= 0) {
            PointProgressBar.setProgress(0);
            return;
        }

        int percent = (int) ((usepoint * 100f) / point);
        PointProgressBar.setMax(100);
        PointProgressBar.setProgress(percent);
        strpoint.setText(usepoint + "/" + point);


    }

    private void initConfigFromLocal() {
        try {
            SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
            String jsonStr = sp.getString("global_config_json", "");

            if (jsonStr.isEmpty()) return;
            JSONObject config = new JSONObject(jsonStr);

            if (config.has("vip")) {
                vipTimestamp = config.optLong("vip", 0);
                if (vipTimestamp > System.currentTimeMillis() / 1000) {
                    if (tvProfileVipBadge != null) tvProfileVipBadge.setVisibility(View.VISIBLE);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                    String timeStr = sdf.format(new Date(vipTimestamp * 1000));
                    tvUserid.setText("账号 ID: " + config.optString("userid") + " (至 " + timeStr + ")");
                } else {
                    if (tvProfileVipBadge != null) tvProfileVipBadge.setVisibility(View.GONE);
                    tvUserid.setText("账号 ID: " + config.optString("userid") + " (VIP权限未生效)");
                }
            }
            tvNickname.setText(config.optString("nickname", "可爱喵喵"));

            int modeVal = config.optInt("setting_yanzhengmode", 1);
            if (modeVal == 0) tvCurrentAuth.setText("当前选择：指纹验证");
            else if (modeVal == 1) tvCurrentAuth.setText("当前选择：密码验证");
            else if (modeVal == 2) tvCurrentAuth.setText("当前选择：无需验证");

            selectedAppIconIndex = config.optInt("setting_appitem", 0);
            renderIconGrid();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.item_profile) {
            Intent intent = new Intent(this, activity_member_info.class);
            startActivity(intent);
        } else if (id == R.id.item_bind) {
            if (Data.MemberBillingBridge.get() == null) {
                FBMessage.Show(this, "服务未就绪");
                return;
            }

            // 委托给闭源 AAR 安全获取带签名的微信绑定码
            Data.MemberBillingBridge.get().getWeChatBindCode(this, App.UID, new Data.IMemberBilling.BindCodeCallback() {
                @Override
                public void onSuccess(String bindCode) {
                    SettingDialog.showWeChatBindDialog(activity_setting.this, bindCode);
                }

                @Override
                public void onFailure(String errorMessage) {
                    FBMessage.Show(activity_setting.this, errorMessage);
                }
            });
        } else if (id == R.id.item_server) {
            toServer();
        } else if (id == R.id.item_burn) {
            SettingDialog.ShowTimeDialog(this);
        } else if (id == R.id.item_strategy) {
            SettingDialog.Zidongfenhui(this);
        } else if (id == R.id.item_security_verify) {
            Intent intent = new Intent(this, activity_password.class);
            intent.putExtra("type", 1);
            startActivity(intent);
        } else if (id == R.id.item_virtual_verify) {
            Intent intent = new Intent(this, activity_password.class);
            intent.putExtra("type", 2);
            startActivity(intent);
        } else if (id == R.id.item_password) {
            Intent intent = new Intent(this, activity_password.class);
            intent.putExtra("type", 4);
            startActivity(intent);
        } else if (id == R.id.item_remind) {
            SettingDialog.showSoundDialog(this);
        } else if (id == R.id.item_lock_mechanism) {
            showChatLockDialog(this);
        } else if (id == R.id.item_auth_mode) {
            showSecurityModeDialog(this);
        } else if (id == R.id.item_click_m) {
            showMkeyDialog(this);
        } else if (id == R.id.item_lock_action) {
            SettingDialog.showLockModeDialog(this);
        } else if (id == R.id.item_update) {
            toUpdate();
        } else if (id == R.id.item_clean_cache) {
            Cleancache();
        } else if (id == R.id.item_backup_chat) {

            Intent it = new Intent();
            it.setClass(that, activity_daochu.class);
            that.startActivity(it);
        } else if (id == R.id.item_backup_setting) {
            if (!MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                return;
            }
            SettingDialog.showBackupRestoreDialog(this);
        } else if (id == R.id.item_exit) {
            openExitConfirmationDialog();
        }
    }

    private void openExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("退出后您将暂时无法接收到最新消息，是否确定退出？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MemberUtils.Exit();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renderIconGrid() {
        appIconGrid.removeAllViews();
        selectedAppIconIndex = SPUtils.getInstance().get("setting_appitem", 0);
        for (int i = 0; i < appIconList.size(); i++) {
            final int index = i;
            AppIconBean bean = appIconList.get(i);

            View cardView = LayoutInflater.from(this).inflate(R.layout.item_app_icon_card, appIconGrid, false);
            ImageView ivImgBox = cardView.findViewById(R.id.app_icon_img_box);
            TextView tvName = cardView.findViewById(R.id.app_icon_name);
            View badgeCheck = cardView.findViewById(R.id.app_badge_check);

            tvName.setText(bean.name);

            if (index == selectedAppIconIndex) {
                cardView.setBackgroundResource(R.drawable.bg_app_card_selected);
                Glide.with(this)
                        .load(bean.iconResId)
                        .centerCrop()
                        .transform(new RoundedCorners(dp2px(8)))
                        .into(ivImgBox);
                badgeCheck.setVisibility(View.VISIBLE);
            } else {
                cardView.setBackgroundResource(R.drawable.bg_app_card_normal);
                Glide.with(this)
                        .load(bean.iconResId)
                        .centerCrop()
                        .transform(new RoundedCorners(dp2px(8)))
                        .into(ivImgBox);
                badgeCheck.setVisibility(View.GONE);
            }

            cardView.setOnClickListener(v -> {
                if (!MemberUtils.GetVipStatic()) {
                    ShowVipDialog(activity_setting.that);
                    return;
                }
                selectedAppIconIndex = index;
                setHomePage(index);
                renderIconGrid();
            });

            GridLayout.Spec rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            GridLayout.Spec colSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
            params.width = 0;
            cardView.setLayoutParams(params);

            appIconGrid.addView(cardView);
        }
    }

    /**
     * 检查本地记录的版本状态并渲染 tv_version
     */
    private void checkAppVersionUI() {
        if (tv_version == null) return;

        // 获取当前安装包的版本名
        String currentVersionName = "v1.0";
        try {
            currentVersionName = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 读取 AAR 同步落盘的版本升级标识
        android.content.SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        boolean hasUpdate = sp.getBoolean("has_app_update", false);
        String newVersionName = sp.getString("new_version_name", "");

        if (hasUpdate && !newVersionName.isEmpty()) {
            // 有新版本：高亮红字或提示有新版本
            tv_version.setText("发现新版本 v" + newVersionName + " (可更新)");
            tv_version.setTextColor(android.graphics.Color.parseColor("#FF4D4F")); // 鲜亮醒目红色
        } else {
            // 已是最新版本：展示当前版本号
            tv_version.setText(currentVersionName + " (已是最新)");
            tv_version.setTextColor(android.graphics.Color.parseColor("#999999")); // 柔和灰色
        }
    }

    private void toServer() {
        if (!MemberUtils.GetVipStatic()) {
            ShowVipDialog(activity_setting.that);
            return;
        }
        Intent it = new Intent();
        it.setClass(App.AppContext, activity_service.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.AppContext.startActivity(it);
    }

    private void toUpdate() {
        Intent it = new Intent();
        it.setClass(App.AppContext, activity_update.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.AppContext.startActivity(it);
    }

    private void Cleancache() {
        FileUtils.deleteAllInDir(App.Folder + "/Cache");
        FBMessage.Show(App.AppContext, "临时文件已删除");
    }

    private void setHomePage(int which) {
        SPUtils.getInstance().put("setting_appitem", which);

        // 默认名字就是主图标
        String name = "com.qapp.midian.activity_main";

        if (which == 1) name = "com.qapp.midian.Activity_Rili";
        else if (which == 2) name = "com.qapp.midian.Activity_FlappyBird";
        else if (which == 3) name = "com.qapp.midian.Activity_Xigua";
        else if (which == 4) name = "com.qapp.midian.Activity_Douyin";
        else if (which == 5) name = "com.qapp.midian.Activity_Taobao";
        else if (which == 6) name = "com.qapp.midian.Activity_WPS";

        // 不管选哪个，统一丢给优化后的方法处理（它会自动启用目标并关闭其他所有的别名图标）
        HomePageUtils.changeLauncher(name);
    }

    private int dp2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class AppIconBean {
        String name;
        int iconResId;

        AppIconBean(String name, int iconResId) {
            this.name = name;
            this.iconResId = iconResId;
        }
    }

}