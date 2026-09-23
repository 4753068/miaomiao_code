package com.qapp.midian;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.yalantis.ucrop.UCrop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import Data.FriendUtils;
import Data.IMemberBilling;
import Data.MemberBillingBridge;
import Utils.FBMessage;
import Utils.StatusBarUtil;
import socket.AutoReconnectWebSocket;

public class activity_editgroup extends AppCompatActivity {

    private TextView tvTitle, tvSubtitle;
    private ImageView ivAvatar;
    private EditText etGroupName, etGroupNotice, etInviteCode;
    private Switch switchJoinVerify;
    private View llInviteCodeContainer;
    private Button btnSubmit, btnGenerateCode;

    private boolean isEditMode = false;
    private String groupId = "";
    private Bitmap customAvatarBitmap = null;
    private Bitmap generatedAvatarBitmap = null;
    private boolean isCustomAvatarSet = false;

    // 缓存群成员 UID 列表，用于修改成功后发送广播
    private List<Integer> memberUids = new ArrayList<>();

    // 预设柔和优雅的渐变色系（用于首字自动生成群头像）
    private static final int[][] PALETTES = {
            {Color.parseColor("#4A90E2"), Color.parseColor("#0052CC")}, // 科技蓝
            {Color.parseColor("#7B68EE"), Color.parseColor("#5D3FDB")}, // 沉稳紫
            {Color.parseColor("#00C9A7"), Color.parseColor("#008B74")}, // 薄荷绿
            {Color.parseColor("#FF8C00"), Color.parseColor("#E65100")}, // 活力橙
            {Color.parseColor("#FF5E62"), Color.parseColor("#D81B60")}  // 珊瑚粉
    };

    // 注册现代图片选择器
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    startCrop(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_group);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        initViews();
        handleIntentData();
        setupListeners();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        ivAvatar = findViewById(R.id.ivAvatar);
        etGroupName = findViewById(R.id.etGroupName);
        etGroupNotice = findViewById(R.id.etGroupNotice);
        etInviteCode = findViewById(R.id.etInviteCode);
        switchJoinVerify = findViewById(R.id.switchJoinVerify);
        llInviteCodeContainer = findViewById(R.id.llInviteCodeContainer);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnGenerateCode = findViewById(R.id.btnGenerateCode);
    }

    private void handleIntentData() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("group_id")) {
            // 编辑模式
            isEditMode = true;
            groupId = intent.getStringExtra("group_id");

            tvTitle.setText(getString(R.string.edit_group_title_edit));
            tvSubtitle.setText(getString(R.string.edit_group_subtitle_edit));
            btnSubmit.setText(getString(R.string.edit_group_btn_save));

            fetchGroupProfile();
        } else {
            // 新建模式
            isEditMode = false;
            tvTitle.setText(getString(R.string.edit_group_title_create));
            tvSubtitle.setText(getString(R.string.edit_group_subtitle_create));
            btnSubmit.setText(getString(R.string.edit_group_btn_create));

            switchJoinVerify.setChecked(true);
            llInviteCodeContainer.setVisibility(View.VISIBLE);
            etInviteCode.setText(generateRandomCode(6));

            refreshGeneratedAvatar(getString(R.string.edit_group_default_char));
        }
    }

    private void fetchGroupProfile() {
        if (MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未就绪");
            return;
        }

        // 委托给闭源 AAR 发起带签名的安全数据获取
        MemberBillingBridge.get().fetchGroupProfile(this, App.UID, groupId, new IMemberBilling.GroupProfileCallback() {
            @Override
            public void onSuccess(JSONObject jsonObject) {
                try {
                    String name = jsonObject.optString("group_name", "");
                    String notice = jsonObject.optString("notice", "");
                    String groupImage = jsonObject.optString("group_image", "");

                    String inviteCode = jsonObject.optString("invite_code", "");
                    if ("null".equals(inviteCode)) {
                        inviteCode = "";
                    }

                    JSONArray membersArray = jsonObject.optJSONArray("data");
                    if (membersArray != null) {
                        memberUids.clear();
                        for (int i = 0; i < membersArray.length(); i++) {
                            JSONObject obj = membersArray.getJSONObject(i);
                            int uid = obj.optInt("uid", 0);
                            if (uid > 0 && uid != App.UID) {
                                memberUids.add(uid);
                            }
                        }
                    }

                    final String finalInviteCode = inviteCode;

                    if (!name.isEmpty()) etGroupName.setText(name);
                    if (!notice.isEmpty()) etGroupNotice.setText(notice);

                    switchJoinVerify.setOnCheckedChangeListener(null);

                    if (!finalInviteCode.isEmpty()) {
                        switchJoinVerify.setChecked(true);
                        llInviteCodeContainer.setVisibility(View.VISIBLE);
                        etInviteCode.setText(finalInviteCode);
                    } else {
                        switchJoinVerify.setChecked(false);
                        llInviteCodeContainer.setVisibility(View.GONE);
                        etInviteCode.setText("");
                    }

                    switchJoinVerify.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            llInviteCodeContainer.setVisibility(View.VISIBLE);
                            if (etInviteCode.getText().toString().trim().isEmpty()) {
                                etInviteCode.setText(generateRandomCode(6));
                            }
                        } else {
                            llInviteCodeContainer.setVisibility(View.GONE);
                            etInviteCode.setText("");
                        }
                    });

                    if (!groupImage.isEmpty()) {
                        String fullUrl = groupImage.startsWith("http") ? groupImage : App.DataServiceUrl + "/" + groupImage;
                        Glide.with(activity_editgroup.this)
                                .asBitmap()
                                .load(fullUrl)
                                .into(new CustomTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                        customAvatarBitmap = resource;
                                        isCustomAvatarSet = true;
                                        ivAvatar.setImageBitmap(customAvatarBitmap);
                                    }

                                    @Override
                                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                                });
                    } else {
                        if (!name.isEmpty()) refreshGeneratedAvatar(name);
                    }
                } catch (Exception e) {
                    FBMessage.Show(activity_editgroup.this, getString(R.string.edit_group_parse_failed));
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_editgroup.this, errorMessage);
            }
        });
    }

    private void setupListeners() {
        ivAvatar.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        etGroupName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isCustomAvatarSet) {
                    String name = s.toString().trim();
                    refreshGeneratedAvatar(name.isEmpty() ? getString(R.string.edit_group_default_char) : name);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        switchJoinVerify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                llInviteCodeContainer.setVisibility(View.VISIBLE);
                if (etInviteCode.getText().toString().trim().isEmpty()) {
                    etInviteCode.setText(generateRandomCode(6));
                }
            } else {
                llInviteCodeContainer.setVisibility(View.GONE);
                etInviteCode.setText("");
            }
        });

        btnGenerateCode.setOnClickListener(v -> {
            etInviteCode.setText(generateRandomCode(6));
        });

        btnSubmit.setOnClickListener(v -> submitGroupData());
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void startCrop(Uri sourceUri) {
        String destFileName = "cropped_group_avatar_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destFileName));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(85);
        options.setHideBottomControls(false);
        options.setFreeStyleCropEnabled(false);
        options.setToolbarWidgetColor(Color.TRANSPARENT);

        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(300, 300)
                .withOptions(options)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            if (data != null) {
                Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null) {
                    try {
                        customAvatarBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), resultUri);
                        ivAvatar.setImageBitmap(customAvatarBitmap);
                        isCustomAvatarSet = true;
                    } catch (IOException e) {
                        FBMessage.Show(this, getString(R.string.edit_group_image_load_failed));
                    }
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            if (data != null) {
                Throwable cropError = UCrop.getError(data);
                String errorMsg = cropError != null ? cropError.getMessage() : getString(R.string.edit_group_unknown_error);
                Toast.makeText(this, getString(R.string.edit_group_crop_failed, errorMsg), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshGeneratedAvatar(String text) {
        String displayChar = text.substring(0, 1).toUpperCase();
        generatedAvatarBitmap = createInitialAvatarBitmap(displayChar, 240, 240);
        ivAvatar.setImageBitmap(generatedAvatarBitmap);
    }

    private Bitmap createInitialAvatarBitmap(String character, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int colorIdx = Math.abs(character.hashCode()) % PALETTES.length;
        int[] palette = PALETTES[colorIdx];

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader shader = new LinearGradient(0, 0, width, height, palette[0], palette[1], Shader.TileMode.CLAMP);
        bgPaint.setShader(shader);
        canvas.drawRect(0, 0, width, height, bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(width * 0.45f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Rect bounds = new Rect();
        textPaint.getTextBounds(character, 0, character.length(), bounds);
        float baseline = (height / 2f) + (bounds.height() / 2f) - bounds.bottom;

        canvas.drawText(character, width / 2f, baseline, textPaint);
        return bitmap;
    }

    private void submitGroupData() {
        String groupName = etGroupName.getText().toString().trim();
        String groupNotice = etGroupNotice.getText().toString().trim();
        String inviteCode = etInviteCode.getText().toString().trim();

        if (groupName.isEmpty()) {
            FBMessage.Show(this, getString(R.string.edit_group_input_name_tip));
            return;
        }

        if (switchJoinVerify.isChecked() && inviteCode.isEmpty()) {
            FBMessage.Show(this, getString(R.string.edit_group_invite_code_empty_tip));
            return;
        }

        if (MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未初始化");
            return;
        }

        btnSubmit.setEnabled(false);

        // 处理头像 Base64 编码
        String base64Avatar = "";
        Bitmap finalAvatar = isCustomAvatarSet ? customAvatarBitmap : generatedAvatarBitmap;
        if (finalAvatar != null) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            finalAvatar.compress(Bitmap.CompressFormat.PNG, 90, baos);
            byte[] imageBytes = baos.toByteArray();
            base64Avatar = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
        }

        // 委托给闭源 AAR 安全提交
        MemberBillingBridge.get().saveGroup(this, App.UID, isEditMode ? groupId : null, groupName,
                groupNotice, inviteCode, base64Avatar, new IMemberBilling.SaveGroupCallback() {
                    @Override
                    public void onSuccess(String avatar) {
                        btnSubmit.setEnabled(true);
                        if (isEditMode) {
                            notifyMembersGroupUpdated(avatar);
                        }
                        FBMessage.Show(activity_editgroup.this, isEditMode ? getString(R.string.edit_group_update_success) : getString(R.string.edit_group_create_success));
                        setResult(RESULT_OK);

                        // 🌟 1. 重新从服务端全量同步好友与新建的群聊数据落库
                        FriendUtils.GET_FRIEND();

                        // 🌟 2. 联动刷新好友界面的本地数据源，确保回到主界面时立刻渲染出新建的群聊
                        if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                            activity_friend.that.runOnUiThread(() -> {
                                FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                            });
                        }

                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        btnSubmit.setEnabled(true);
                        FBMessage.Show(activity_editgroup.this, errorMessage);
                    }
                });
    }

    private void notifyMembersGroupUpdated(String avatar) {
        if (AutoReconnectWebSocket.socket == null || memberUids.isEmpty()) return;
        for (int i = 0; i < memberUids.size(); i++) {
            int targetUid = memberUids.get(i);
            try {
                String groupName = etGroupName.getText().toString().trim();
                Log.e("TT", targetUid + "/" + groupName);
                JSONObject jo = new JSONObject();
                jo.put("type", "Update_Group_Profile");
                jo.put("send_uid", App.UID);
                jo.put("send_time", System.currentTimeMillis() / 1000);

                JSONObject jo_data = new JSONObject();
                jo_data.put("_ToUid", targetUid);
                jo_data.put("_FromUID", App.UID);
                jo_data.put("group_id", groupId);
                jo_data.put("group_name", groupName);
                jo_data.put("group_avatar", avatar);
                jo.put("data", jo_data);

                AutoReconnectWebSocket.socket.sendMessage(jo.toString());
            } catch (org.json.JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private File saveBitmapToCache(Bitmap bmp) {
        if (bmp == null) return null;
        File cacheDir = new File(getExternalCacheDir(), "Cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File file = new File(cacheDir, "GROUP_AVATAR_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}