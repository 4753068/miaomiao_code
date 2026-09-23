package com.qapp.midian;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import Data.FriendUtils;
import Data.IMemberBilling;
import Data.MemberBillingBridge;
import Utils.FBMessage;
import Utils.QRCodeUtils;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import socket.AutoReconnectWebSocket;

public class activity_group_profile extends AppCompatActivity {
    public static activity_group_profile that;
    private ImageView btnBack, btnQrCode, btnSetting;
    private RoundedImageView ivGroupAvatar;
    private TextView tvGroupName, tvGroupNotice, tvMemberCount;
    private RecyclerView recyclerViewMembers;

    private CardView cardGroupAction;
    private TextView tvGroupAction;

    private String groupId = "";
    private int localFriendUid = 0;
    private MemberAdapter adapter;
    private final List<MemberModel> memberList = new ArrayList<>();

    private boolean isGroupOwner = false;
    private boolean isDeleteMode = false;
    private String groupName = "";
    private SwitchCompat switchDnd;
    private CardView cardDnd;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_profile);
        StatusBarUtil.setStatusBarMode(this, true, R.color.white);
        that = this;

        groupId = getIntent().getStringExtra("group_id");
        localFriendUid = getIntent().getIntExtra("local_friend_uid", 0);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isDeleteMode) {
                    isDeleteMode = false;
                    adapter.notifyDataSetChanged();
                } else {
                    finish();
                }
            }
        });

        initViews();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnQrCode = findViewById(R.id.btnQrCode);
        btnSetting = findViewById(R.id.btnSetting);

        ivGroupAvatar = findViewById(R.id.ivGroupAvatar);
        tvGroupName = findViewById(R.id.tvGroupName);
        tvGroupNotice = findViewById(R.id.tvGroupNotice);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        recyclerViewMembers = findViewById(R.id.recyclerViewMembers);

        cardGroupAction = findViewById(R.id.cardGroupAction);
        tvGroupAction = findViewById(R.id.tvGroupAction);

        btnBack.setOnClickListener(v -> {
            if (isDeleteMode) {
                isDeleteMode = false;
                adapter.notifyDataSetChanged();
            } else {
                finish();
            }
        });

        btnQrCode.setOnClickListener(view -> {
            String qrContent = "ADD_GROUP:" + groupId;
            QRCodeUtils.showQrCodeDialog(that, qrContent, getString(R.string.group_profile_qrcode_title, groupName));
        });

        btnSetting.setOnClickListener(v -> {
            Intent intent = new Intent(activity_group_profile.this, activity_editgroup.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        });

        recyclerViewMembers.setLayoutManager(new GridLayoutManager(this, 5));
        adapter = new MemberAdapter();
        recyclerViewMembers.setAdapter(adapter);

        cardDnd = findViewById(R.id.cardDnd);
        switchDnd = findViewById(R.id.switch_dnd);

        // 读取当前群的免打扰状态（默认 false）
        String dndKey = "group_dnd_" + groupId;
        boolean isDnd = SPUtils.getInstance().get(dndKey, false);
        switchDnd.setChecked(isDnd);

// 切换监听
        switchDnd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 🌟 关键：只有当用户真正用手指点击/滑动开关时才触发接口请求
            // 避免代码调用 setChecked() 初始化时重复触发网络请求
            if (!buttonView.isPressed()) {
                return;
            }

            if (MemberBillingBridge.get() == null) {
                FBMessage.Show(activity_group_profile.this, "服务未就绪");
                switchDnd.setChecked(!isChecked);
                return;
            }

            int miandarao = isChecked ? 1 : 0;

            // 调用通用免打扰更新接口
            MemberBillingBridge.get().updateDndStatus(
                    activity_group_profile.this,
                    App.UID,
                    localFriendUid,
                    groupId,
                    miandarao,
                    new IMemberBilling.DndCallback() {
                        @Override
                        public void onSuccess(int result) {
                            boolean finalStatus = (result == 1);
                            // 1. 同步保存 SP，供接收消息时快速判断
                            SPUtils.getInstance().put(dndKey, finalStatus);
                            if (localFriendUid > 0) {
                                SPUtils.getInstance().put("group_dnd_uid_" + localFriendUid, finalStatus);
                            }
                            // 2. 本地数据库 friend 表对应记录同步更新（如果有 miandarao 字段）
                            if (localFriendUid > 0 && App.db != null && App.db.isOpen()) {
                                try {
                                    App.db.execSQL("UPDATE friend SET miandarao=? WHERE Friend_UID=? AND UID=?",
                                            new Object[]{result, localFriendUid, App.UID});
                                } catch (Exception ignored) {}
                            }
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            FBMessage.Show(activity_group_profile.this, errorMsg);
                            // 失败时回滚 Switch 状态（因为此时 buttonView.isPressed() 为 false，不会死循环）
                            switchDnd.setChecked(!isChecked);
                        }
                    }
            );
        });

        // 点击整个卡片时触发 switch 切换
        if (cardDnd != null) {
            cardDnd.setOnClickListener(v -> switchDnd.toggle());
        }
    }

    private void fetchGroupData() {
        if (MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未就绪");
            return;
        }

        MemberBillingBridge.get().fetchGroupProfile(this, App.UID, groupId, new IMemberBilling.GroupProfileCallback() {
            @Override
            public void onSuccess(JSONObject jsonObject) {
                try {
                    groupName = jsonObject.optString("group_name", getString(R.string.group_profile_default_name));
                    String notice = jsonObject.optString("notice", getString(R.string.group_profile_default_notice));
                    String groupImage = jsonObject.optString("group_image", "");

                    JSONArray array = jsonObject.optJSONArray("data");
                    if (array == null) array = new JSONArray();

                    memberList.clear();
                    isGroupOwner = false;

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        MemberModel m = new MemberModel();

                        m.uid = obj.optInt("uid", 0);
                        m.role = obj.optInt("role", 0);
                        m.nickname = obj.optString("nickname", getString(R.string.group_profile_unknown_member));
                        m.image = obj.optString("image", "");
                        memberList.add(m);

                        if (m.uid == App.UID && m.role == 1) {
                            isGroupOwner = true;
                        }
                    }

                    tvGroupName.setText(groupName);
                    tvGroupNotice.setText(notice);
                    tvMemberCount.setText(getString(R.string.group_profile_member_count_format, memberList.size()));

                    if (ivGroupAvatar != null && !groupImage.isEmpty()) {
                        String fullUrl = groupImage.startsWith("http") ? groupImage : App.DataServiceUrl + "/" + groupImage;
                        Glide.with(activity_group_profile.this)
                                .load(fullUrl)
                                .placeholder(R.drawable.user_default)
                                .into(ivGroupAvatar);
                    }

                    adapter.notifyDataSetChanged();

                    if (isGroupOwner) {
                        btnSetting.setVisibility(View.VISIBLE);
                        tvGroupAction.setText(getString(R.string.group_profile_action_disband));
                        cardGroupAction.setOnClickListener(v -> showDisbandConfirmDialog());
                    } else {
                        btnSetting.setVisibility(View.GONE);
                        tvGroupAction.setText(getString(R.string.group_profile_action_exit));
                        cardGroupAction.setOnClickListener(v -> showExitConfirmDialog());
                    }
                } catch (Exception e) {
                    Log.e("GroupProfile", "UI渲染错误: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_group_profile.this, errorMessage);
            }
        });


    }

    private void showDeleteMemberDialog(MemberModel model) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.group_profile_dialog_title))
                .setMessage(getString(R.string.group_profile_remove_member_confirm, model.nickname))
                .setPositiveButton(getString(R.string.group_profile_btn_confirm_remove), (dialog, which) -> executeDeleteMember(model))
                .setNegativeButton(getString(R.string.group_profile_dialog_cancel), null)
                .show();
    }

    private void executeDeleteMember(MemberModel model) {
        if (MemberBillingBridge.get() == null) return;

        MemberBillingBridge.get().removeGroupMember(this, App.UID, groupId, model.uid, new IMemberBilling.GroupActionCallback() {
            @Override
            public void onSuccess(String message) {
                notifyMemberRemoved(model.uid);

                runOnUiThread(() -> {
                    FBMessage.Show(activity_group_profile.this, getString(R.string.group_profile_member_removed));

                    for (int i = 0; i < memberList.size(); i++) {
                        if (memberList.get(i).uid == model.uid) {
                            memberList.remove(i);
                            break;
                        }
                    }
                    tvMemberCount.setText(getString(R.string.group_profile_member_count_format, memberList.size()));
                    adapter.notifyDataSetChanged();
                    fetchGroupData();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> FBMessage.Show(activity_group_profile.this, errorMessage));
            }
        });
    }

    private void notifyMemberRemoved(int targetUid) {
        if (AutoReconnectWebSocket.socket != null && targetUid > 0) {
            try {
                // 恢复为服务端认可的 NewFriend 信令进行通讯
                JSONObject jo = new JSONObject();
                jo.put("type", "NewFriend");
                jo.put("send_uid", App.UID);
                jo.put("send_time", System.currentTimeMillis() / 1000);

                JSONObject joData = new JSONObject();
                joData.put("_MessageId", System.currentTimeMillis() / 1000 + "_kick_" + groupId);
                joData.put("_ToUid", targetUid);
                joData.put("_FromUID", App.UID);
                joData.put("group_id", groupId);
                joData.put("action", "remove_group"); // 将移除动作标志藏在内部，让接收端自行判断
                jo.put("data", joData);

                AutoReconnectWebSocket.socket.sendMessage(jo.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.group_profile_exit_dialog_title))
                .setMessage(getString(R.string.group_profile_exit_dialog_msg))
                .setPositiveButton(getString(R.string.group_profile_btn_confirm_exit), (dialog, which) -> exitGroup())
                .setNegativeButton(getString(R.string.group_profile_dialog_cancel), null)
                .show();
    }

    private void exitGroup() {
        if (MemberBillingBridge.get() == null) return;

        MemberBillingBridge.get().removeGroupMember(this, App.UID, groupId, App.UID, new IMemberBilling.GroupActionCallback() {
            @Override
            public void onSuccess(String message) {
                clearLocalGroupDataAndFinish(getString(R.string.group_profile_exit_success));
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_group_profile.this, errorMessage);
            }
        });
    }

    private void showDisbandConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.group_profile_disband_dialog_title))
                .setMessage(getString(R.string.group_profile_disband_dialog_msg))
                .setPositiveButton(getString(R.string.group_profile_btn_confirm_disband), (dialog, which) -> disbandGroup())
                .setNegativeButton(getString(R.string.group_profile_dialog_cancel), null)
                .show();
    }

    private void disbandGroup() {
        if (MemberBillingBridge.get() == null) return;

        MemberBillingBridge.get().disbandGroup(this, App.UID, groupId, new IMemberBilling.GroupActionCallback() {
            @Override
            public void onSuccess(String message) {
                notifyMembersGroupDisbanded();
                clearLocalGroupDataAndFinish(getString(R.string.group_profile_disband_success));
            }

            @Override
            public void onFailure(String errorMessage) {
                FBMessage.Show(activity_group_profile.this, errorMessage);
            }
        });
    }

    private void notifyMembersGroupDisbanded() {
        if (AutoReconnectWebSocket.socket != null && memberList != null) {
            for (MemberModel member : memberList) {
                if (member.uid != App.UID) {
                    try {
                        JSONObject jo = new JSONObject();
                        jo.put("type", "Group_Disbanded");
                        jo.put("send_uid", App.UID);
                        jo.put("send_time", System.currentTimeMillis() / 1000);

                        JSONObject joData = new JSONObject();
                        joData.put("_MessageId", System.currentTimeMillis() / 1000 + "_disband_" + groupId);
                        joData.put("_ToUid", member.uid);
                        joData.put("_FromUID", App.UID);
                        joData.put("group_id", groupId);
                        jo.put("data", joData);

                        AutoReconnectWebSocket.socket.sendMessage(jo.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void clearLocalGroupDataAndFinish(String toastMsg) {
        App.db.execSQL("delete from friend where Friend_UID=" + localFriendUid);
        App.db.execSQL("delete from chat where ToUid=" + localFriendUid + " or FromUID=" + localFriendUid);

        runOnUiThread(() -> {
            FBMessage.Show(activity_group_profile.this, toastMsg);
            if (activity_chat.that != null) {
                activity_chat.that.finish();
            }
            if (activity_friend.that != null) {
                FriendUtils.GET_FRIEND();
            }
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            fetchGroupData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchGroupData();
    }

    class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.module_group_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            int memberCount = memberList.size();

            if (position == memberCount) {
                holder.tvMemberName.setText(getString(R.string.group_profile_member_add));
                holder.tvMemberRole.setVisibility(View.GONE);
                holder.ivDeleteBadge.setVisibility(View.GONE);
                holder.ivMemberAvatar.setImageResource(R.drawable.jia2);

                holder.itemView.setOnClickListener(v -> {
                    if (isDeleteMode) {
                        isDeleteMode = false;
                        notifyDataSetChanged();
                    }
                    Intent intent = new Intent(activity_group_profile.this, activity_invite_member.class);
                    intent.putExtra("group_id", groupId);
                    startActivityForResult(intent, 101);
                });
                return;
            }

            if (isGroupOwner && position == memberCount + 1) {
                holder.tvMemberName.setText(isDeleteMode ? getString(R.string.group_profile_member_done) : getString(R.string.group_profile_member_delete));
                holder.tvMemberRole.setVisibility(View.GONE);
                holder.ivDeleteBadge.setVisibility(View.GONE);
                holder.ivMemberAvatar.setImageResource(isDeleteMode ? R.drawable.fanhui : R.drawable.jian);

                holder.itemView.setOnClickListener(v -> {
                    isDeleteMode = !isDeleteMode;
                    notifyDataSetChanged();
                });
                return;
            }

            MemberModel model = memberList.get(position);
            holder.tvMemberName.setText(model.nickname);

            if (model.role == 1) {
                holder.tvMemberRole.setVisibility(View.VISIBLE);
                holder.tvMemberRole.setText(getString(R.string.group_profile_role_owner));
                holder.tvMemberRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F0FF")));
                holder.tvMemberRole.setTextColor(Color.parseColor("#1A65FF"));
            } else {
                holder.tvMemberRole.setVisibility(View.GONE);
            }

            if (model.image != null && !model.image.isEmpty()) {
                String fullUrl = model.image.startsWith("http") ? model.image : App.DataServiceUrl + "/" + model.image;
                Glide.with(activity_group_profile.this)
                        .load(fullUrl)
                        .placeholder(R.drawable.user_default)
                        .into(holder.ivMemberAvatar);
            } else {
                holder.ivMemberAvatar.setImageResource(R.drawable.user_default);
            }

            if (isDeleteMode && model.role != 1) {
                holder.ivDeleteBadge.setVisibility(View.VISIBLE);
                View.OnClickListener deleteClick = v -> showDeleteMemberDialog(model);
                holder.ivDeleteBadge.setOnClickListener(deleteClick);
                holder.itemView.setOnClickListener(deleteClick);
            } else {
                holder.ivDeleteBadge.setVisibility(View.GONE);
                holder.ivDeleteBadge.setOnClickListener(null);
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            int count = memberList.size() + 1;
            if (isGroupOwner) {
                count += 1;
            }
            return count;
        }

        class VH extends RecyclerView.ViewHolder {
            RoundedImageView ivMemberAvatar;
            TextView tvMemberName, tvMemberRole;
            ImageView ivDeleteBadge;

            public VH(@NonNull View itemView) {
                super(itemView);
                ivMemberAvatar = itemView.findViewById(R.id.ivMemberAvatar);
                tvMemberName = itemView.findViewById(R.id.tvMemberName);
                tvMemberRole = itemView.findViewById(R.id.tvMemberRole);
                ivDeleteBadge = itemView.findViewById(R.id.ivDeleteBadge);
            }
        }
    }

    static class MemberModel {
        int uid;
        int role;
        String nickname;
        String image;
    }
}