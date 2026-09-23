package com.qapp.midian;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONException;
import org.json.JSONObject;

import Data.FriendUtils;
import Data.IMemberBilling;
import Data.MemberBillingBridge;
import Utils.FBMessage;
import Utils.StatusBarUtil;
import socket.AutoReconnectWebSocket;

public class activity_group_join extends AppCompatActivity {

    private ImageView btnBack;
    private RoundedImageView ivGroupAvatar;
    private TextView tvGroupName, tvGroupMembers, tvGroupNotice;
    private EditText etReason, etInviteCode;
    private LinearLayout llInviteCodeContainer;
    private Button btnSubmit;

    private String groupId = "";
    private int ownerUid = 0;
    private int isVerify = 0; // 0直接进，1需群主审核
    private int hasInviteCode = 0; // 0无邀请码，1有邀请码

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_join);
        StatusBarUtil.setStatusBarMode(this, true, R.color.white);

        groupId = getIntent().getStringExtra("group_id");
        if (groupId != null) {
            groupId = groupId.trim();
        }

        if (TextUtils.isEmpty(groupId)) {
            FBMessage.Show(this, getString(R.string.group_join_invalid_id));
            finish();
            return;
        }

        initViews();
        fetchGroupDetail();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivGroupAvatar = findViewById(R.id.ivGroupAvatar);
        tvGroupName = findViewById(R.id.tvGroupName);
        tvGroupMembers = findViewById(R.id.tvGroupMembers);
        tvGroupNotice = findViewById(R.id.tvGroupNotice);
        etReason = findViewById(R.id.etReason);
        etInviteCode = findViewById(R.id.etInviteCode);
        llInviteCodeContainer = findViewById(R.id.llInviteCodeContainer);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnBack.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submitJoinRequest());
    }

    private void fetchGroupDetail() {
        if (Data.MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未就绪");
            return;
        }

        MemberBillingBridge.get().fetchGroupDetail(this, App.UID, groupId, new IMemberBilling.GroupDetailCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                String name = data.optString("group_name", getString(R.string.group_join_unnamed));
                String notice = data.optString("notice", getString(R.string.group_join_no_notice));
                String avatar = data.optString("avatar", "");
                int count = data.optInt("member_count", 0);
                ownerUid = data.optInt("owner_uid", 0);
                isVerify = data.optInt("is_verify", 0);
                int isJoined = data.optInt("is_joined", 0);
                hasInviteCode = data.optInt("has_invite_code", 0);

                tvGroupName.setText(name);
                tvGroupMembers.setText(getString(R.string.group_join_member_count_format, count));
                tvGroupNotice.setText(notice);

                if (hasInviteCode == 1) {
                    llInviteCodeContainer.setVisibility(View.VISIBLE);
                } else {
                    llInviteCodeContainer.setVisibility(View.GONE);
                }

                if (isVerify == 1) {
                    btnSubmit.setText(getString(R.string.group_join_btn_apply));
                } else {
                    btnSubmit.setText(getString(R.string.group_join_btn_direct));
                }

                if (isJoined == 1) {
                    btnSubmit.setText(getString(R.string.group_join_btn_already_in));
                    btnSubmit.setEnabled(false);
                } else if (isJoined == 2) {
                    btnSubmit.setText(getString(R.string.group_join_btn_reviewing));
                    btnSubmit.setEnabled(false);
                }

                if (!avatar.isEmpty()) {
                    String fullUrl = avatar.startsWith("http") ? avatar : App.DataServiceUrl + "/" + avatar;
                    Glide.with(activity_group_join.this)
                            .load(fullUrl)
                            .placeholder(R.drawable.user_default)
                            .into(ivGroupAvatar);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_group_join.this, errorMessage);
            }
        });
    }

    private void submitJoinRequest() {
        String reason = etReason.getText().toString().trim();
        String inviteCode = etInviteCode.getText().toString().trim();

        if (hasInviteCode == 1 && TextUtils.isEmpty(inviteCode)) {
            FBMessage.Show(this, getString(R.string.group_join_input_code_tip));
            return;
        }

        if (isVerify == 1 && TextUtils.isEmpty(reason)) {
            FBMessage.Show(this, getString(R.string.group_join_input_reason_tip));
            return;
        }

        if (Data.MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未就绪");
            return;
        }

        btnSubmit.setEnabled(false);

        Data.MemberBillingBridge.get().submitJoinGroup(this, App.UID, groupId, reason, inviteCode, new Data.IMemberBilling.JoinGroupCallback() {
            @Override
            public void onSuccess(int code, String msg, boolean isDirectJoin) {
                FBMessage.Show(activity_group_join.this, msg);
                if (code == 1) {
                    if (isDirectJoin) {
                        notifyOwnerMemberJoined(ownerUid);
                        FriendUtils.GET_FRIEND();
                    } else {
                        notifyOwnerMemberApplied(ownerUid);
                    }
                    finish();
                } else {
                    btnSubmit.setEnabled(true);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                btnSubmit.setEnabled(true);
                FBMessage.Show(activity_group_join.this, errorMessage);
            }
        });
    }

    private void notifyOwnerMemberJoined(int targetUid) {
        if (AutoReconnectWebSocket.socket != null && targetUid > 0) {
            try {
                JSONObject jo = new JSONObject();
                jo.put("type", "NewFriend");
                jo.put("send_uid", App.UID);
                jo.put("send_time", System.currentTimeMillis() / 1000);
                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", System.currentTimeMillis() / 1000 + "_join_" + groupId);
                jo_data.put("_ToUid", targetUid);
                jo_data.put("_FromUID", App.UID);
                jo.put("data", jo_data);

                AutoReconnectWebSocket.socket.sendMessage(jo.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyOwnerMemberApplied(int targetUid) {
        if (AutoReconnectWebSocket.socket != null && targetUid > 0) {
            try {
                JSONObject jo = new JSONObject();
                jo.put("type", "NewFriend");
                jo.put("send_uid", App.UID);
                jo.put("send_time", System.currentTimeMillis() / 1000);
                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", System.currentTimeMillis() / 1000 + "_apply_" + groupId);
                jo_data.put("_ToUid", targetUid);
                jo_data.put("_FromUID", App.UID);
                jo.put("data", jo_data);

                AutoReconnectWebSocket.socket.sendMessage(jo.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}