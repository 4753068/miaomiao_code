package com.qapp.midian;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import Data.FriendUtils;
import Data.IMemberBilling;
import Data.MemberBillingBridge;
import Dialog.DialogUtils;
import Utils.FBMessage;
import Utils.SPUtils;
import Utils.StatusBarUtil;

public class activity_friendsetting extends AppCompatActivity {
    public static activity_friendsetting that;
    public static SwitchCompat switchPauseContact;
    private ImageView btnTopSettings;
    private RelativeLayout menuRemark;
    public static SwitchCompat switchDnd;
    private RelativeLayout menuClearChat;
    private RelativeLayout menuClearBothChat;
    private CardView btnDeleteFriend;

    public static int Friend_UID = 0;
    private int currentType = 0;
    private RelativeLayout menuSearchChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friendsetting);
        that = this;
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            Friend_UID = bundle.getInt("friend_uid");
        }

        initViews();
    }

    private void initViews() {
        btnTopSettings = findViewById(R.id.btn_top_settings);
        menuRemark = findViewById(R.id.menu_remark);
        switchDnd = findViewById(R.id.switch_dnd);
        menuClearChat = findViewById(R.id.menu_clear_chat);
        menuClearBothChat = findViewById(R.id.menu_clear_both_chat);
        btnDeleteFriend = findViewById(R.id.btn_delete_friend);
        switchPauseContact = findViewById(R.id.switch_pause_contact);
        menuSearchChat = findViewById(R.id.menu_search_chat);

        btnTopSettings.setOnClickListener(v -> {
            Intent it = new Intent();
            it.setClass(that, activity_setting.class);
            that.startActivity(it);
        });

        menuRemark.setOnClickListener(v -> {
            DialogUtils.showCustomRemarkDialog(that);
        });

        // 仅清空自己本机的记录
        menuClearChat.setOnClickListener(v -> {
            DialogUtils.showClearChatDialog(that, activity_chat.Friend_UID);
        });

        // 清空双方的聊天记录
        menuClearBothChat.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(that)
                    .setTitle(getString(R.string.friendsetting_clear_both_title))
                    .setMessage(getString(R.string.friendsetting_clear_both_message))
                    .setPositiveButton(getString(R.string.friendsetting_dialog_confirm), (dialog, which) -> {
                        if (MemberBillingBridge.get() == null) return;

                        MemberBillingBridge.get().clearBothSidesChat(that, App.UID, Friend_UID, App.db, new IMemberBilling.ClearBothSidesCallback() {
                            @Override
                            public void onSuccess() {
                                try {
                                    org.json.JSONObject jo = new org.json.JSONObject();
                                    jo.put("type", "Delete_Both_Sides_Chat");
                                    jo.put("uid", App.UID);

                                    org.json.JSONObject jo_data = new org.json.JSONObject();
                                    jo_data.put("_FromUID", App.UID);
                                    jo_data.put("_ToUid", Friend_UID);
                                    jo.put("data", jo_data);

                                    if (socket.AutoReconnectWebSocket.socket != null) {
                                        socket.AutoReconnectWebSocket.socket.sendRawMessage(jo.toString());
                                    }
                                } catch (Exception ignored) {}

                                FBMessage.Show(that, getString(R.string.friendsetting_clear_both_success));
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                if ("VIP_REQUIRED".equals(errorMessage)) {
                                    MemberBillingBridge.get().showSubscriptionDialog(that);
                                } else {
                                    FBMessage.Show(that, errorMessage);
                                }
                            }
                        });

                        dialog.dismiss();
                    })
                    .setNegativeButton(getString(R.string.friendsetting_dialog_cancel), null)
                    .show();
        });

        btnDeleteFriend.setOnClickListener(v -> {
            DialogUtils.showDeleteFriendDialog(that, Friend_UID);
        });

        menuSearchChat.setOnClickListener(v -> {
            Intent it = new Intent(that, activity_search_chat.class);
            it.putExtra("friend_uid", Friend_UID);
            startActivity(it);
        });
    }

    private void setListeners() {
        switchDnd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // 避免代码恢复状态时重复触发

            if (MemberBillingBridge.get() == null) {
                FBMessage.Show(that, "服务未就绪");
                switchDnd.setChecked(!isChecked);
                return;
            }

            int miandarao = isChecked ? 1 : 0;
            // 🌟 单聊好友：传入 friend_uid，groupId 传空字符串 ""
            MemberBillingBridge.get().updateDndStatus(that, App.UID, Friend_UID, "", miandarao, new IMemberBilling.DndCallback() {
                @Override
                public void onSuccess(int result) {
                    boolean finalDnd = (result == 1);
                    // 1. 同步保存好友专属免打扰标识
                    SPUtils.getInstance().put("friend_dnd_" + Friend_UID, finalDnd);

                    // 2. 本地数据库 friend 表同步更新
                    if (App.db != null && App.db.isOpen()) {
                        try {
                            App.db.execSQL("UPDATE friend SET miandarao=? WHERE Friend_UID=? AND UID=?",
                                    new Object[]{result, Friend_UID, App.UID});
                        } catch (Exception ignored) {}
                    }
                }

                @Override
                public void onFailure(String errorMsg) {
                    FBMessage.Show(that, errorMsg);
                    switchDnd.setChecked(!isChecked); // 失败回滚开关状态
                }
            });
        });

        switchPauseContact.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && currentType == 2) {
                DialogUtils.showPauseContactDialog(that);
                currentType = 3;
            } else if (!isChecked) {
                FriendUtils.UpdateType(2, Friend_UID);
                currentType = 2;
            }
        });
    }

    public void setFriendType() {
        String sql_isFriend = "select * from friend where (Friend_UID=" + Friend_UID + " and uid=" + App.UID + ") limit 1";
        Cursor cursor_isFriend = App.db.rawQuery(sql_isFriend, null);

        currentType = 2;
        int dbMiandarao = 0;
        if (cursor_isFriend.getCount() > 0) {
            cursor_isFriend.moveToFirst();
            currentType = cursor_isFriend.getInt(cursor_isFriend.getColumnIndexOrThrow("Type"));

            int dndIndex = cursor_isFriend.getColumnIndex("miandarao");
            if (dndIndex != -1) {
                dbMiandarao = cursor_isFriend.getInt(dndIndex);
            }
        }
        cursor_isFriend.close();

        // 优先读取好友专属 SP，其次读取数据库，兼容旧版 Type=5
        boolean isDnd = SPUtils.getInstance().get("friend_dnd_" + Friend_UID,
                dbMiandarao == 1 || currentType == 5);
        switchDnd.setChecked(isDnd);

        switch (currentType) {
            case 3:
                switchPauseContact.setChecked(true);
                switchPauseContact.setEnabled(true);
                break;
            case 4:
                switchPauseContact.setChecked(true);
                switchPauseContact.setEnabled(false);
                break;
            default:
                switchPauseContact.setChecked(false);
                switchPauseContact.setEnabled(true);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        switchDnd.setOnCheckedChangeListener(null);
        switchPauseContact.setOnCheckedChangeListener(null);
        setFriendType();
        setListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null;
    }
}