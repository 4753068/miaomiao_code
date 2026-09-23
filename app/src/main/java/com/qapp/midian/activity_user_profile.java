package com.qapp.midian;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONObject;

import java.io.IOException;

import Data.FriendUtils;
import Utils.FBMessage;
import Utils.StatusBarUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class activity_user_profile extends AppCompatActivity {

    public static activity_user_profile that = null;

    private ImageView btnBack;
    private RoundedImageView ivUserAvatar;
    private TextView tvUserNickname, tvUserAccount, tvFriendStatus;
    private Button btnAddFriend, btnSendMessage;

    private String targetUserId = "";
    private int targetUid = 0;
    private String targetNickname = "";
    private String userid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        StatusBarUtil.setStatusBarMode(this, true, R.color.white);
        that = this;

        targetUserId = getIntent().getStringExtra("target_userid");
        Log.e("TBNA", "目标用户ID/账号: " + targetUserId);
        if (TextUtils.isEmpty(targetUserId)) {
            FBMessage.Show(this, "用户参数缺失");
            finish();
            return;
        }

        initViews();
        fetchUserProfile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        that = null;
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvUserNickname = findViewById(R.id.tvUserNickname);
        tvUserAccount = findViewById(R.id.tvUserAccount);
        tvFriendStatus = findViewById(R.id.tvFriendStatus);
        btnAddFriend = findViewById(R.id.btnAddFriend);
        btnSendMessage = findViewById(R.id.btnSendMessage);

        btnBack.setOnClickListener(v -> finish());

        // 点击添加好友
        btnAddFriend.setOnClickListener(v -> {
            if (targetUid <= 0) {
                FBMessage.Show(this, "资料加载中，请稍后重试");
                return;
            }
            btnAddFriend.setEnabled(false);
            // 传入真实完整账号 userid 与自增主键 targetUid
            FriendUtils.AddFriend(userid, targetUid);
        });

        // 已经是好友时点击直接进入会话
        btnSendMessage.setOnClickListener(v -> {
            Intent intent = new Intent(this, activity_chat.class);
            Bundle bundle = new Bundle();
            bundle.putInt("uid", targetUid);
            intent.putExtras(bundle);
            startActivity(intent);
            finish();
        });
    }

    private void fetchUserProfile() {
        if (Data.MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未就绪");
            return;
        }

        // 委托给闭源 AAR 发起带签名的安全数据获取
        Data.MemberBillingBridge.get().getUserDetail(this, App.UID, targetUserId, new Data.IMemberBilling.UserDetailCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                targetUid = data.optInt("uid", 0);
                targetNickname = data.optString("nickname", "未知用户");
                String avatar = data.optString("avatar", "");
                userid = data.optString("userid", "");
                int isFriend = data.optInt("is_friend", 0);
                int isSelf = data.optInt("is_self", 0);
                int addtofriend = data.optInt("addtofriend", 0);

                // 11位手机号后四位替换为 *
                String displayUserid = userid;
                if (displayUserid != null && displayUserid.matches("^1\\d{10}$")) {
                    displayUserid = displayUserid.substring(0, 7) + "****";
                }

                tvUserNickname.setText(targetNickname);
                tvUserAccount.setText("账号: " + displayUserid);

                if (!avatar.isEmpty()) {
                    String fullUrl = avatar.startsWith("http") ? avatar : App.DataServiceUrl + "/" + avatar;
                    Glide.with(activity_user_profile.this)
                            .load(fullUrl)
                            .placeholder(R.drawable.user_default)
                            .into(ivUserAvatar);
                }

                if (isSelf == 1) {
                    tvFriendStatus.setText("我自己");
                    btnAddFriend.setVisibility(View.GONE);
                    btnSendMessage.setVisibility(View.GONE);
                } else if (isFriend == 1) {
                    tvFriendStatus.setText("已是好友");
                    btnAddFriend.setVisibility(View.GONE);
                    btnSendMessage.setVisibility(View.VISIBLE);
                } else {
                    tvFriendStatus.setText("非好友");
                    btnSendMessage.setVisibility(View.GONE);
                    if (addtofriend == 1) {
                        btnAddFriend.setVisibility(View.VISIBLE);
                        btnAddFriend.setText("对方拒绝加好友");
                        btnAddFriend.setEnabled(false);
                    } else {
                        btnAddFriend.setVisibility(View.VISIBLE);
                        btnAddFriend.setText("添加为好友");
                        btnAddFriend.setEnabled(true);
                    }
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_user_profile.this, errorMessage);
            }
        });
    }
}