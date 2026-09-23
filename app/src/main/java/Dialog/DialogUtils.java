package Dialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;
import com.qapp.midian.activity_friendsetting;

import Data.ChatUtils;
import Data.FriendUtils;
import Utils.FBMessage;

public class DialogUtils {

    // 定义一个请求码，用于在Activity中接收文件选择结果
    public static final int REQUEST_CODE_SELECT_BACKUP_FILE = 999;

    public static void showCustomRemarkDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_friend_remark, null);

        EditText etRemark = view.findViewById(R.id.etRemark);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        // 防空判断
        if (activity_chat.Friend_Nickname != null) {
            etRemark.setText(activity_chat.Friend_Nickname);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String remarkText = etRemark.getText().toString().trim();
            if (!remarkText.isEmpty()) {
                if (Data.MemberBillingBridge.get() == null) {
                    FBMessage.Show(context, "服务未就绪");
                    return;
                }

                Activity act = (context instanceof Activity) ? (Activity) context : null;

                Data.MemberBillingBridge.get().updateFriendRemark(act, App.UID, activity_chat.Friend_UID, remarkText, new Data.IMemberBilling.RemarkCallback() {
                    @Override
                    public void onSuccess() {
                        // 🌟 修复 SQL 注入：使用参数化更新
                        App.db.execSQL(
                                "update friend set _Nickname=? where Friend_UID=?",
                                new Object[]{remarkText, activity_chat.Friend_UID}
                        );

                        activity_chat.Friend_Nickname = remarkText;
                        if (activity_chat.nickname != null) {
                            activity_chat.nickname.setText(remarkText);
                        }
                        FBMessage.Show(context, "备注修改成功");
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        FBMessage.Show(context, errorMessage);
                    }
                });

                dialog.dismiss();
            } else {
                etRemark.setError("备注不能为空");
            }
        });
    }

    public static void showSearchChatHistoryDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_search_chat_history, null);

        EditText etSearchKeyword = view.findViewById(R.id.etSearchKeyword);
        Button btnSearchCancel = view.findViewById(R.id.btnSearchCancel);
        Button btnSearchConfirm = view.findViewById(R.id.btnSearchConfirm);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnSearchCancel.setOnClickListener(v -> dialog.dismiss());

        btnSearchConfirm.setOnClickListener(v -> {
            String keyword = etSearchKeyword.getText().toString().trim();
            if (!keyword.isEmpty()) {
                ChatUtils.GET_LOCA_CHAT(activity_chat.Friend_UID, 0, 1, keyword);
                dialog.dismiss();
            } else {
                etSearchKeyword.setError("搜索内容不能为空");
            }
        });
    }

    public static void showClearChatDialog(Context context, int friend_uid) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_clear_chat_history, null);

        Button btnClearCancel = view.findViewById(R.id.btnClearCancel);
        Button btnClearConfirm = view.findViewById(R.id.btnClearConfirm);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnClearCancel.setOnClickListener(v -> dialog.dismiss());

        btnClearConfirm.setOnClickListener(v -> {
            // 🌟 修复 SQL 注入：使用参数化执行删除
            App.db.execSQL(
                    "DELETE FROM chat where (ToUid=? and FromUid=?) or (ToUid=? and FromUid=?)",
                    new Object[]{friend_uid, App.UID, App.UID, friend_uid}
            );

            SharedPreferences.Editor editor = App.AppContext.getSharedPreferences("setting", Context.MODE_PRIVATE).edit();
            editor.remove("friend_uid");
            editor.remove("friend_userid");
            editor.remove("friend_nickname");
            editor.remove("friend_image");
            editor.apply(); // 使用 apply() 替代过时的 commit() 提升性能

            Log.e("TBA", "已清空本地所有信息");

            if (activity_chat.that != null && !activity_chat.that.isFinishing()) {
                activity_chat.Datas_Chat.clear();
                activity_chat.that.runOnUiThread(() -> {
                    if (activity_chat.mAdapter_Chat != null) {
                        activity_chat.mAdapter_Chat.notifyDataSetChanged();
                    }
                });
            }

            if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
            }

            FBMessage.Show(context, "已清空本地所有信息");
            dialog.dismiss();
        });
    }

    public static void showPauseContactDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pause_contact, null);

        Button btnPauseCancel = view.findViewById(R.id.btnPauseCancel);
        Button btnPauseConfirm = view.findViewById(R.id.btnPauseConfirm);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnPauseCancel.setOnClickListener(v -> {
            if (activity_friendsetting.switchPauseContact != null) {
                activity_friendsetting.switchPauseContact.setChecked(false);
            }
            dialog.dismiss();
        });

        btnPauseConfirm.setOnClickListener(v -> {
            FriendUtils.UpdateType(3, activity_friendsetting.Friend_UID);
            dialog.dismiss();
        });
    }

    public static void showDeleteFriendDialog(Context context, int friend_uid) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_friend, null);

        Button btnDeleteCancel = view.findViewById(R.id.btnDeleteCancel);
        Button btnDeleteConfirm = view.findViewById(R.id.btnDeleteConfirm);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnDeleteCancel.setOnClickListener(v -> dialog.dismiss());

        btnDeleteConfirm.setOnClickListener(v -> {
            FriendUtils.UpdateType(9, friend_uid);
            if (activity_friendsetting.that != null && !activity_friendsetting.that.isFinishing()) {
                activity_friendsetting.that.finish();
            }
            dialog.dismiss();
        });
    }
}