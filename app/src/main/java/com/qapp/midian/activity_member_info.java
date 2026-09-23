package com.qapp.midian;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import Data.MemberUtils;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
public class activity_member_info extends AppCompatActivity {

    // UI 组件声明
    private ImageView ivAvatar;
    private EditText etNickname;
    private RadioGroup rgSex;
    private Switch switchAddFriend;
    private Button btnSubmit;

    private ProgressBar PointProgressBar;
    private TextView strpoint;
    // 数据状态
    private int currentUid = 0;
    private String croppedAvatarBase64 = null;

    private final OkHttpClient client = new OkHttpClient();

    // 1. 注册图片选择器 (现代 Android API)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    startCrop(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_info); // 假设你的布局文件名为 activity_edit_profile.xml
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);
        initViews();
        initDataFromSP();
        setupListeners();
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        etNickname = findViewById(R.id.etNickname);
        rgSex = findViewById(R.id.rgSex);
        switchAddFriend = findViewById(R.id.switchAddFriend);
        btnSubmit = findViewById(R.id.btnSubmit);
        PointProgressBar=findViewById(R.id.PointProgressBar);
        strpoint=findViewById(R.id.strpoint);
    }

    // 2. 从 SharedPreferences 读取初始数据
    private void initDataFromSP() {


        // 尝试获取 uid 或 userid
        currentUid = SPUtils.getInstance().get("uid",0);

        etNickname.setText( SPUtils.getInstance().get("nickname",""));

        int sex =  SPUtils.getInstance().get("sex",0);
        if (sex == 0) {
            rgSex.check(R.id.rbBoy);      // 公子
        } else if (sex == 1) {
            rgSex.check(R.id.rbGirl);     // 姑娘
        } else if (sex == 2) {
            rgSex.check(R.id.rbNeutral);  // 中性
        }

        int addFriend =  SPUtils.getInstance().get("addtofriend", 0);
        switchAddFriend.setChecked(addFriend == 1);

        String imageUrl =  SPUtils.getInstance().get("image", "");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).into(ivAvatar);
        }

        int point =SPUtils.getInstance().get("Point",0);
        int usepoint =SPUtils.getInstance().get("usePoint",0);

        if (point <= 0) {
            PointProgressBar.setProgress(0);
            return;
        }

        int percent = (int)((usepoint * 100f) / point);

        PointProgressBar.setMax(100);
        PointProgressBar.setProgress(percent);

        strpoint.setText(usepoint+"/"+point);

    }

    private void setupListeners() {
        // 点击头像选择图片
        ivAvatar.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        // 提交按钮
        btnSubmit.setOnClickListener(v -> submitProfile());
    }

    // 3. 启动 UCrop 进行 1:1 正方形裁剪
    private void startCrop(Uri sourceUri) {
        String destFileName = "cropped_avatar_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destFileName));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(85);
        options.setHideBottomControls(false); // 确保底部控制栏显示
        options.setFreeStyleCropEnabled(false);

        // ================== 核心配置：把确定按钮变到底部 ==================
        // 1. 隐藏顶部右上角的默认打勾按钮（将其颜色设为透明，使其不可见、不可点）
        options.setToolbarWidgetColor(android.graphics.Color.TRANSPARENT);

        // 2. 启用底部带有文本/图标的确定按钮样式（不同UCrop版本支持略有不同，这是最通用的做法）
        // 在内置的底部控制中，手势（Gestures）或缩放（Scale）标签页自带了底部的确定逻辑。
        // 如果你希望更直接，可以通过主题或者覆写 UCrop 的底部布局（方案二）。
        // =============================================================

        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(300, 300)
                .withOptions(options)
                .start(this);
    }

    // 4. 处理裁剪结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            if (data != null) {
                Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null) {
                    // 显示裁剪后的头像
                    Glide.with(this).load(resultUri).into(ivAvatar);
                    // 转换为 Base64 准备上传
                    convertUriToBase64(resultUri);
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            if (data != null) {
                Throwable cropError = UCrop.getError(data);
                String errorMsg = cropError != null ? cropError.getMessage() : "未知错误";
                Toast.makeText(this, "裁剪失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 5. 将图片 Uri 转为 Base64 字符串
    private void convertUriToBase64(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }

                byte[] bytes = byteBuffer.toByteArray();
                String base64String = Base64.encodeToString(bytes, Base64.NO_WRAP);

                // 拼接 H5 格式的 Base64 前缀，保证后端无需修改直接接收
                croppedAvatarBase64 = "data:image/jpeg;base64," + base64String;

                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 6. 提交数据到服务器 (OkHttp 异步请求)
    private void submitProfile() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) {
            Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUid == 0) {
            Toast.makeText(this, "未能获取到 UID 标识", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Data.MemberBillingBridge.get() == null) {
            Toast.makeText(this, "服务未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgSex.getCheckedRadioButtonId();
        int sex = 0;
        if (checkedId == R.id.rbGirl) {
            sex = 1;
        } else if (checkedId == R.id.rbNeutral) {
            sex = 2;
        }

        final int finalSex = sex;
        final int addFriendParam = switchAddFriend.isChecked() ? 1 : 0;

        btnSubmit.setEnabled(false);
        btnSubmit.setText("提交中...");

        // 委托给闭源 AAR 发起带签名的安全提交
        Data.MemberBillingBridge.get().submitMemberInfo(
                this,
                currentUid,
                nickname,
                finalSex,
                addFriendParam,
                croppedAvatarBase64,
                new Data.IMemberBilling.UpdateProfileCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(activity_member_info.this, "资料修改成功！", Toast.LENGTH_SHORT).show();

                        // 🌟 核心修复点 1：将修改后的资料同步保存到 SPUtils 全局配置中
                        SPUtils.getInstance().put("nickname", nickname);
                        SPUtils.getInstance().put("sex", finalSex);
                        SPUtils.getInstance().put("addtofriend", addFriendParam);
                        if (croppedAvatarBase64 != null) {
                            SPUtils.getInstance().put("image", croppedAvatarBase64);
                        }

                        btnSubmit.postDelayed(() -> finish(), 800);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(activity_member_info.this, errorMessage, Toast.LENGTH_SHORT).show();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("保存修改");
                    }
                }
        );
    }

    // 7. 保存状态回 Android SP
    private void saveDataToSP(String nickname, int sex, int addFriendParam, String avatarBase64) {
        SharedPreferences sp = getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        editor.putString("nickname", nickname);
        editor.putInt("sex", sex);
        editor.putInt("addtofriend", addFriendParam);

        if (avatarBase64 != null) {
            editor.putString("image", avatarBase64);
        }

        editor.apply();
    }
}