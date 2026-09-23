package com.qapp.midian;

import static Emoji.ApngExpressionParser.EMOJI_MAP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.blankj.utilcode.util.PermissionUtils;
import com.qapp.commercial_auth.AESUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import Data.ChatUtils;
import Emoji.ApngExpressionParser;
import Emoji.ApngTextView;
import Utils.ApngEmojiSpan;
import Utils.AudioRecorderUtils;
import Utils.FBMessage;
import Utils.FBUploadMedia;
import Utils.FlipDetector;
import Utils.Notification;
import Utils.RecycleViewDivider;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import Utils.StringUtils;
import Utils.UriToPathUtil;
import adapter.Adapter_Chat;
import adapter.Module_Chat;
import adapter.Module_em;
import adapter.Adapter_em;
import io.reactivex.annotations.NonNull;
import jp.wasabeef.recyclerview.animators.FlipInTopXAnimator;
import socket.AutoReconnectWebSocket;

public class activity_chat extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    public static activity_chat that = null;
    public static int Friend_UID = 0;
    public static TextView nettis;
    public static String Friend_Nickname = "", Friend_Image;
    private static int Type = 0;

    public static List<Module_em> mDatas_em;
    public static Adapter_em mAdapterEm, mAdapterEm_user;
    public static List<Module_em> mDatas_em_user;

    public static RecyclerView RecyclerView_Chat = null;
    public static List<Module_Chat> Datas_Chat;
    public static Adapter_Chat mAdapter_Chat;

    public static String SendMessageId = null;
    public static TextView txtYingyong = null;
    public static LinearLayout YingyongBox = null, chat_topbox;
    public static EditText edit_message = null;
    public static TextView message_number;
    public static ApngTextView txtYuyin;

    private static ImageView btn_send;
    public static RelativeLayout morebox = null;
    public static LinearLayout top_tis, upload_zhaopian, upload_shiping, upload_paizhao, upload_luxiang, upload_weizhi, chat_inputbox = null;
    public static RecyclerView embox = null;

    private RecyclerView emuser = null;
    public static int ShowChatBox = 0;
    private static LinearLayout btn_sendBox = null;
    public static FrameLayout messageZy = null;

    private int page = 1;
    public static File CurrMediaFile = null;
    private ImageView btn_close_yingyong;
    public static SwipeRefreshLayout mMainRefresh;

    private boolean isRefresh = false;
    private boolean isSendMessage = false;
    private String currentPhotoPath;
    public static int RecorderDuration = 0;
    private static LinearLayoutManager layoutManager;

    public static LinearLayout pop;
    public static TextView pop_message;
    public static TextView nickname;

    public static final ConcurrentHashMap<String, Long> pendingMessages = new ConcurrentHashMap<>();
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static Switch btnMiandarao, top_zanting;

    private PopupWindow globalEmPopupWindow = null;

    public static String Friend_UserID = "";

    public static boolean isGroupOwner = false;
    private FlipDetector flipDetector;

    public static long pendingTargetMsgId = -1;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleScrollToTargetMessage(intent);
    }

    @SuppressLint({"Range", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_chat);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);
        that = this;

        flipDetector = new FlipDetector(this, new FlipDetector.OnFlipListener() {
            @Override
            public void onFlipped() {
                FlipDetector.returnToHome(that);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Friend_UID = bundle.getInt("uid");
            if (Friend_UID == 0) {
                Friend_UID = bundle.getInt("friend_uid", 0);
            }
        }
        if (Friend_UID == 0) {
            Friend_UID = intent.getIntExtra("friend_uid", 0);
        }

        long targetId = intent.getLongExtra("target_msg_id", -1);
        if (targetId != -1) {
            pendingTargetMsgId = targetId;
        }

        if (Friend_UID == 0) {
            Log.e("ChatActivity", "致命错误：Friend_UID 丢失，无法初始化聊天界面");
            runOnUiThread(() -> {
                FBMessage.Show(this, getString(R.string.chat_session_invalid));
                finish();
            });
            return;
        }

        Vibrator vibrator = (Vibrator) App.AppContext.getSystemService(VIBRATOR_SERVICE);

        chat_topbox = findViewById(R.id.chat_topbox);
        pop = findViewById(R.id.pop);
        pop_message = findViewById(R.id.pop_message);
        message_number = findViewById(R.id.message_number);
        top_tis = findViewById(R.id.top_tis);
        chat_inputbox = findViewById(R.id.chat_inputbox);
        btn_send = findViewById(R.id.btn_send);
        btn_sendBox = findViewById(R.id.btn_sendBox);
        messageZy = findViewById(R.id.messageZy);
        YingyongBox = findViewById(R.id.YingyongBox);
        btn_close_yingyong = findViewById(R.id.btn_close_yingying);
        txtYingyong = findViewById(R.id.txtYingyong);
        mMainRefresh = findViewById(R.id.swipeRefreshLayout);
        mMainRefresh.setOnRefreshListener(that);
        nickname = findViewById(R.id.nickname);
        txtYuyin = findViewById(R.id.txtYuyin);
        nettis = findViewById(R.id.nettis);

        ImageView btnYuyun = findViewById(R.id.btnyuyin);
        btnYuyun.setOnClickListener(v -> {
            boolean isVoiceHidden = txtYuyin.getVisibility() == View.GONE;
            txtYuyin.setVisibility(isVoiceHidden ? View.VISIBLE : View.GONE);
            edit_message.setVisibility(isVoiceHidden ? View.GONE : View.VISIBLE);
        });

        txtYuyin.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (vibrator != null) vibrator.vibrate(100);
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    PermissionUtils.permission(Manifest.permission.RECORD_AUDIO)
                            .callback(new PermissionUtils.SimpleCallback() {
                                @Override public void onGranted() {
                                    try { AudioRecorderUtils.startRecord(); } catch (Exception ignored) { FBMessage.Show(that, getString(R.string.chat_record_permission_error)); }
                                }
                                @Override public void onDenied() { FBMessage.Show(that, getString(R.string.chat_record_permission_denied)); }
                            }).request();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (AudioRecorderUtils.mMediaRecorder != null) AudioRecorderUtils.stopRecord();
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });

        edit_message = findViewById(R.id.edit_message);
        edit_message.setOnKeyListener((v, keyCode, event) -> {
            boolean enterkey_sendmessage = SPUtils.getInstance().get("enterkey_sendmessage", false);
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER && enterkey_sendmessage && !isSendMessage) {
                String message = edit_message.getText().toString().trim();
                if (message.isEmpty()) { FBMessage.Show(that, getString(R.string.chat_empty_msg_warning)); return false; }

                if (Type != 2 && Type != 30) {
                    FBMessage.Show(that, getString(R.string.chat_not_friend_or_in_group));
                    return false;
                }
                SendMessage(message);
                return true;
            }
            return false;
        });

        RecyclerView_Chat = findViewById(R.id.recycler_chat_list);
        layoutManager = new LinearLayoutManager(that);
        layoutManager.setStackFromEnd(true);

        RecyclerView_Chat.setLayoutManager(layoutManager);

        Datas_Chat = new ArrayList<>();
        mAdapter_Chat = new Adapter_Chat(Datas_Chat);
        RecyclerView_Chat.setAdapter(mAdapter_Chat);
        RecyclerView_Chat.setItemAnimator(new FlipInTopXAnimator());
        RecyclerView_Chat.getItemAnimator().setRemoveDuration(500);

        edit_message.setOnClickListener(v -> {
            if (SPUtils.getInstance().get("switch_hidechat", false)) {
                messageZy.setVisibility(View.VISIBLE);
                chat_topbox.setVisibility(View.GONE);
                ShowChatBox++;
            }
        });

        edit_message.setOnFocusChangeListener((v, hasFocus) -> {
            boolean hidechat = SPUtils.getInstance().get("switch_hidechat", false);
            messageZy.setVisibility((hasFocus && hidechat) ? View.VISIBLE : View.GONE);
            chat_topbox.setVisibility((hasFocus && hidechat) ? View.GONE : View.VISIBLE);
        });

        messageZy.setOnClickListener(v -> {
            messageZy.setVisibility(View.GONE);
            chat_topbox.setVisibility(View.VISIBLE);
        });

        edit_message.addTextChangedListener(new TextWatcher() {
            private ApngEmojiSpan[] oldSpans;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s instanceof Spanned) {
                    oldSpans = ((Spanned) s).getSpans(0, s.length(), ApngEmojiSpan.class);
                }
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String t = edit_message.getText().toString();
                btn_sendBox.setVisibility(!t.isEmpty() ? View.VISIBLE : View.GONE);
                if (!t.isEmpty()) {
                    new Thread(() -> App.db.execSQL("update friend set Content=? where Friend_UID=?", new Object[]{t, Friend_UID})).start();
                }
            }
            @Override public void afterTextChanged(Editable s) {
                if (oldSpans != null) {
                    ApngEmojiSpan[] newSpans = s.getSpans(0, s.length(), ApngEmojiSpan.class);
                    for (ApngEmojiSpan oldSpan : oldSpans) {
                        boolean found = false;
                        for (ApngEmojiSpan newSpan : newSpans) {
                            if (oldSpan == newSpan) { found = true; break; }
                        }
                        if (!found) {
                            Drawable d = oldSpan.getDrawable();
                            if (d instanceof com.github.penfeizhou.animation.apng.APNGDrawable) {
                                ((com.github.penfeizhou.animation.apng.APNGDrawable) d).stop();
                            }
                        }
                    }
                }
            }
        });

        btn_send.setOnClickListener(view -> {
            String message = edit_message.getText().toString().trim();
            if (message.isEmpty()) { FBMessage.Show(that, getString(R.string.chat_empty_msg_warning)); return; }
            SendMessage(message);
        });

        ImageView btn_em = findViewById(R.id.btn_em);
        btn_em.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) that.getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(that.getWindow().getDecorView().getWindowToken(), 0);

            if (globalEmPopupWindow == null) {
                View popupView = LayoutInflater.from(that).inflate(R.layout.popup_chat_em, null);
                int heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 440, getResources().getDisplayMetrics());
                globalEmPopupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, heightPx, true);

                popupView.findViewById(R.id.upload_zhaopian).setOnClickListener(it -> { UploadFile(0); globalEmPopupWindow.dismiss(); });
                popupView.findViewById(R.id.upload_paizhao).setOnClickListener(it -> { UploadFile(2); globalEmPopupWindow.dismiss(); });
                popupView.findViewById(R.id.upload_luxiang).setOnClickListener(it -> { UploadFile(3); globalEmPopupWindow.dismiss(); });
                popupView.findViewById(R.id.upload_weizhi).setOnClickListener(it -> { UploadFile(4); globalEmPopupWindow.dismiss(); });

                embox = popupView.findViewById(R.id.embox);
                GridLayoutManager manager = new GridLayoutManager(that, 6);
                embox.setLayoutManager(manager);
                embox.addItemDecoration(new RecycleViewDivider(LinearLayoutManager.VERTICAL, 0, getResources().getColor(R.color.white)));

                mDatas_em = new ArrayList<>();
                EMOJI_MAP.forEach((key, value) -> mDatas_em.add(new Module_em(key)));
                mAdapterEm = new Adapter_em(that, edit_message, mDatas_em);
                embox.setAdapter(mAdapterEm);
                ((SimpleItemAnimator) embox.getItemAnimator()).setSupportsChangeAnimations(false);

                emuser = popupView.findViewById(R.id.em_user);
                emuser.setLayoutManager(new GridLayoutManager(that, 6));
                emuser.addItemDecoration(new RecycleViewDivider(LinearLayoutManager.VERTICAL, 0, getResources().getColor(R.color.white)));

                mDatas_em_user = new ArrayList<>();
                mAdapterEm_user = new Adapter_em(activity_chat.this, edit_message, mDatas_em_user);
                emuser.setAdapter(mAdapterEm_user);
                ((SimpleItemAnimator) emuser.getItemAnimator()).setSupportsChangeAnimations(false);

                globalEmPopupWindow.setOutsideTouchable(true);
                globalEmPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            if (mDatas_em_user != null) {
                for (Module_em oldEm : mDatas_em_user) {
                    ApngExpressionParser.stopAnimators(oldEm.getKey());
                }
                mDatas_em_user.clear();
                Cursor cursor_em = App.db.rawQuery("select * from em order by UpdateTime desc Limit 6", null);
                if (cursor_em.getCount() > 0) {
                    while (cursor_em.moveToNext()) {
                        mDatas_em_user.add(new Module_em(cursor_em.getString(cursor_em.getColumnIndex("Em"))));
                    }
                }
                cursor_em.close();
                if (mAdapterEm_user != null) mAdapterEm_user.notifyDataSetChanged();
            }

            int popupHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -30, getResources().getDisplayMetrics());
            globalEmPopupWindow.showAsDropDown(btn_em, 0, -(btn_em.getHeight() + popupHeightPx));
        });

        FrameLayout btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> {
            if (Type == 30) {
                Intent it = new Intent(that, activity_group_profile.class);
                it.putExtra("group_id", Friend_UserID);
                it.putExtra("local_friend_uid", Friend_UID);
                that.startActivity(it);
            } else {
                Intent it = new Intent();
                it.setClass(App.AppContext, activity_friendsetting.class);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Bundle bundle_chat = new Bundle();
                bundle_chat.putInt("friend_uid", Friend_UID);
                it.putExtras(bundle_chat);
                App.AppContext.startActivity(it);
            }
        });

        btn_close_yingyong = findViewById(R.id.btn_close_yingying);
        btn_close_yingyong.setOnClickListener(v -> {
            YingyongBox.setVisibility(View.GONE);
            txtYingyong.setText("");
        });

        Cursor cursor = App.db.rawQuery("select * from friend where Friend_UID=" + Friend_UID + " limit 1", null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            String _nickname2 = cursor.getString(cursor.getColumnIndex("_Nickname"));
            Friend_Image = cursor.getString(cursor.getColumnIndex("Image"));
            Type = cursor.getInt(cursor.getColumnIndex("Type"));

            if (_nickname2 == null || _nickname2.equals("null") || _nickname2.isEmpty()) {
                _nickname2 = cursor.getString(cursor.getColumnIndex("Nickname"));
            }
            nickname.setText(_nickname2);
            Friend_Nickname = _nickname2;
            edit_message.setText(cursor.getString(cursor.getColumnIndex("Content")));
        }
        cursor.close();
        ChatUtils.GET_LOCA_CHAT(Friend_UID, 0, page, null);
    }

    public void handleScrollToTargetMessage(Intent intent) {
        if (intent == null) return;
        long targetMsgId = intent.getLongExtra("target_msg_id", -1);
        if (targetMsgId == -1) return;

        intent.removeExtra("target_msg_id");
        pendingTargetMsgId = targetMsgId;

        scrollToTargetPositionIfExist();
    }

    public static void scrollToTargetPositionIfExist() {
        if (pendingTargetMsgId == -1 || that == null || RecyclerView_Chat == null || Datas_Chat == null) {
            return;
        }

        that.runOnUiThread(() -> {
            int targetPos = -1;
            for (int i = 0; i < Datas_Chat.size(); i++) {
                Module_Chat item = Datas_Chat.get(i);
                if (item != null && item.getID() == pendingTargetMsgId) {
                    targetPos = i;
                    break;
                }
            }

            if (targetPos != -1) {
                final int pos = targetPos;
                RecyclerView_Chat.post(() -> {
                    if (layoutManager != null) {
                        layoutManager.scrollToPositionWithOffset(pos, 80);
                    } else {
                        RecyclerView_Chat.scrollToPosition(pos);
                    }
                    pendingTargetMsgId = -1;
                });
            } else {
                loadChatHistoryToTarget(pendingTargetMsgId);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null;

        AutoReconnectWebSocket.isGroupChat = false;
        AutoReconnectWebSocket.currentGroupId = "";
        isGroupOwner = false;

        adapter.Adapter_Chat.releasePlayer();
        if (globalEmPopupWindow != null) {
            globalEmPopupWindow.dismiss();
            globalEmPopupWindow = null;
        }
        if (SPUtils.getInstance().get("switch_backdelete", false)) {
            new Thread(() -> App.db.execSQL("DELETE FROM chat where (ToUid=" + Friend_UID + " and FromUid=" + App.UID + ") or (ToUid=" + App.UID + " and FromUid=" + Friend_UID + ")")).start();
        }
    }

    @Override
    @SuppressLint("Range")
    protected void onResume() {
        super.onResume();
        that = this;

        boolean switch_sensorlock = SPUtils.getInstance().get("switch_sensorlock", false);
        if (flipDetector != null && switch_sensorlock) {
            flipDetector.start();
        }

        setFriendType();
        try {
            // 🌟 修复: 计算未读消息红点时，兼容群聊。群聊的未读也存在 MessageNumber 里，或者根据 ToUid=Friend_UID 判断
            Cursor cursor_count;
            if (Type == 30) {
                cursor_count = App.db.rawQuery("select * from chat where isRead=0 and ToUid=" + Friend_UID + " and FromUid<>" + App.UID, null);
            } else {
                cursor_count = App.db.rawQuery("select * from chat where isRead=0 and ToUid=" + App.UID + " and FromUid<>" + Friend_UID + " and FromUid<>" + App.UID, null);
            }

            if (cursor_count.getCount() == 0) {
                Notification.RemoveMessage();
            } else {
                message_number.setText(String.valueOf(cursor_count.getCount()));
                message_number.setVisibility(View.VISIBLE);
            }
            cursor_count.close();

            // 如果当前界面有新消息，刷新一下
            Cursor cursor_newMessage;
            if (Type == 30) {
                cursor_newMessage = App.db.rawQuery("select * from chat where isRead=0 and ToUid=" + Friend_UID, null);
            } else {
                cursor_newMessage = App.db.rawQuery("select * from chat where isRead=0 and ToUid=" + App.UID + " and FromUid=" + Friend_UID, null);
            }

            if (cursor_newMessage.getCount() > 0) {
                ChatUtils.GET_LOCA_CHAT(Friend_UID, 0, page, null);
            }
            cursor_newMessage.close();

        } catch (Exception ignored) {}

        if (!App.ShareText.isEmpty()) {
            String textToSend = App.ShareText;
            App.ShareText = "";
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SendMessage(textToSend);
            }, 300);
        }

        if (App.ShareUris != null && !App.ShareUris.isEmpty()) {
            List<Uri> urisToSend = new ArrayList<>(App.ShareUris);
            App.ShareUris.clear();

            for (Uri uri : urisToSend) {
                UriToPathUtil toPath = new UriToPathUtil();
                String path = toPath.getImageAbsolutePath(this, uri);
                if (path == null) continue;
                FBUploadMedia.UploadMedia(that, uri);
            }
        }
    }

    public static void setFriendType() {
        String sql_isFriend = "select * from friend where (Friend_UID=" + Friend_UID + " and uid=" + App.UID + ") limit 1";
        Cursor cursor_isFriend = App.db.rawQuery(sql_isFriend, null);
        if (cursor_isFriend.getCount() > 0) {
            cursor_isFriend.moveToFirst();
            int _Type = cursor_isFriend.getInt(cursor_isFriend.getColumnIndexOrThrow("Type"));
            Friend_UserID = cursor_isFriend.getString(cursor_isFriend.getColumnIndexOrThrow("UserID"));

            if (that != null) {
                that.runOnUiThread(() -> {
                    if (_Type == 3 || _Type == 4) {
                        AutoReconnectWebSocket.isGroupChat = false;
                        AutoReconnectWebSocket.currentGroupId = "";
                        nettis.setText(that.getString(R.string.chat_friend_blocked_you));
                        nettis.setVisibility(View.VISIBLE);
                        chat_inputbox.setVisibility(View.GONE);
                    } else if (_Type == 30) {
                        AutoReconnectWebSocket.isGroupChat = true;
                        AutoReconnectWebSocket.currentGroupId = Friend_UserID;
                        nettis.setText(that.getString(R.string.chat_group_verifying));
                        nettis.setVisibility(View.VISIBLE);
                        chat_inputbox.setVisibility(View.GONE);
                        checkGroupMembership(Friend_UserID);
                    } else {
                        AutoReconnectWebSocket.isGroupChat = false;
                        AutoReconnectWebSocket.currentGroupId = "";

                        nettis.setVisibility(View.GONE);
                        chat_inputbox.setVisibility(View.VISIBLE);
                    }
                });
            }
        } else {
            if (that != null) {
                that.runOnUiThread(() -> that.finish());
            }
        }
        cursor_isFriend.close();
    }

    private static void checkGroupMembership(String groupId) {
        if (Data.MemberBillingBridge.get() == null || that == null || that.isFinishing()) {
            return;
        }

        Data.MemberBillingBridge.get().checkGroupMembership(that, App.UID, groupId, new Data.IMemberBilling.GroupMemberCheckCallback() {
            @Override
            public void onSuccess(boolean isMember, boolean isOwner) {
                if (that == null || that.isFinishing() || that.isDestroyed()) return;

                isGroupOwner = isOwner;
                if (isMember) {
                    nettis.setVisibility(View.GONE);
                    chat_inputbox.setVisibility(View.VISIBLE);
                } else {
                    nettis.setText(that.getString(R.string.chat_group_not_member));
                    nettis.setVisibility(View.VISIBLE);
                    chat_inputbox.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (that == null || that.isFinishing() || that.isDestroyed()) return;

                nettis.setText(that.getString(R.string.chat_group_verify_network_error));
                nettis.setVisibility(View.VISIBLE);
                chat_inputbox.setVisibility(View.GONE);
            }
        });
    }

    public static void ShowChatList(String JsonStr, int type, int page) {
        try {
            new Thread(() -> App.db.execSQL("update friend set MessageNumber=0 where UID=" + Friend_UID)).start();
            JSONArray jsonArray = new JSONArray(JsonStr);

            if (jsonArray.length() > 0) {
                List<Module_Chat> newMessages = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    newMessages.add(parseJsonObjectToModel(jsonArray.getJSONObject(i)));
                }

                @SuppressLint({"NewApi", "LocalSuppress"})
                Comparator<Module_Chat> timeComparator = (o1, o2) -> {
                    try {
                        long t1 = Long.parseLong(o1.getTime());
                        long t2 = Long.parseLong(o2.getTime());
                        return Long.compare(t1, t2);
                    } catch (Exception e) {
                        return o1.getTime().compareTo(o2.getTime());
                    }
                };

                Collections.sort(newMessages, timeComparator);

                if (that != null) {
                    that.runOnUiThread(() -> {
                        if (RecyclerView_Chat == null || mAdapter_Chat == null) return;

                        if (page > 1) {
                            for (int i = newMessages.size() - 1; i >= 0; i--) {
                                Datas_Chat.add(0, newMessages.get(i));
                            }
                            Collections.sort(Datas_Chat, timeComparator);
                            mAdapter_Chat.notifyDataSetChanged();

                            if (mMainRefresh != null) {
                                mMainRefresh.setRefreshing(false);
                            }
                        } else {
                            for (Module_Chat model : newMessages) {
                                Datas_Chat.add(type == 1 ? Datas_Chat.size() : 0, model);
                            }
                            Collections.sort(Datas_Chat, timeComparator);
                            mAdapter_Chat.notifyDataSetChanged();

                            if (pendingTargetMsgId != -1) {
                                scrollToTargetPositionIfExist();
                            } else {
                                int lastPos = mAdapter_Chat.getItemCount() - 1;
                                if (lastPos >= 0) {
                                    RecyclerView_Chat.scrollToPosition(lastPos);
                                }
                            }

                            if (mMainRefresh != null) {
                                mMainRefresh.setRefreshing(false);
                            }
                        }
                    });
                }

                if (page <= 1) {
                    // 🌟 修复: 在清除未读状态时，如果是群聊，条件是 ToUid=Friend_UID
                    if (Type == 30) {
                        new Thread(() -> App.db.execSQL("update chat set IsRead=1 where IsRead=0 and ToUid=" + Friend_UID)).start();
                    } else {
                        new Thread(() -> App.db.execSQL("update chat set IsRead=1 where IsRead=0 and FromUid=" + Friend_UID + " and ToUid=" + App.UID)).start();
                    }
                }
            } else {
                if (that != null) {
                    that.runOnUiThread(() -> {
                        if (mMainRefresh != null) mMainRefresh.setRefreshing(false);
                    });
                }
            }
        } catch (Exception e) {
            Log.e("ERROR-->", "ShowChatList 解析或排序异常: " + e.getMessage());
            if (that != null) {
                that.runOnUiThread(() -> {
                    if (mMainRefresh != null) mMainRefresh.setRefreshing(false);
                });
            }
        }
    }

    private static Module_Chat parseJsonObjectToModel(JSONObject jsonObject) throws JSONException {
        return new Module_Chat(
                jsonObject.getInt("_ID"), jsonObject.getString("_Message"), jsonObject.getString("_MessageId"),
                jsonObject.getInt("_ToUid"), jsonObject.getInt("_FromUID"), "", jsonObject.getString("_Inputtime"),
                jsonObject.getString("_Yingyong"), jsonObject.getString("_Mediaurl"), jsonObject.getInt("_IsRead"),
                jsonObject.getInt("_Progress")
        );
    }

    private File createImageFile(String kzm) {
        File storageDir = new File(getExternalCacheDir(), "Cache");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        try {
            File imageFile = File.createTempFile("IMG_" + (System.currentTimeMillis() / 1000), "." + kzm, storageDir);
            currentPhotoPath = imageFile.getAbsolutePath();
            return imageFile;
        } catch (IOException e) { return null; }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                new Thread(() -> {
                    try {
                        FBUploadMedia.UploadMedia(activity_chat.this, uri);
                    } catch (Exception e) {
                        Log.e("UploadFileError", "处理相册选择文件失败: " + e.getMessage());
                        runOnUiThread(() -> FBMessage.Show(that, getString(R.string.chat_file_process_error, e.getMessage())));
                    }
                }).start();
            }
        } else if (resultCode == -1 && requestCode == 3) {
            CurrMediaFile = new File(currentPhotoPath);
            Uri uri = Uri.fromFile(CurrMediaFile);
            new Thread(() -> {
                try {
                    FBUploadMedia.UploadMedia(activity_chat.this, uri);
                } catch (Exception e) {
                    Log.e("UploadFileError", "处理相册选择文件失败: " + e.getMessage());
                    runOnUiThread(() -> FBMessage.Show(that, getString(R.string.chat_file_process_error, e.getMessage())));
                }
            }).start();
        } else if (resultCode == -1 && requestCode == 4) {
            try {
                Uri uri = data.getData();
                new Thread(() -> {
                    try {
                        FBUploadMedia.UploadMedia(activity_chat.this, uri);
                    } catch (Exception e) {
                        Log.e("UploadFileError", "处理相册选择文件失败: " + e.getMessage());
                        runOnUiThread(() -> FBMessage.Show(that, getString(R.string.chat_file_process_error, e.getMessage())));
                    }
                }).start();
            } catch (Exception e) {
                Log.e("TBA", "ERROR:" + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onRefresh() {
        if (!isRefresh) {
            isRefresh = true;
            page++;

            new Thread(() -> {
                ChatUtils.GET_LOCA_CHAT(Friend_UID, 0, page, null);
                runOnUiThread(() -> isRefresh = false);
            }).start();

            if (mMainRefresh != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (mMainRefresh != null && mMainRefresh.isRefreshing()) {
                        mMainRefresh.setRefreshing(false);
                    }
                }, 500);
            }
        } else {
            if (mMainRefresh != null) mMainRefresh.setRefreshing(false);
        }
    }

    private void UploadFile(int which) {
        if (which == 0) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/gif", "video/mp4", "video/3gpp"})
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(intent, 1);
        } else if (which == 2 || which == 3) {
            PermissionUtils.permission(Manifest.permission.CAMERA).callback(new PermissionUtils.SimpleCallback() {
                @Override
                public void onGranted() {
                    if (which == 2) {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        File photoFile = createImageFile("jpg");
                        if (photoFile != null) {
                            Uri photoUri = FileProvider.getUriForFile(activity_chat.this, getPackageName() + ".fileprovider", photoFile);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                            for (ResolveInfo resolveInfo : resInfoList) {
                                grantUriPermission(resolveInfo.activityInfo.packageName, photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }
                            startActivityForResult(takePictureIntent, 3);
                        }
                    } else {
                        Intent intentCamera = new Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
                        startActivityForResult(intentCamera, 4);
                    }
                }
                @Override public void onDenied() { FBMessage.Show(that, getString(R.string.chat_camera_permission_denied)); }
            }).request();
        } else if (which == 4) {
            PermissionUtils.permission(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    .callback(new PermissionUtils.SimpleCallback() {
                        @Override
                        public void onGranted() {
                            if (morebox != null) morebox.setVisibility(View.GONE);
                            sendCurrentLocationMessage();
                        }

                        @Override
                        public void onDenied() {
                            FBMessage.Show(that, getString(R.string.chat_loc_permission_denied));
                        }
                    }).request();
        }
    }

    private void sendCurrentLocationMessage() {
        FBMessage.Show(this, getString(R.string.chat_loc_getting));

        Utils.LocationUtils.getInstance().startSingleLocation(this, new Utils.LocationUtils.OnLocationResultListener() {
            @Override
            public void onSuccess(com.amap.api.location.AMapLocation location) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();

                String defaultLocTitle = getString(R.string.chat_loc_current);
                String title = !TextUtils.isEmpty(location.getDescription()) ? location.getDescription()
                        : (!TextUtils.isEmpty(location.getPoiName()) ? location.getPoiName() : defaultLocTitle);
                String address = !TextUtils.isEmpty(location.getAddress()) ? location.getAddress() : "";

                if (lat > 0 && lng > 0) {
                    SPUtils.getInstance().put("lat", String.valueOf(lat));
                    SPUtils.getInstance().put("lng", String.valueOf(lng));
                    if (!TextUtils.isEmpty(address)) {
                        SPUtils.getInstance().put("address", address);
                    }
                }

                sendLocationJson(lat, lng, title, address);
            }

            @Override
            public void onFailure(int errorCode, String errorInfo) {
                Log.e("Location", "高德定位失败，ErrCode: " + errorCode + ", " + errorInfo);
                double lat = 0, lng = 0;
                String address = "";
                try {
                    lat = Double.parseDouble(SPUtils.getInstance().get("lat", "0"));
                    lng = Double.parseDouble(SPUtils.getInstance().get("lng", "0"));
                    address = SPUtils.getInstance().get("address", "");
                } catch (Exception ignored) {}

                if (lat > 0 && lng > 0) {
                    sendLocationJson(lat, lng, getString(R.string.chat_loc_current), address);
                } else {
                    runOnUiThread(() -> FBMessage.Show(activity_chat.this, getString(R.string.chat_loc_failed_tip)));
                }
            }
        });
    }

    private void sendLocationJson(double lat, double lng, String title, String address) {
        try {
            JSONObject locJson = new JSONObject();
            locJson.put("type", "LOCATION");
            locJson.put("title", title);
            locJson.put("address", address);
            locJson.put("lat", lat);
            locJson.put("lng", lng);

            final String sendMsg = locJson.toString();
            runOnUiThread(() -> SendMessage(sendMsg));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void updateUploadProgress(String messageId, int progress, boolean isUploading) {
        if (that == null || that.isFinishing() || that.isDestroyed()) return;
        that.runOnUiThread(() -> {
            for (int i = 0; i < Datas_Chat.size(); i++) {
                Module_Chat item = Datas_Chat.get(i);
                if (item.getMessageId().equals(messageId)) {
                    item.set_Progress(progress);
                    item.isDownloading = isUploading;
                    Bundle payload = new Bundle();
                    payload.putString("UPDATE_PROGRESS", "true");
                    if (mAdapter_Chat != null) {
                        mAdapter_Chat.notifyItemChanged(i, payload);
                    }
                    break;
                }
            }
        });
    }

    public static void SendMessage(String message) {
        message = StringUtils.sqliteEscape(message);
        String yingyong = txtYingyong.getText().toString();
        if (yingyong.equals("null")) yingyong = "";

        String finalMessage = message;
        String finalYingyong = yingyong;

        if (that != null) {
            that.runOnUiThread(() -> {
                ApngExpressionParser.stopAnimators(edit_message.getText());
                edit_message.setText("");
                messageZy.setVisibility(View.GONE);
                chat_topbox.setVisibility(View.VISIBLE);
                YingyongBox.setVisibility(View.GONE);
                txtYingyong.setText("");
            });
        }

        String encryptedMessage, encryptedYingyong = "";
        try {
            encryptedMessage = AESUtils.encrypt(App.AppContext, finalMessage);

            if (!finalYingyong.isEmpty()) {
                encryptedYingyong = AESUtils.encrypt(App.AppContext, finalYingyong);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long time = System.currentTimeMillis();
        SendMessageId = App.UID + "" + time;
        JSONObject jo = new JSONObject();
        try {
            if (Type == 30) {
                // 恢复最外层的标准格式，绝不能乱加任何服务端未定义的字段
                jo.put("type", "GroupMessage")
                        .put("send_uid", App.UID)
                        .put("send_time", time);

                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", SendMessageId)
                        .put("_Message", encryptedMessage)
                        .put("_Yingyong", encryptedYingyong)
                        .put("_Mediaurl", "")
                        // _GroupId 传入服务端的真实群号 (例如 513178895610)
                        .put("_GroupId", Friend_UserID)
                        // 【最关键的修复】发给服务端的群广播消息，_ToUid 必须严格为 0
                        .put("_ToUid", 0)
                        .put("_FromUID", App.UID)
                        .put("_Inputtime", time)
                        .put("_ID", 0)
                        .put("_IsRead", 0)
                        .put("_Progress", 0);
                jo.put("data", jo_data);
            } else {
                // 单聊逻辑保持原样
                jo.put("type", "ChatMessage")
                        .put("send_uid", App.UID)
                        .put("send_time", time);

                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", SendMessageId)
                        .put("_Message", encryptedMessage)
                        .put("_Yingyong", encryptedYingyong)
                        .put("_Mediaurl", "")
                        // 单聊时，对方的 Friend_UID 刚好对应服务端的真实用户 UID
                        .put("_ToUid", Friend_UID)
                        .put("_FromUID", App.UID)
                        .put("_Inputtime", time)
                        .put("_ID", 0)
                        .put("_IsRead", 0)
                        .put("_Progress", 0);
                jo.put("data", jo_data);
            }

            // 整合顺序执行线程
            new Thread(() -> {
                // 1：强制将数据落入本地数据库并在 UI 渲染
                try {
                    JSONObject json = new JSONObject(finalMessage);
                    if (json.has("type") && "LOCATION".equals(json.getString("type"))) {
                        throw new JSONException("Location is not media");
                    }
                    String mediaMarkMessage = AESUtils.encrypt(that, "[/MEDIA]");
                    Cursor cursor_new = App.db.rawQuery("select id,MessageId from chat where MessageId=? order by id desc limit 1", new String[]{SendMessageId});
                    if (cursor_new.getCount() == 0) {
                        App.db.execSQL("insert into chat(MessageId,ToUid,FromUID,Message,Inputtime,IsRead,Yingyong,Mediaurl) values (?,?,?,?,?,0,?,?)",
                                new Object[]{SendMessageId, Friend_UID, App.UID, mediaMarkMessage, time, finalYingyong, finalMessage});
                        App.db.execSQL("update friend set Content='',Updatetime=? where Friend_UID=?", new Object[]{time, Friend_UID});
                    }
                    cursor_new.close();
                    ChatUtils.LocaMessage("", "", Friend_UID, finalMessage, true, false, json.getString("path"), time);
                } catch (JSONException e) {
                    try {
                        Cursor cursor_new = App.db.rawQuery("select id,MessageId from chat where MessageId=? order by id desc limit 1", new String[]{SendMessageId});
                        if (cursor_new.getCount() == 0) {
                            App.db.execSQL("insert into chat(MessageId,ToUid,FromUID,Message,Inputtime,IsRead,Yingyong,Mediaurl) values (?,?,?,?,?,0,?,?)",
                                    new Object[]{SendMessageId, Friend_UID, App.UID, finalMessage, time, finalYingyong, ""});
                            App.db.execSQL("update friend set Content='',Updatetime=? where Friend_UID=?", new Object[]{time, Friend_UID});
                        }
                        cursor_new.close();
                        ChatUtils.LocaMessage(finalMessage, finalYingyong, Friend_UID, "", true, true, "", time);
                    } catch (Exception ignored) {}
                } catch (Exception ignored) {}

                // 2：给 UI 主线程缓冲
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}

                // 3：检查网络发送
                try {
                    if (AutoReconnectWebSocket.socket != null && AutoReconnectWebSocket.socketConnected.get()) {
                        Log.e("SOCKET",jo.toString());
                        AutoReconnectWebSocket.socket.sendMessage(jo.toString());
                    } else {
                        triggerLocalTimeout(SendMessageId);
                    }
                } catch (Exception e) {
                    triggerLocalTimeout(SendMessageId);
                }
            }).start();

        } catch (JSONException e) { throw new RuntimeException(e); }
    }

    private static void triggerLocalTimeout(String msgId) {
        if (that == null) return;
        that.runOnUiThread(() -> {
            try {
                new Thread(() -> App.db.execSQL("UPDATE chat SET IsRead = 7 WHERE MessageId = ?", new Object[]{msgId})).start();
                for (int i = 0; i < Datas_Chat.size(); i++) {
                    Module_Chat item = Datas_Chat.get(i);
                    if (msgId.equals(item.getMessageId())) {
                        Datas_Chat.set(i, new Module_Chat(item.getID(), item.getMessage(), item.getMessageId(), item.getUID(), item.getFromUID(), item.getThumb(), item.getTime(), item.getYingyong(), item.getMediaurl(), 7, 0));
                        if (mAdapter_Chat != null) {
                            mAdapter_Chat.notifyItemChanged(i);
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (SPUtils.getInstance().get("switch_backdelete", false)) {
            new Thread(() -> App.db.execSQL("DELETE FROM chat where (ToUid=" + Friend_UID + " and FromUid=" + App.UID + ") or (ToUid=" + App.UID + " and FromUid=" + Friend_UID + ")")).start();
        }
        boolean switch_sensorlock = SPUtils.getInstance().get("switch_sensorlock", false);
        if (flipDetector != null && switch_sensorlock) {
            flipDetector.stop();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_friend_uid", Friend_UID);
        if (currentPhotoPath != null) {
            outState.putString("saved_photo_path", currentPhotoPath);
        }
    }

    public static void loadChatHistoryToTarget(long targetId) {
        new Thread(() -> {
            try {
                Cursor timeCursor = App.db.rawQuery("SELECT Inputtime FROM chat WHERE id = " + targetId, null);
                long targetTime = 0;
                if (timeCursor != null && timeCursor.moveToFirst()) {
                    targetTime = timeCursor.getLong(timeCursor.getColumnIndexOrThrow("Inputtime"));
                    timeCursor.close();
                } else {
                    if (timeCursor != null) timeCursor.close();
                    return;
                }

                // 🌟 修复: 深度加载历史记录时兼容群聊条件
                String baseCondition;
                if (Type == 30) {
                    baseCondition = "(ToUid=" + Friend_UID + ")";
                } else {
                    baseCondition = "((FromUid=" + App.UID + " AND ToUid=" + Friend_UID + ") OR (FromUid=" + Friend_UID + " AND ToUid=" + App.UID + "))";
                }

                Cursor countCursor = App.db.rawQuery("SELECT COUNT(*) FROM chat WHERE " + baseCondition + " AND Inputtime >= " + targetTime, null);
                int count = 20;
                if (countCursor != null && countCursor.moveToFirst()) {
                    count = countCursor.getInt(0);
                    countCursor.close();
                }

                int limit = count + 10;

                Cursor cursor = App.db.rawQuery("SELECT * FROM chat WHERE " + baseCondition + " ORDER BY Inputtime DESC LIMIT " + limit, null);
                List<Module_Chat> tempMessages = new ArrayList<>();
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                        String message = cursor.getString(cursor.getColumnIndexOrThrow("Message"));
                        String messageId = cursor.getString(cursor.getColumnIndexOrThrow("MessageId"));
                        int toUid = cursor.getInt(cursor.getColumnIndexOrThrow("ToUid"));
                        int fromUid = cursor.getInt(cursor.getColumnIndexOrThrow("FromUid"));
                        String inputTime = cursor.getString(cursor.getColumnIndexOrThrow("Inputtime"));
                        String yingyong = cursor.getString(cursor.getColumnIndexOrThrow("Yingyong"));
                        String mediaurl = cursor.getString(cursor.getColumnIndexOrThrow("Mediaurl"));
                        int isRead = cursor.getInt(cursor.getColumnIndexOrThrow("IsRead"));

                        tempMessages.add(new Module_Chat(id, message == null ? "" : message,
                                messageId, toUid, fromUid, "", inputTime,
                                yingyong == null ? "" : yingyong,
                                mediaurl == null ? "" : mediaurl, isRead, 0));
                    }
                    cursor.close();
                }

                Collections.sort(tempMessages, (o1, o2) -> {
                    try {
                        long t1 = Long.parseLong(o1.getTime());
                        long t2 = Long.parseLong(o2.getTime());
                        return Long.compare(t1, t2);
                    } catch (Exception e) {
                        return o1.getTime().compareTo(o2.getTime());
                    }
                });

                if (that != null) {
                    that.runOnUiThread(() -> {
                        Datas_Chat.clear();
                        Datas_Chat.addAll(tempMessages);
                        mAdapter_Chat.notifyDataSetChanged();

                        that.page = (tempMessages.size() / 20) + 1;

                        int targetPos = -1;
                        for (int i = 0; i < Datas_Chat.size(); i++) {
                            if (Datas_Chat.get(i).getID() == targetId) {
                                targetPos = i;
                                break;
                            }
                        }

                        if (targetPos != -1) {
                            final int pos = targetPos;
                            RecyclerView_Chat.post(() -> {
                                if (layoutManager != null) {
                                    layoutManager.scrollToPositionWithOffset(pos, 80);
                                } else {
                                    RecyclerView_Chat.scrollToPosition(pos);
                                }
                                pendingTargetMsgId = -1;
                            });
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("ChatActivity", "深度加载上下文失败: " + e.getMessage());
            }
        }).start();
    }
}