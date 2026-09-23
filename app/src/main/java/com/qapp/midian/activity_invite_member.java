package com.qapp.midian;

import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Utils.FBMessage;
import Utils.StatusBarUtil;
import socket.AutoReconnectWebSocket;

public class activity_invite_member extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnConfirm;
    private RecyclerView recyclerView;
    private String groupId = "";

    private List<FriendItem> friendList = new ArrayList<>();
    private Set<Integer> selectedUids = new HashSet<>();
    private InviteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite_member);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        groupId = getIntent().getStringExtra("group_id");

        btnBack = findViewById(R.id.btnBack);
        btnConfirm = findViewById(R.id.btnConfirm);
        recyclerView = findViewById(R.id.recyclerViewFriends);

        btnBack.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> submitInvite());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InviteAdapter();
        recyclerView.setAdapter(adapter);

        loadLocalFriends();
    }

    private void loadLocalFriends() {
        friendList.clear();
        String sql = "SELECT Friend_UID, Nickname, Image, Type FROM friend WHERE UID=" + App.UID + " AND Type != 30";
        Cursor cursor = App.db.rawQuery(sql, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                FriendItem item = new FriendItem();
                item.uid = cursor.getInt(cursor.getColumnIndexOrThrow("Friend_UID"));
                item.nickname = cursor.getString(cursor.getColumnIndexOrThrow("Nickname"));
                item.image = cursor.getString(cursor.getColumnIndexOrThrow("Image"));
                friendList.add(item);
            }
            cursor.close();
        }
        adapter.notifyDataSetChanged();
    }

    private void submitInvite() {
        if (selectedUids.isEmpty()) {
            FBMessage.Show(this, getString(R.string.invite_member_select_at_least_one));
            return;
        }

        if (Data.MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "安全服务未就绪");
            return;
        }

        btnConfirm.setEnabled(false);

        List<String> uidStrs = new ArrayList<>();
        for (Integer uid : selectedUids) {
            uidStrs.add(String.valueOf(uid));
        }
        String uidsParam = TextUtils.join(",", uidStrs);

        Data.MemberBillingBridge.get().inviteGroupMembers(this, App.UID, groupId, uidsParam, new Data.IMemberBilling.InviteGroupCallback() {
            @Override
            public void onSuccess(String msg) {
                notifyNewMembersAsync(new ArrayList<>(selectedUids));

                FBMessage.Show(activity_invite_member.this, msg);
                setResult(RESULT_OK);
                recyclerView.postDelayed(() -> finish(), 150);
            }

            @Override
            public void onFailure(String errorMessage) {
                btnConfirm.setEnabled(true);
                FBMessage.Show(activity_invite_member.this, errorMessage);
            }
        });
    }

    private void notifyNewMembersAsync(List<Integer> targets) {
        if (AutoReconnectWebSocket.socket == null || targets == null || targets.isEmpty()) {
            return;
        }
        new Thread(() -> {
            long baseTime = System.currentTimeMillis() / 1000;
            for (int i = 0; i < targets.size(); i++) {
                int targetUid = targets.get(i);
                try {
                    // 同样使用标准的 NewFriend 依靠 _ToUid 让服务端准确推给对方
                    JSONObject jo = new JSONObject();
                    jo.put("type", "NewFriend");
                    jo.put("send_uid", App.UID);
                    jo.put("send_time", baseTime);

                    JSONObject joData = new JSONObject();
                    joData.put("_MessageId", baseTime + "_" + targetUid + "_" + i);
                    joData.put("_ToUid", targetUid);
                    joData.put("_FromUID", App.UID);
                    joData.put("group_id", groupId);
                    joData.put("action", "invite_group"); // 自定义参数藏在 data 里

                    jo.put("data", joData);

                    AutoReconnectWebSocket.socket.sendMessage(jo.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateConfirmButton() {
        btnConfirm.setText(getString(R.string.invite_member_btn_confirm_format, selectedUids.size()));
    }

    class InviteAdapter extends RecyclerView.Adapter<InviteAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.module_select_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FriendItem item = friendList.get(position);
            holder.tvNickname.setText(item.nickname);
            holder.cbSelect.setChecked(selectedUids.contains(item.uid));

            Object loadSource;
            if (item.image != null && item.image.startsWith("http")) {
                loadSource = item.image;
            } else if (item.image != null && item.image.startsWith("/uploads/")) {
                loadSource = App.DataServiceUrl + item.image;
            } else {
                loadSource = new File(item.image != null ? item.image : "");
            }

            Glide.with(activity_invite_member.this)
                    .load(loadSource)
                    .placeholder(R.drawable.user_default)
                    .error(R.drawable.user_default)
                    .into(holder.ivAvatar);

            holder.itemView.setOnClickListener(v -> {
                if (selectedUids.contains(item.uid)) {
                    selectedUids.remove(item.uid);
                } else {
                    selectedUids.add(item.uid);
                }
                holder.cbSelect.setChecked(selectedUids.contains(item.uid));
                updateConfirmButton();
            });
        }

        @Override
        public int getItemCount() {
            return friendList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            RoundedImageView ivAvatar;
            TextView tvNickname;

            public VH(@NonNull View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cbSelect);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvNickname = itemView.findViewById(R.id.tvNickname);
            }
        }
    }

    static class FriendItem {
        int uid;
        String nickname;
        String image;
    }
}