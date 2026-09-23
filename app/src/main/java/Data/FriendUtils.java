package Data;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Handler;
import android.os.Looper;

import com.qapp.midian.App;

import Utils.FBMessage;
import adapter.Module_Friend;

import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;
import com.qapp.midian.activity_main;
import com.qapp.midian.activity_search_network;
import com.qapp.midian.activity_user_profile;

import org.json.JSONArray;

import java.io.File;

import socket.AutoReconnectWebSocket;

public class FriendUtils {
    // 获取本地数据库中的好友
    public static String GET_LOCATION_FRIEND(activity_friend that) {
        // ✨【防空修复】如果传入的 activity 实例已经为 null，或者界面正在销毁，直接拦截返回
        if (that == null || that.isFinishing() || that.isDestroyed()) {
            return new JSONArray().toString();
        }

        if (that.originalDatas != null) {
            that.originalDatas.clear();
        }

        JSONArray jsonArray = new JSONArray();
        String sql = "select * from friend where UID=" + App.UID + " and (type<=5 or type=30) order by Updatetime desc, type asc,nickname asc limit 200";
        if (App.isErrorPassword) sql = "select * from friend where UID=0 order by Updatetime desc, type asc,nickname asc limit 200";
        Cursor cursor = App.db.rawQuery(sql, null);
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                int _UID = cursor.getInt(cursor.getColumnIndexOrThrow("UID"));
                int _Type = cursor.getInt(cursor.getColumnIndexOrThrow("Type"));
                long _UpdateTime = cursor.getLong(cursor.getColumnIndexOrThrow("UpdateTime"));
                int _FromUID = cursor.getInt(cursor.getColumnIndexOrThrow("Friend_UID"));
                String _UserID = cursor.getString(cursor.getColumnIndexOrThrow("UserID"));
                String Image = cursor.getString(cursor.getColumnIndexOrThrow("Image"));
                String ImageUrl = cursor.getString(cursor.getColumnIndexOrThrow("ImageUrl"));

                if (ImageUrl != null && ImageUrl.startsWith("/uploads/")) {
                    ImageUrl = App.DataServiceUrl + ImageUrl;
                }

                // 静默检查并下载头像
                checkAndDownloadAvatar(that, _FromUID, Image, ImageUrl);

                // ==================== ✨ 核心修复：区分群聊和单聊的未读数与最新消息查询 ====================
                long readcount = 0;
                String sql_content;

                if (_Type == 30) {
                    // 群聊逻辑：所有消息的 ToUid 都是群组ID(_FromUID)，未读消息排除自己发的
                    readcount = DatabaseUtils.longForQuery(App.db, "SELECT COUNT(*) FROM chat WHERE isRead=0 and ToUid=" + _FromUID + " and FromUid<>" + App.UID, null);
                    sql_content = "select inputtime,Message,MessageId,Mediaurl,FromUid,isRead from chat where ToUid=" + _FromUID + " order by inputtime desc limit 1";
                } else {
                    // 单聊逻辑：ToUid是自己，FromUid是对方
                    readcount = DatabaseUtils.longForQuery(App.db, "SELECT COUNT(*) FROM chat WHERE isRead=0 and ToUid=" + App.UID + " and FromUid=" + _FromUID, null);
                    sql_content = "select inputtime,Message,MessageId,Mediaurl,FromUid,isRead from chat where (FromUid=" + _FromUID + " and ToUid=" + App.UID + ") or (FromUid=" + App.UID + " and ToUid=" + _FromUID + ") order by inputtime desc limit 1";
                }
                // =========================================================================================

                App.db.execSQL("update friend set MessageNumber=" + readcount + " where Friend_UID=" + _FromUID);

                String nickname = cursor.getString(cursor.getColumnIndexOrThrow("Nickname"));
                String _nickname = cursor.getString(cursor.getColumnIndexOrThrow("_Nickname"));
                if (_nickname == null || _nickname.equals("null")) _nickname = nickname;

                String content = "";
                String mediaurl = "";

                Cursor cursor_content = App.db.rawQuery(sql_content, null);
                if (cursor_content.getCount() > 0) {
                    cursor_content.moveToFirst();
                    content = cursor_content.getString(cursor_content.getColumnIndexOrThrow("Message"));
                    mediaurl = cursor_content.getString(cursor_content.getColumnIndexOrThrow("Mediaurl"));
                    if (mediaurl != null && mediaurl.length() > 0) {
                        content = "图片或视频";
                    }
                    int _Read = cursor_content.getInt(cursor_content.getColumnIndexOrThrow("IsRead"));
                    if (_Read == 5) {
                        content = "消息已撤回";
                    }
                } else {
                    content = "-";
                }
                cursor_content.close();

                // ✨【防空修复】添加数据前再次确保 originalDatas 容器依然可用
                if (that.originalDatas != null) {
                    that.originalDatas.add(new Module_Friend(_UID, _FromUID, content, _UserID, _nickname, cursor.getInt(cursor.getColumnIndexOrThrow("Type")), cursor.getString(cursor.getColumnIndexOrThrow("Image")), (int) readcount, _UpdateTime, cursor.getString(cursor.getColumnIndexOrThrow("ImageUrl"))));
                }
            }
            if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                activity_friend.that.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity_friend.that != null) {
                            activity_friend.that.applySearchFilter();
                        }
                    }
                });
            }
        } else {
            if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                activity_friend.that.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity_friend.that != null && activity_friend.that.originalDatas != null) {
                            activity_friend.that.originalDatas.clear();
                            activity_friend.that.applySearchFilter();
                        }
                    }
                });
            }
        }
        cursor.close();
        return jsonArray.toString();
    }

    /**
     * 检查本地头像文件是否存在，若不存在则利用 Glide 在后台线程静默下载并同步回本地目录与数据库
     */
    private static void checkAndDownloadAvatar(final Context context, final int friendUid, final String localImageName, final String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.trim().isEmpty() || imageUrl.equals("null") || !imageUrl.contains("http")) {
            return;
        }

        String avatarDir = App.Folder + "/Image";
        File dir = new File(avatarDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 根据 URL 生成唯一的文件名，防止缓存冲突
        String fileName;
        if (localImageName == null || localImageName.isEmpty() || localImageName.equals("null") || localImageName.contains("http")) {
            fileName = "avatar_" + friendUid + "_" + Math.abs(imageUrl.hashCode()) + ".jpg";
        } else {
            fileName = new File(localImageName).getName();
        }

        final File localFile = new File(dir, fileName);

        // 如果本地文件已存在且有效，则跳过下载
        if (localFile.exists() && localFile.length() > 0) {
            return;
        }

        new Thread(() -> {
            try {
                File cacheFile = com.bumptech.glide.Glide.with(context.getApplicationContext())
                        .asFile()
                        .load(imageUrl)
                        .submit()
                        .get();

                if (cacheFile != null && cacheFile.exists()) {
                    com.blankj.utilcode.util.FileUtils.copy(cacheFile, localFile);

                    // 🌟 核心修复点：这里必须存入 localFile.getAbsolutePath() 完整的绝对路径，而不能只存 fileName！
                    App.db.execSQL("update friend set Image=? where Friend_UID=?", new Object[]{localFile.getAbsolutePath(), friendUid});

                    // 通知列表刷新显示最新本地缓存
                    if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                        activity_friend.that.runOnUiThread(() -> {
                            if (activity_friend.that != null) {
                                activity_friend.that.applySearchFilter();
                            }
                        });
                    }

                    // 通知聊天界面刷新显示最新本地缓存
                    if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed() && activity_chat.Friend_UID == friendUid) {
                        activity_chat.that.runOnUiThread(() -> {
                            if (activity_chat.mAdapter_Chat != null) {
                                activity_chat.mAdapter_Chat.notifyDataSetChanged();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("AvatarDownload", "Glide 后台下载头像失败, FriendUID: " + friendUid, e);
            }
        }).start();
    }
    // 获取网络数据库中的好友
    public static void GET_FRIEND() {
        GET_FRIEND(null);
    }

    // 新增/确认包含可传回调的重载
    public static void GET_FRIEND(Runnable onFinish) {
        if (App.UID == 0) return;
        if (MemberBillingBridge.get() != null) {
            MemberBillingBridge.get().syncFriends(activity_main.that, App.UID, App.db, App.Folder, () -> {
                // AAR 将网络好友和群落盘完成后，先执行默认的本地列表刷新
                if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                    activity_friend.that.runOnUiThread(() -> {
                        FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                    });
                }
                // 再执行自定义的后置回调
                if (onFinish != null) {
                    onFinish.run();
                }
            });
        }
    }

    // 供外部调用切换置顶状态
    public static void UpdateFriendTop(int friendUid, int isTop) {
        try {
            App.db.execSQL("UPDATE friend SET IsTop = ? WHERE Friend_UID = ? AND UID = ?",
                    new Object[]{isTop, friendUid, App.UID});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void UpdateType(int type, int friendId) {
        if (MemberBillingBridge.get() == null) return;

        MemberBillingBridge.get().updateFriendType(App.UID, friendId, type, App.db, new IMemberBilling.ActionCallback() {
            @Override
            public void onSocketNotify(String socketJson) {
                if (AutoReconnectWebSocket.socket != null) {
                    AutoReconnectWebSocket.socket.sendMessage(socketJson);
                }
            }

            @Override
            public void onUiRefresh() {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // 🌟 核心修复点 1：确保本地数据库显式锁定为设置的目标 type（防止被覆盖）
                    App.db.execSQL("UPDATE friend SET Type = ? WHERE Friend_UID = ? AND UID = ?",
                            new Object[]{type, friendId, App.UID});

                    if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed()) {
                        activity_chat.setFriendType();
                        ChatUtils.GET_LOCA_CHAT(activity_chat.Friend_UID, 0, 1, null);
                    }
                    if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                        GET_LOCATION_FRIEND(activity_friend.that);
                    }
                });
            }

            @Override
            public void onToast(String message) {
            }
        });
    }

    public static void AddFriend(String number, int uid) {
        if (MemberBillingBridge.get() == null) return;

        MemberBillingBridge.get().addFriend(App.UID, number, uid, App.db, new IMemberBilling.ActionCallback() {
            @Override
            public void onSocketNotify(String socketJson) {
                if (AutoReconnectWebSocket.socket != null) {
                    AutoReconnectWebSocket.socket.sendMessage(socketJson);
                }
            }

            @Override
            public void onUiRefresh() {
                new Handler(Looper.getMainLooper()).post(FriendUtils::GET_FRIEND);
            }

            @Override
            public void onToast(String message) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity_user_profile.that != null && !activity_user_profile.that.isFinishing()) {
                        FBMessage.Show(activity_user_profile.that, message);
                    } else if (activity_search_network.that != null && !activity_search_network.that.isFinishing()) {
                        FBMessage.Show(activity_search_network.that, message);
                    } else if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                        FBMessage.Show(activity_friend.that, message);
                    }
                });
            }
        });
    }
}