package Data;

import static android.content.Context.VIBRATOR_SERVICE;
import static com.blankj.utilcode.util.ViewUtils.runOnUiThread;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import Utils.Notification;
import Utils.RingUtils;
import Utils.SPUtils;

public class MessageUtils {
    public static MediaPlayer mediaPlayer;
    private static final String TAG = "GROUP_DEBUG";

    /**
     * 离线消息写入服务器结果回调
     */
    public interface OnOfflineBackupCallback {
        void onSuccess();
        void onFailure(String errorMsg);
    }

    @SuppressLint({"Range", "SetTextI18n"})
    public static void ChatMessage(JSONObject jsonObject) {
        try {
            Log.d(TAG, "===> 收到底层待处理原始数据: " + jsonObject.toString());

            String groupId = jsonObject.optString("_GroupId", "");
            boolean isGroup = !groupId.isEmpty();

            int touid = jsonObject.getInt("_ToUid");
            int fromuid = jsonObject.getInt("_FromUID");

            // 1. 群聊归属地转换校验
            if (isGroup) {
                int localFriendUid = 0;

                Cursor cursor = App.db.rawQuery(
                        "SELECT Friend_UID FROM friend WHERE userid=? AND UID=? LIMIT 1",
                        new String[]{groupId, String.valueOf(App.UID)}
                );

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        localFriendUid = cursor.getInt(0);
                    }
                    cursor.close();
                }

                if (localFriendUid == 0) {
                    Cursor c2 = App.db.rawQuery("SELECT Friend_UID FROM friend WHERE userid=? LIMIT 1", new String[]{groupId});
                    if (c2 != null) {
                        if (c2.moveToFirst()) {
                            localFriendUid = c2.getInt(0);
                        }
                        c2.close();
                    }
                }

                if (localFriendUid == 0) {
                    Log.e(TAG, "❌ 本地没有该群记录，触发刷新列表并在1秒后重试该消息: " + groupId);
                    FriendUtils.GET_FRIEND();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        ChatMessage(jsonObject);
                    }, 1200);
                    return;
                }

                touid = localFriendUid;
            }

            // 2. 数据获取与解密
            String message = jsonObject.getString("_Message");
            String yingyong = jsonObject.getString("_Yingyong");
            String MessageId = jsonObject.getString("_MessageId");
            String MediaUrl = jsonObject.getString("_Mediaurl");
            int id = jsonObject.getInt("_ID");
            long time = jsonObject.getLong("_Inputtime");

            try {
                String encryptedMessage = null, encryptedYingyong = "", encryptedMedia = "";
                if (!message.equals("")) {
                    encryptedMessage = AESUtils.decrypt(App.AppContext, message);
                    if (!yingyong.equals("")) encryptedYingyong = AESUtils.decrypt(App.AppContext, yingyong);
                    if (!MediaUrl.equals("")) encryptedMedia = AESUtils.decrypt(App.AppContext, MediaUrl);
                }

                // 3. 落盘判重
                // 🌟 使用参数化查询避免潜在注入
                String sql_new = "select id,MessageId from chat where MessageId=? order by id desc limit 1";
                Cursor cursor_new = App.db.rawQuery(sql_new, new String[]{MessageId});

                if (cursor_new.getCount() == 0) {

                    boolean isCurrentChat = false;
                    if (activity_chat.that != null) {
                        if (isGroup) {
                            isCurrentChat = (activity_chat.Friend_UID == touid);
                        } else {
                            isCurrentChat = (activity_chat.Friend_UID == fromuid || (activity_chat.Friend_UID == touid && fromuid == App.UID));
                        }
                    }

                    int isRead = isCurrentChat ? 3 : 0;

                    // 🌟 修复注入隐患：改用参数化操作将消息落库
                    App.db.execSQL(
                            "insert into chat(MessageId,ToUid,FromUID,Message,Inputtime,IsRead,Yingyong,Mediaurl) values (?,?,?,?,?,?,?,?)",
                            new Object[]{MessageId, touid, fromuid, encryptedMessage, time, isRead, encryptedYingyong, encryptedMedia}
                    );

                    if (isGroup) {
                        App.db.execSQL("update friend set Updatetime=? where Friend_UID=? and UID=?", new Object[]{time, touid, App.UID});
                    } else {
                        App.db.execSQL("update friend set Updatetime=? where Friend_UID=? and UID=?", new Object[]{time, fromuid, App.UID});
                    }

                    JSONObject jo_show = new JSONObject();
                    JSONArray jsonArray = new JSONArray();
                    try {
                        jo_show.put("_MessageId", MessageId);
                        jo_show.put("_Message", encryptedMessage);
                        jo_show.put("_Yingyong", encryptedYingyong);
                        jo_show.put("_Inputtime", time);
                        jo_show.put("_IsRead", 1);
                        jo_show.put("_Mediaurl", encryptedMedia);
                        jo_show.put("_ToUid", touid);
                        jo_show.put("_FromUID", fromuid);
                        jo_show.put("_IsRead", isRead);
                        jo_show.put("_ID", id);
                        jo_show.put("_Progress", 0);
                        jsonArray.put(jo_show);
                    } catch (JSONException e) {
                        Log.e("MSG", "ERROR:" + e.getMessage());
                        throw new RuntimeException(e);
                    }

                    // 查询发信人身份类型
                    int Type = 2;
                    int queryFriendUid = isGroup ? touid : fromuid;
                    String sql = "select * from friend where Friend_UID=? limit 1";
                    Cursor cursor = App.db.rawQuery(sql, new String[]{String.valueOf(queryFriendUid)});
                    if (cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        Type = cursor.getInt(cursor.getColumnIndex("Type"));
                    }
                    cursor.close();


                    // 读取免打扰状态：直接从本地 friend 表中查询 miandarao 字段
                    boolean isDnd = false;
                    try {
                        String dndSql;
                        String[] dndArgs;
                        if (isGroup) {
                            // 群组：根据 group_id 和当前用户的 UID 查询
                            dndSql = "SELECT miandarao FROM friend WHERE userid=? AND UID=? LIMIT 1";
                            dndArgs = new String[]{groupId, String.valueOf(App.UID)};
                        } else {
                            // 单聊好友：根据对方的 Friend_UID 和当前用户的 UID 查询
                            dndSql = "SELECT miandarao FROM friend WHERE Friend_UID=? AND UID=? LIMIT 1";
                            dndArgs = new String[]{String.valueOf(fromuid), String.valueOf(App.UID)};
                        }

                        Cursor dndCursor = App.db.rawQuery(dndSql, dndArgs);
                        if (dndCursor != null) {
                            if (dndCursor.moveToFirst()) {
                                int miandaraoValue = dndCursor.getInt(0);
                                isDnd = (miandaraoValue != 0);
                            }
                            dndCursor.close();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "查询免打扰状态异常", e);
                    }



                    // 5. UI 响应（列表刷新或铃声震动反馈）
                    if (activity_chat.that == null) {
                        ChatUtils.GET_NUMBER();
                        boolean is_invisible = SPUtils.getInstance().get("is_invisible", true);
                        Log.e("isDnd",isDnd+"/"+is_invisible+"/"+Type);
                        // 🌟 若开启免打扰，则不播放提示音与系统通知
                        if (!is_invisible && !isDnd) {
                            PlayMusic();
                        }

                        if (activity_friend.that != null) {
                            FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                        }
                    } else {
                        Vibrator vibrator = (Vibrator) App.AppContext.getSystemService(VIBRATOR_SERVICE);
                        boolean FriendShortVibrate = SPUtils.getInstance().get("FriendShortVibrate", false);

                        // 🌟 免打扰群聊消息不触发振动
                        if (!isDnd && vibrator != null && vibrator.hasVibrator() && FriendShortVibrate) {
                            vibrator.vibrate(50);
                        }

                        if (isCurrentChat) {
                            Log.d(TAG, "消息属于当前界面，直接渲染上屏");
                            activity_chat.ShowChatList(jsonArray.toString(), 1, 1);
                            activity_chat.RecyclerView_Chat.post(() -> {
                                activity_chat.RecyclerView_Chat.smoothScrollToPosition(activity_chat.mAdapter_Chat.getItemCount() - 1);
                            });
                        } else {
                            Log.d(TAG, "消息不属于当前界面，弹出浮动通知");
                            runOnUiThread(() -> {
                                activity_chat.pop.setVisibility(View.VISIBLE);
                                Animation animation = AnimationUtils.loadAnimation(App.AppContext, R.anim.show_in);
                                activity_chat.pop.startAnimation(animation);
                                activity_chat.pop_message.setText(isGroup ? "收到一条新的群聊消息" : "其他好友发来新的消息");

                                int unreadCount = 0;
                                Cursor cursorCount = App.db.rawQuery("select count(*) from chat where isRead=0 and (ToUid=" + App.UID + " or ToUid in (select Friend_UID from friend where Type=30))", null);
                                if (cursorCount != null) {
                                    if (cursorCount.moveToFirst()) unreadCount = cursorCount.getInt(0);
                                    cursorCount.close();
                                }

                                if (unreadCount > 0) {
                                    activity_chat.message_number.setText(String.valueOf(unreadCount));
                                    activity_chat.message_number.setVisibility(View.VISIBLE);
                                }

                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    Animation animation2 = AnimationUtils.loadAnimation(App.AppContext, R.anim.show_out);
                                    activity_chat.pop.startAnimation(animation2);
                                    activity_chat.pop.setVisibility(View.GONE);
                                }, 5000);
                            });
                        }
                    }
                } else {
                    Log.w(TAG, "数据库已存在相同 MessageId，跳过重复写入: " + MessageId);
                }
                cursor_new.close();
            } catch (Exception e) {
                Log.e(TAG, "解密或消息存储异常", e);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析 JSON 数据失败", e);
        }
    }

    public static void GroupMessage(JSONObject data) {
        ChatMessage(data);
    }

    // 原无回调版本（兼容保留）
    public static void SendOffLineMessage(JSONObject data) {
        SendOffLineMessage(data, null);
    }

    /**
     * 写入服务器离线消息接口（带结果回调）
     */
    public static void SendOffLineMessage(JSONObject data, OnOfflineBackupCallback callback) {
        if (MemberBillingBridge.get() == null) {
            if (callback != null) callback.onFailure("Bridge 未就绪");
            return;
        }

        MemberBillingBridge.get().sendOfflineMessage(data, new IMemberBilling.OfflineBackupCallback() {
            @Override
            public void onSuccess() {
                if (callback != null) callback.onSuccess();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (callback != null) callback.onFailure(errorMsg);
            }
        });
    }

    public static void PlayMusic() {
        boolean CloseNotice = SPUtils.getInstance().get("CloseNotice", false);
        if (!CloseNotice) {
            String sql_count = "SELECT COUNT(*) FROM chat WHERE isRead=0 AND FromUid<>" + App.UID +
                    " AND (ToUid=" + App.UID + " OR ToUid IN (SELECT Friend_UID FROM friend WHERE Type=30))";
            int totalUnread = (int) android.database.DatabaseUtils.longForQuery(App.db, sql_count, null);
            if (totalUnread <= 0) totalUnread = 1;

            Notification.NewMessageWithCount(totalUnread);
        }

        boolean OpenSound = SPUtils.getInstance().get("OpenSound", false);
        if (OpenSound && activity_chat.that == null) {
            int _sound = SPUtils.getInstance().get("sound", 1);
            RingUtils.PlayRing(_sound);
        }

        Vibrator vibrator = (Vibrator) App.AppContext.getSystemService(VIBRATOR_SERVICE);
        boolean OpenVibrate = SPUtils.getInstance().get("OpenVibrate", false);
        if (OpenVibrate) {
            boolean ShortVibrate = SPUtils.getInstance().get("ShortVibrate", false);
            if (ShortVibrate) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(50);
                }
            } else {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        long[] timings = new long[]{0, 500, 200, 500};
                        int repeatIndex = -1;
                        VibrationEffect effect = VibrationEffect.createWaveform(timings, repeatIndex);
                        vibrator.vibrate(effect);
                    } else {
                        long[] pattern = new long[]{0, 500, 200, 500};
                        vibrator.vibrate(pattern, -1);
                    }
                }
            }
        }
    }

    /**
     * 定时销毁聊天记录（闭源防破解）
     */
    public static void DeleteForTime() {
        boolean deletetimeOpen = SPUtils.getInstance().get("deletetime_open", false);
        String deleteTime = SPUtils.getInstance().get("setting_deletetime", "0");

        if (MemberBillingBridge.get() != null) {
            boolean isExecuted = MemberBillingBridge.get().checkAndDeleteForTime(App.db, deleteTime, deletetimeOpen);
            if (isExecuted) {
                Calendar current = Calendar.getInstance();
                App.currHour = current.get(Calendar.HOUR_OF_DAY);
            }
        }
    }
}