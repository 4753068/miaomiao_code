package com.qapp.midian;

import static Data.MemberUtils.ShowVipDialog;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import Data.CheckUserUtil;
import Data.FriendUtils;
import Data.MemberBillingBridge;
import Data.MemberUtils;
import Dialog.DialogUtils;
import Utils.BiometricPrompt;
import Utils.FBMessage;
import Utils.FlipDetector;
import Utils.HomePageUtils;
import Utils.ImageUtils;
import Utils.NetUtils;
import Utils.QRCodeUtils;
import Utils.RecycleViewDivider;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import adapter.Adapter_Friend;
import adapter.Module_Friend;
import socket.AutoReconnectWebSocket;

public class activity_friend extends AppCompatActivity {
    public static activity_friend that = null;
    public static TextView nettis;
    public static FrameLayout biobackground;
    public static androidx.recyclerview.widget.RecyclerView RecyclerView = null;

    public static List<Module_Friend> Datas;
    public List<Module_Friend> originalDatas = new ArrayList<>();

    public static Adapter_Friend mAdapter;
    public static TextView tips = null, nickname = null;
    private static ImageView btnSetting = null;
    public static android.app.ProgressDialog loadingDialog;

    public EditText et_search;
    // 声明翻转检测器实例
    private FlipDetector flipDetector;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);
        that = this;
        // 初始化翻转检测器，并实现翻转成功时的回调
        flipDetector = new FlipDetector(this, new FlipDetector.OnFlipListener() {
            @Override
            public void onFlipped() {
                // 触发手机翻转后的动作
                FlipDetector.returnToHome(that);
            }
        });

        biobackground = findViewById(R.id.biobackground);

        int YanzhengMode = SPUtils.getInstance().get("setting_yanzhengmode", 0);
        if (YanzhengMode == 2) {
            biobackground.setVisibility(View.VISIBLE);
            BiometricPrompt.showBiometricPrompt(that);
        } else if (YanzhengMode == 1) {
            biobackground.setVisibility(View.VISIBLE);
            CheckUserUtil.ShowPin(that);
        } else {
            biobackground.setVisibility(View.GONE);
        }

        nettis = findViewById(R.id.nettis);
        if (NetUtils.getAPNType() == 0) nettis.setVisibility(View.VISIBLE);
        btnSetting = findViewById(R.id.btnSetting);
        tips = findViewById(R.id.tips);

        RecyclerView = findViewById(R.id.recycler_friend_list);
        RecyclerView.setLayoutManager(new LinearLayoutManager(that));
        RecyclerView.addItemDecoration(new RecycleViewDivider(LinearLayoutManager.VERTICAL, 0, getResources().getColor(R.color.white)));
        Datas = new ArrayList<>();
        mAdapter = new Adapter_Friend(Datas);
        RecyclerView.setAdapter(mAdapter);
        ((SimpleItemAnimator) RecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        et_search = findViewById(R.id.et_search);

        et_search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                applySearchFilter();
                return true;
            }
            return false;
        });

        et_search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearchFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        ImageView btn_app = findViewById(R.id.btn_app);
        btn_app.setOnClickListener(v -> {
            Intent it = new Intent();
            it.setClass(that, activity_web.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle bundle_chat = new Bundle();
            bundle_chat.putString("url", "https://member.am930.cn/?app=midian&func=app");
            it.putExtras(bundle_chat);
            App.AppContext.startActivity(it);
        });

        FloatingActionButton btnAddFriend = findViewById(R.id.btnAddFriend);
        btnAddFriend.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(App.AppContext).inflate(R.layout.popup_friend, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            popupView.findViewById(R.id.top_scan).setOnClickListener(it -> {
                IntentIntegrator intentIntegrator = new IntentIntegrator(that);
                intentIntegrator.setBeepEnabled(true);
                intentIntegrator.setCaptureActivity(activity_qr.class);
                intentIntegrator.initiateScan();
                popupWindow.dismiss();
            });

            popupView.findViewById(R.id.top_id).setOnClickListener(it -> {
                Intent it_search_friend = new Intent();
                it_search_friend.setClass(that, activity_search_network.class);
                that.startActivity(it_search_friend);
                popupWindow.dismiss();
            });

            popupView.findViewById(R.id.top_group).setOnClickListener(it -> {
                Intent it_qr = new Intent();
                it_qr.setClass(that, activity_editgroup.class);
                that.startActivity(it_qr);
                popupWindow.dismiss();
            });

            popupView.findViewById(R.id.top_qrcode).setOnClickListener(it -> {
                String userid = SPUtils.getInstance().get("userid", "0");
                String nick_name = SPUtils.getInstance().get("nickname", "0");
                String image = SPUtils.getInstance().get("image", "0");
                String qrContent = "ADD_FRIEND:" + userid;
                QRCodeUtils.showQrCodeDialog(that, qrContent, nick_name);
                popupWindow.dismiss();
            });

            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupHeightPx = popupView.getMeasuredHeight();
            int offsetY = -(btnAddFriend.getHeight() + popupHeightPx + 30);

            popupWindow.showAsDropDown(btnAddFriend, 0, offsetY);
        });

        btnSetting.setOnClickListener(view -> {
            Intent it = new Intent();
            it.setClass(that, activity_setting.class);
            that.startActivity(it);
        });

        handleShareIntent(getIntent());
    }

    // ✨ 新增一个强制刷新 UI 列表的方法
    public static void forceRefreshList() {
        if (that != null && !that.isFinishing()) {
            that.runOnUiThread(() -> {
                FriendUtils.GET_LOCATION_FRIEND(that);
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    public void applySearchFilter() {
        if (originalDatas == null) return;

        String keyword = et_search != null ? et_search.getText().toString().trim().toLowerCase() : "";
        Datas.clear();

        if (keyword.isEmpty()) {
            Datas.addAll(originalDatas);
        } else {
            for (Module_Friend friend : originalDatas) {
                String name = friend.getNickname() != null ? friend.getNickname().toLowerCase() : "";
                String uid = friend.getUserid() != null ? friend.getUserid().toLowerCase() : "";

                if (name.contains(keyword) || uid.contains(keyword)) {
                    Datas.add(friend);
                }
            }
        }

        mAdapter.notifyDataSetChanged();

        // 搜索过滤后重新查询当前可见好友的在线状态
        queryFriendsOnlineStatus();

        if (Datas.isEmpty()) {
            tips.setVisibility(View.VISIBLE);
            tips.setText(keyword.isEmpty() ? getString(R.string.friend_empty_list) : getString(R.string.friend_search_empty));
            RecyclerView.setVisibility(View.GONE);
        } else {
            tips.setVisibility(View.GONE);
            RecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {
            Context context = activity_friend.this;

            if (result.getContents() == null) {
                FBMessage.Show(context, getString(R.string.friend_scan_no_useful_info));
            } else {
                String scanResult = result.getContents().trim();

                if (scanResult.startsWith("ADD_FRIEND:")) {
                    String[] s = scanResult.split(":");
                    Log.e("QRCODE",s.length+"");
                    if (s.length >= 2) {
                        String targetUserId = s[1];
                        Intent intent = new Intent(context, activity_user_profile.class);
                        intent.putExtra("target_userid", targetUserId);
                        startActivity(intent);
                    } else {
                        FBMessage.Show(context, getString(R.string.friend_qr_format_error));
                    }
                } else if (scanResult.startsWith("ADD_GROUP:")) {
                    String[] s = scanResult.split(":");
                    if (s.length >= 2) {
                        String groupId = s[1];
                        Intent intent = new Intent(context, activity_group_join.class);
                        intent.putExtra("group_id", groupId);
                        startActivity(intent);
                    } else {
                        FBMessage.Show(context, getString(R.string.friend_group_qr_format_error));
                    }
                } else if (scanResult.startsWith("midian://group/join?group_id=")) {
                    String groupId = scanResult.replace("midian://group/join?group_id=", "").trim();
                    Intent intent = new Intent(context, activity_group_join.class);
                    intent.putExtra("group_id", groupId);
                    startActivity(intent);
                } else if (scanResult.startsWith("miaomiao_login:")) {
                    String webSessionId = scanResult.replace("miaomiao_login:", "").trim();
                    sendQrLoginConfirmPacket(webSessionId);
                } else {
                    FBMessage.Show(context, getString(R.string.friend_scan_no_useful_info));
                }
            }
        }
    }

    private void sendQrLoginConfirmPacket(String webSessionId) {
        if (AutoReconnectWebSocket.socket != null && AutoReconnectWebSocket.socketConnected.get()) {
            try {
                JSONObject packet = new JSONObject();
                packet.put("type", "QrLoginConfirm");
                packet.put("send_uid", App.UID);
                packet.put("send_time", System.currentTimeMillis() / 1000);

                JSONObject data = new JSONObject();
                data.put("_SessionId", webSessionId);
                data.put("_UserUid", App.UID);
                packet.put("data", data);

                AutoReconnectWebSocket.socket.sendMessage(packet.toString());
                FBMessage.Show(this, getString(R.string.friend_qr_login_success));

            } catch (JSONException e) {
                Log.e("QrLogin", "组装扫码确认 JSON 数据包失败", e);
            }
        } else {
            FBMessage.Show(this, getString(R.string.friend_qr_login_failed));
        }
    }

    // ✨ 客户端主动向服务端发送查询在线状态的指令
    public void queryFriendsOnlineStatus() {
        if (Datas == null || Datas.isEmpty()) return;

        List<String> uidsToQuery = new ArrayList<>();
        for (Module_Friend friend : Datas) {
            // 过滤群组
            if (friend.getType() != 30) {
                uidsToQuery.add(String.valueOf(friend.getFriendUid()));
            }
        }

        if (uidsToQuery.isEmpty()) return;

        if (AutoReconnectWebSocket.socket != null && AutoReconnectWebSocket.socketConnected.get()) {
            try {
                JSONObject packet = new JSONObject();
                packet.put("type", "CheckOnlineStatus");

                JSONObject data = new JSONObject();
                data.put("uids", new JSONArray(uidsToQuery));

                packet.put("data", data);

                AutoReconnectWebSocket.socket.sendMessage(packet.toString());
                Log.d("FriendOnline", "已发送在线状态查询请求: " + uidsToQuery.size() + " 个用户");
            } catch (Exception e) {
                Log.e("FriendOnline", "组装在线状态查询 JSON 失败", e);
            }
        }
    }

    // ✨ 供全局 WebSocket 接收器调用，全量刷新在线状态
    public static void updateOnlineStatuses(Map<Integer, Boolean> statuses) {
        if (mAdapter != null && statuses != null) {
            mAdapter.updateOnlineStatuses(statuses);
        }
    }

    // ✨ 供全局 WebSocket 接收器调用，单点推送即时更新
    public static void updateSingleOnlineStatus(int uid, boolean isOnline) {
        if (mAdapter != null) {
            mAdapter.updateSingleOnlineStatus(uid, isOnline);
        }
    }

    @SuppressLint("Range")
    @Override
    protected void onResume() {
        super.onResume();
        that = this;

        // 当聊天界面进入前台获得焦点时，启动传感器监听
        boolean switch_sensorlock=SPUtils.getInstance().get("switch_sensorlock",false);
        if (flipDetector != null && switch_sensorlock) {
            flipDetector.start();
        }


        // 🌟 委托给 AAR 闭源检查并执行非 VIP 降级重置
        if (Data.MemberBillingBridge.get() != null) {
            Data.MemberBillingBridge.get().checkAndResetExpiredVipPrivileges(this, () -> {
                try {
                    PackageManager packageManager = App.AppContext.getPackageManager();
                    packageManager.setComponentEnabledSetting(
                            new ComponentName(App.AppContext, "com.qapp.midian.activity_main"),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                    );
                    new Handler(Looper.getMainLooper()).postDelayed(HomePageUtils::getCurrentLauncher, 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        if (App.FriendPass) {
            RecyclerView.setVisibility(View.VISIBLE);
        }

        String sql = "select * from friend where UID=" + App.UID + " limit 1";
        Cursor cursor = App.db.rawQuery(sql, null);
        if (cursor.getCount() == 0) {
            tips.setVisibility(View.VISIBLE);
            RecyclerView.setVisibility(View.GONE);
            loadingDialog = new android.app.ProgressDialog(that);
            loadingDialog.setMessage(getString(R.string.friend_syncing_dialog));
            loadingDialog.setCancelable(false);
            loadingDialog.show();
            FriendUtils.GET_FRIEND();
        } else {
            if (!App.isErrorPassword) {
                FriendUtils.GET_LOCATION_FRIEND(that);
                // 延迟发起在线状态查询，确保本地好友列表已经挂载
                new Handler(Looper.getMainLooper()).postDelayed(() -> queryFriendsOnlineStatus(), 500);
            } else {
                tips.setVisibility(View.VISIBLE);
                RecyclerView.setVisibility(View.GONE);
            }
        }
        cursor.close();

        String image = SPUtils.getInstance().get("image", "");
        Object loadModel = image;

        if (image.startsWith("data:image")) {
            try {
                String base64Data = image.split(",")[1];
                loadModel = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            } catch (Exception ignored) {}
        }

        Glide.with(this)
                .load(loadModel)
                .centerCrop()
                .transform(new RoundedCorners(48))
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        btnSetting.setImageBitmap(((BitmapDrawable) resource).getBitmap());
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        btnSetting.setImageDrawable(getResources().getDrawable(R.drawable.user_default));
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 只有不置空，子页面（如建群、改资料）保存成功时才能通过 that 通知主界面立即刷新！

        if (SPUtils.getInstance().get("switch_apphomelock", false)) App.FriendPass = false;

        boolean switch_sensorlock = SPUtils.getInstance().get("switch_sensorlock", false);
        if (flipDetector != null && switch_sensorlock) {
            flipDetector.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null; // 保持在这里置空即可
        App.isErrorPassword = false;
        if (SPUtils.getInstance().get("switch_apphomelock", false)) App.FriendPass = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (action == null) return;

        new Thread(() -> {
            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (type.startsWith("text/")) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    String sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT); // 浏览器通常会把标题放这里

                    StringBuilder sb = new StringBuilder();
                    if (sharedSubject != null && !sharedSubject.isEmpty()) {
                        sb.append(sharedSubject).append("\n");
                    }
                    if (sharedText != null && !sharedText.isEmpty()) {
                        sb.append(sharedText);
                    }

                    String finalText = sb.toString().trim();

                    if (!finalText.isEmpty()) {
                        App.ShareText = finalText;
                        App.ShareUris.clear();
                        runOnUiThread(() -> {
                            if (that != null) FBMessage.Show(that, that.getString(R.string.friend_share_text_received));
                        });
                    }
                } else {
                    Uri mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (mediaUri != null) {
                        App.ShareUris.clear();
                        Uri safeUri = convertToSafeLocalUri(mediaUri);
                        if (safeUri != null) {
                            App.ShareUris.add(safeUri);
                            App.ShareText = "";
                            runOnUiThread(() -> {
                                if (that != null) FBMessage.Show(that, that.getString(R.string.friend_share_file_received));
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (that != null) FBMessage.Show(that, that.getString(R.string.friend_share_file_error));
                            });
                        }
                    }
                }
                intent.setAction(null);
                setIntent(intent);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                ArrayList<Uri> mediaUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (mediaUris != null && !mediaUris.isEmpty()) {
                    App.ShareUris.clear();
                    int successCount = 0;
                    for (Uri uri : mediaUris) {
                        Uri safeUri = convertToSafeLocalUri(uri);
                        if (safeUri != null) {
                            App.ShareUris.add(safeUri);
                            successCount++;
                        }
                    }
                    App.ShareText = "";
                    int finalSuccessCount = successCount;
                    runOnUiThread(() -> {
                        if (that != null) FBMessage.Show(that, that.getString(R.string.friend_share_files_count_received, finalSuccessCount));
                    });
                }
                intent.setAction(null);
                setIntent(intent);
            }
        }).start();
    }

    private Uri convertToSafeLocalUri(Uri externalUri) {
        if (externalUri == null) return null;

        if ("file".equals(externalUri.getScheme())) {
            return externalUri;
        }

        try {
            String fileName = "share_" + System.currentTimeMillis();
            Cursor cursor = getContentResolver().query(externalUri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.isEmpty()) fileName = name;
                    }
                }
                cursor.close();
            }

            File cacheDir = new File(getExternalCacheDir(), "shared_files");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File localFile = new File(cacheDir, fileName);

            try (java.io.InputStream is = getContentResolver().openInputStream(externalUri);
                 java.io.FileOutputStream os = new java.io.FileOutputStream(localFile)) {
                if (is == null) return null;
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
                return Uri.fromFile(localFile);
            }
        } catch (Exception e) {
            Log.e("SharePermission", "拷贝分享文件失败: " + e.getMessage());
            return null;
        }
    }
}