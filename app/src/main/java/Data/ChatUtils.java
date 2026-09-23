package Data;

import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_daochu;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import Utils.FBMessage;
import Utils.FloatButton;
import Utils.Notification;
import Utils.SPUtils;
import adapter.Module_Chat;

public class ChatUtils {
    public static long ChatPageCount = 0;
    public static int ChatCount = 0;
    public static int DaoruDaochuCount;

    private static final Handler handler_ChatUrils = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                String count = msg.obj.toString();
                if (FloatButton.messageNumber != null) {
                    if (count.equals("0")) {
                        FloatButton.messageNumber.setVisibility(View.GONE);
                    } else {
                        int switch_hidemkey = SPUtils.getInstance().get("switch_hidemkey", 0);
                        if (switch_hidemkey == 0) {
                            FloatButton.messageNumber.setText(count);
                            FloatButton.messageNumber.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        }
    };

    public static void ShowChatActivity(int uid) {
        try {
            String sql = "select * from friend where Friend_UID=" + uid + " order by id asc";
            Cursor cursor = App.db.rawQuery(sql, null);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                String _Nickname = cursor.getString(cursor.getColumnIndexOrThrow("Nickname"));
                int _Friend_UID = cursor.getInt(cursor.getColumnIndexOrThrow("Friend_UID"));
                int _Userid = cursor.getInt(cursor.getColumnIndexOrThrow("UserID"));
                int _Type = cursor.getInt(cursor.getColumnIndexOrThrow("Type"));
                String _Image = cursor.getString(cursor.getColumnIndexOrThrow("Image"));

                SPUtils.getInstance().put("friend_uid", _Friend_UID);
                SPUtils.getInstance().put("friend_userid", _Userid);
                SPUtils.getInstance().put("friend_nickname", _Nickname);
                SPUtils.getInstance().put("friend_image", _Image);
                Intent it = new Intent();
                it.setClass(App.AppContext, activity_chat.class);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Bundle bundle_chat = new Bundle();
                bundle_chat.putInt("userid", _Userid);
                bundle_chat.putInt("uid", _Friend_UID);
                bundle_chat.putInt("type", _Type);

                it.putExtras(bundle_chat);
                App.AppContext.startActivity(it);
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 读取本地聊天记录
    public static String GET_LOCA_CHAT(int fromuid, int type, int page, String keyword) {
        int pagesize = 30;
        App.UID = SPUtils.getInstance().get("uid", 0);
        JSONArray jsonArray = new JSONArray();

        String searchCondition = "";
        if (keyword != null && !keyword.trim().isEmpty()) {
            // 🌟 修复 SQL 注入：过滤单引号转义
            String safeKeyword = keyword.replace("'", "''");
            searchCondition = " and Message LIKE '%" + safeKeyword + "%' ";
        }

        // 查出当前会话是单聊还是群聊
        int chatType = 2; // 默认单聊
        Cursor typeCursor = App.db.rawQuery("select Type from friend where Friend_UID=" + fromuid + " limit 1", null);
        if (typeCursor.moveToFirst()) {
            chatType = typeCursor.getInt(0);
        }
        typeCursor.close();

        // 将超过 1 分钟未发送成功的发出消息置为 6（未发送成功）
        try {
            long nowTime = System.currentTimeMillis();
            App.db.execSQL(
                    "UPDATE chat SET IsRead = 7 WHERE FromUid = ? AND ToUid = ? AND IsRead < 3 AND (? - CAST(Inputtime AS INTEGER)) > 60000",
                    new Object[]{App.UID, fromuid, nowTime}
            );
        } catch (Exception e) {
            Log.e("ChatUtils", "检查并更新发送超时消息失败: " + e.getMessage());
        }

        // 根据类型动态生成基础条件
        String baseCondition;
        if (chatType == 30) {
            baseCondition = " ToUid=" + fromuid + " ";
        } else {
            baseCondition = " ((ToUid=" + App.UID + " and FromUID=" + fromuid + ") or (ToUid=" + fromuid + " and FromUID=" + App.UID + ")) ";
        }

        String sql = "select * from chat where " + baseCondition + " and IsRead=0 " + searchCondition + " order by inputtime desc limit " + pagesize;

        if (type == 0) {
            String countSql = "SELECT COUNT(*) FROM chat WHERE " + baseCondition + searchCondition;
            ChatCount = (int) DatabaseUtils.longForQuery(App.db, countSql, null);

            double p = (double) ChatCount / pagesize;
            ChatPageCount = Math.round(p);
            int offset = (int) Math.ceil((page - 1) * pagesize);

            sql = "select * from chat where " + baseCondition + searchCondition + " order by inputtime desc limit " + pagesize + " OFFSET " + offset;
        }

        if (type == 2) {
            sql = "select * from chat where ToUid=" + App.UID + " and IsRead=0 " + searchCondition + " order by inputtime desc limit " + pagesize;
        }

        Cursor cursor = App.db.rawQuery(sql, null);
        if (cursor.getCount() > 0) {
            if (!activity_chat.Datas_Chat.isEmpty() && page == 1) activity_chat.Datas_Chat.clear();
            while (cursor.moveToNext()) {
                try {
                    String _Key = cursor.getString(cursor.getColumnIndexOrThrow("Msgkey"));
                    String _Message = cursor.getString(cursor.getColumnIndexOrThrow("Message"));
                    String _Yingyong = cursor.getString(cursor.getColumnIndexOrThrow("Yingyong"));
                    int _IsRead = cursor.getInt(cursor.getColumnIndexOrThrow("IsRead"));
                    String _Mediaurl = cursor.getString(cursor.getColumnIndexOrThrow("Mediaurl"));
                    int _FromUID = cursor.getInt(cursor.getColumnIndexOrThrow("FromUid"));
                    String _MessageId = cursor.getString(cursor.getColumnIndexOrThrow("MessageId"));
                    int _ToUid = cursor.getInt(cursor.getColumnIndexOrThrow("ToUid"));
                    int _ID = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    long _Inputtime = cursor.getLong(cursor.getColumnIndexOrThrow("Inputtime"));

                    if (activity_chat.that != null) {
                        App.db.execSQL("update chat set IsRead=1 where IsRead=0 and ToUid=" + fromuid);
                    }

                    JSONObject jo = new JSONObject();
                    jo.put("_MessageId", _MessageId);
                    jo.put("_Message", _Message);
                    jo.put("_Key", _Key);
                    jo.put("_Yingyong", _Yingyong);
                    jo.put("_Mediaurl", _Mediaurl);
                    jo.put("_ToUid", _ToUid);
                    jo.put("_FromUID", _FromUID);
                    jo.put("_Inputtime", _Inputtime);
                    jo.put("_ID", _ID);
                    jo.put("_IsRead", _IsRead);
                    jo.put("_Progress", 0);
                    jsonArray.put(jo);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (activity_chat.that != null && activity_chat.Friend_UID == fromuid) {
                activity_chat.ShowChatList(jsonArray.toString(), 0, page);
            }
        } else {
            Message msg = new Message();
            msg.what = 0;
            msg.obj = 0;
            handler_ChatUrils.sendMessage(msg);
        }
        cursor.close();

        return jsonArray.toString();
    }

    public static void LocaMessage(String message, String yingyong, int Friend_UID, String MediaUrl, boolean ShowLocaList, boolean SendToFriend, String filepath, long time) {
        String SendMessageId = App.UID + "" + time;
        JSONArray jsonArray = new JSONArray();
        JSONObject jo = new JSONObject();
        try {
            jo.put("_MessageId", SendMessageId);
            jo.put("_Message", message);
            jo.put("_Key", "");
            jo.put("_Yingyong", yingyong);
            jo.put("_Mediaurl", MediaUrl);
            jo.put("_ToUid", Friend_UID);
            jo.put("_FromUID", App.UID);
            jo.put("_Inputtime", time);
            jo.put("_ID", 0);
            jo.put("_IsRead", 0);
            jo.put("_Progress", 0);
        } catch (JSONException e) {
            Log.e("MSG", "ERROR:" + e.getMessage());
            throw new RuntimeException(e);
        }
        jsonArray.put(jo);

        if (ShowLocaList) {
            activity_chat.ShowChatList(jsonArray.toString(), 1, 1);
        }
    }

    public static void SendSuccess(JSONObject jsonObject) throws JSONException {
        final String messageid = jsonObject.getString("_MessageId");
        boolean switch_zidongfenhui = SPUtils.getInstance().get("switch_zidongfenhui", false);

        if (!switch_zidongfenhui) {
            // 普通接收
            try {
                App.db.execSQL("update chat set IsRead=3 where MessageId=?", new String[]{messageid});
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 🌟 修复：先检查活动是否依然存活，避免空指针崩溃
            if (activity_chat.that != null && !activity_chat.that.isFinishing()) {
                activity_chat.that.runOnUiThread(() -> {
                    // 防止任务在队列排队期间，Activity恰好被销毁
                    if (activity_chat.that == null || activity_chat.that.isFinishing()) return;
                    if (activity_chat.Datas_Chat == null || activity_chat.mAdapter_Chat == null) return;

                    for (int i = 0; i < activity_chat.Datas_Chat.size(); i++) {
                        Module_Chat item = activity_chat.Datas_Chat.get(i);
                        if (item.getMessageId().equals(messageid)) {
                            activity_chat.Datas_Chat.set(i, new Module_Chat(
                                    item.getID(), item.getMessage(), messageid, item.getUID(), item.getFromUID(),
                                    "", item.getTime() + "", item.getYingyong(), item.getMediaurl(), 3, 0));

                            Bundle payload = new Bundle();
                            payload.putInt("_IsRead", 3);
                            activity_chat.mAdapter_Chat.notifyItemChanged(i, payload);
                            break;
                        }
                    }
                });
            }
        } else {
            // 自动焚毁，不管页面在不在，5秒后都会执行动态查找并删除操作
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                autoDeleteMessageById(messageid);
            }, 5000);
        }
    }

    /**
     * 🌟 安全执行删除操作的保障方法（防止列表越界异常）
     */
    private static void executeFinalDelete(String messageId, int position) {
        if (activity_chat.that == null || activity_chat.that.isFinishing()) return;

        if (activity_chat.Datas_Chat != null && activity_chat.mAdapter_Chat != null) {
            if (position >= 0 && position < activity_chat.Datas_Chat.size()) {
                // 再次双重校验索引处的ID，防止被下拉刷新的动作错乱
                if (activity_chat.Datas_Chat.get(position).getMessageId().equals(messageId)) {
                    activity_chat.Datas_Chat.remove(position);
                    activity_chat.mAdapter_Chat.notifyItemRemoved(position);
                    int itemCountAfterDelete = activity_chat.Datas_Chat.size() - position;
                    activity_chat.mAdapter_Chat.notifyItemRangeChanged(position, itemCountAfterDelete);
                }
            }
        }
    }

    public static void GET_NUMBER() {
        String sql_count = "SELECT COUNT(*) FROM chat WHERE isRead=0 AND FromUid<>" + App.UID +
                " AND (ToUid=" + App.UID + " OR ToUid IN (SELECT Friend_UID FROM friend WHERE Type=30))";

        int unreadTotal = (int) DatabaseUtils.longForQuery(App.db, sql_count, null);

        if (unreadTotal > 0) {
            Message msg = new Message();
            msg.what = 0;
            msg.obj = String.valueOf(unreadTotal);
            handler_ChatUrils.sendMessage(msg);

            Notification.NewMessageWithCount(unreadTotal);
        } else {
            Message msg = new Message();
            msg.what = 0;
            msg.obj = "0";
            handler_ChatUrils.sendMessage(msg);

            Notification.RemoveMessage();
        }
    }

    /**
     * 🌟 安全删除单条数据（涵盖了即使界面已经被销毁，数据库仍旧能删除的强力修复）
     */
    public static void autoDeleteMessageById(final String messageId) {
        // 1. 永远先清理数据库内容，保证数据底层的纯净性（如果界面没了，这步是兜底）
        new Thread(() -> {
            try {
                App.db.execSQL("DELETE FROM chat WHERE MessageId=?", new String[]{messageId});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 2. 如果 Activity 已经被系统或用户销毁，直接停止下面的动画及UI渲染逻辑
        if (activity_chat.that == null || activity_chat.that.isFinishing()) return;

        activity_chat.that.runOnUiThread(() -> {
            // 防止抛到主线程队列时状态发生改变
            if (activity_chat.that == null || activity_chat.that.isFinishing()) return;
            if (activity_chat.Datas_Chat == null || activity_chat.mAdapter_Chat == null) return;
            if (activity_chat.RecyclerView_Chat == null) return;

            int currentPosition = -1;
            for (int k = 0; k < activity_chat.Datas_Chat.size(); k++) {
                if (messageId.equals(activity_chat.Datas_Chat.get(k).getMessageId())) {
                    currentPosition = k;
                    break;
                }
            }

            if (currentPosition != -1) {
                final int finalPosition = currentPosition;
                try {
                    androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder =
                            activity_chat.RecyclerView_Chat.findViewHolderForAdapterPosition(finalPosition);

                    if (viewHolder != null) {
                        View itemView = viewHolder.itemView;
                        itemView.animate()
                                .scaleX(1.4f)
                                .scaleY(1.4f)
                                .alpha(0f)
                                .setDuration(450)
                                .withEndAction(() -> {
                                    executeFinalDelete(messageId, finalPosition);
                                    if (itemView != null) {
                                        itemView.setScaleX(1.0f);
                                        itemView.setScaleY(1.0f);
                                        itemView.setAlpha(1.0f);
                                    }
                                }).start();
                    } else {
                        executeFinalDelete(messageId, finalPosition);
                    }
                } catch (Exception e) {
                    executeFinalDelete(messageId, finalPosition);
                }
            }
        });
    }

    /**
     * 🌟 强制将记录导出动作下沉到 AAR 商业底层进行闭环 VIP 验证
     */
    public static void Daochu_Chat(int Friend_UID, String Friend_Nickname) {
        if (MemberBillingBridge.get() == null) {
            FBMessage.Show(App.AppContext, "安全核心服务未就绪");
            return;
        }

        // 此处通常作为外部零散调用的兜底，将导出权限严格委托回 AAR
        MemberBillingBridge.get().exportChatHistory(
                activity_chat.that, // 优先选用当前可能存活的会话 Activity
                App.UID,
                Friend_UID,
                Friend_Nickname,
                App.db,
                new IMemberBilling.ExportChatCallback() {

                    public void onProgress(int current, int total) {}

                    @Override
                    public void onProgress(int progress) {}

                    @Override
                    public void onSuccess(String exportFilePath) {
                        FBMessage.Show(App.AppContext, "导出成功：" + exportFilePath);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if ("VIP_REQUIRED".equals(errorMessage) && activity_chat.that != null) {
                            MemberBillingBridge.get().showSubscriptionDialog(activity_chat.that);
                        } else {
                            FBMessage.Show(App.AppContext, errorMessage != null ? errorMessage : "导出失败");
                        }
                    }
                }
        );
    }

    /**
     * 导入备份记录
     */
    public static void Daoru_chat(String filename) {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filename);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            String _content = content.toString();
            String[] parts = _content.split("\n");
            if (parts.length < 3) return;

            String key = parts[0];
            String json = parts[2];
            String message = "";

            try {
                if (key != null && !key.isEmpty()) {
                    message = AESUtils.decrypt(App.AppContext,json);

                    JSONArray jsonArray = new JSONArray(message);
                    DaoruDaochuCount = jsonArray.length();
                    if (jsonArray.length() > 0) {
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String MessageId = jsonObject.getString("MessageId");
                            String _Message = jsonObject.getString("Message");
                            String _Key = jsonObject.getString("Msgkey");
                            String _Yingyong = jsonObject.getString("Yingyong");
                            String _Mediaurl = jsonObject.getString("Mediaurl");
                            int _Inputtime = jsonObject.getInt("Inputtime");
                            int _FromeUid = jsonObject.getInt("FromUid");
                            int _ToUid = jsonObject.getInt("ToUid");

                            // 🌟 修复 SQL 注入：使用参数化查询与安全的占位符插入
                            String sql = "select MessageId from chat where MessageId=?";
                            Cursor cursor = App.db.rawQuery(sql, new String[]{MessageId});
                            if (cursor.getCount() == 0) {
                                App.db.execSQL(
                                        "insert into chat(MessageId,ToUid,FromUid,Message,Inputtime,IsRead,Yingyong,Mediaurl,Msgkey) values (?,?,?,?,?,0,?,?,?)",
                                        new Object[]{MessageId, _ToUid, _FromeUid, _Message, _Inputtime, _Yingyong, _Mediaurl, _Key}
                                );
                            }
                            cursor.close();

                            if (activity_daochu.downloadHandler != null) {
                                Message msg = new Message();
                                msg.what = 1;
                                msg.obj = i + "";
                                activity_daochu.downloadHandler.sendMessage(msg);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}