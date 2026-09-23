package adapter;

import static com.blankj.utilcode.util.ActivityUtils.startActivity;
import static com.qapp.midian.activity_chat.that;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friendsetting;
import com.qapp.midian.activity_web;
import com.qapp.midian.R;
import com.qapp.midian.activity_media;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import Data.ChatUtils;
import Emoji.ApngTextView;
import Utils.AudioRecorderUtils;
import Utils.DateTimeUtils;
import Utils.FBMessage;
import Utils.FileManage;
import Utils.ImageLoadUtils;
import Utils.SPUtils;
import Utils.StringUtils;
import Utils.Upload;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import socket.AutoReconnectWebSocket;
import jp.wasabeef.glide.transformations.BlurTransformation;

public class Adapter_Chat extends RecyclerView.Adapter<Adapter_Chat.ViewHolder> {
    private int mPosition = 0;
    public static int cPosition = -1, PlayPosition = -1;

    public static ExoPlayer exoPlayer;
    public static boolean isUserSeeking = false;

    public static Handler audioProgressHandler = new Handler(Looper.getMainLooper());
    public static Runnable audioProgressRunnable;

    public static void startAudioProgress() {
        if (audioProgressRunnable != null) {
            audioProgressHandler.removeCallbacks(audioProgressRunnable);
        }
        audioProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.isPlaying() && PlayPosition != -1 && !isUserSeeking) {
                    long currentPos = exoPlayer.getCurrentPosition();
                    long duration = exoPlayer.getDuration();
                    if (duration > 0) {
                        float progress = (currentPos * 100f / duration);
                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            that.runOnUiThread(() -> {
                                if (activity_chat.mAdapter_Chat != null) {
                                    Bundle payload = new Bundle();
                                    payload.putFloat("AUDIO_PROGRESS", progress);
                                    activity_chat.mAdapter_Chat.notifyItemChanged(PlayPosition, payload);
                                }
                            });
                        }
                    }
                    audioProgressHandler.postDelayed(this, 100);
                }
            }
        };
        audioProgressHandler.post(audioProgressRunnable);
    }

    public static void stopAudioProgress() {
        if (audioProgressRunnable != null) {
            audioProgressHandler.removeCallbacks(audioProgressRunnable);
        }
    }

    public static void releasePlayer() {
        stopAudioProgress();
        PlayPosition = -1;
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            exoPlayer = null;
        }
    }

    private List<Module_Chat> mData;

    public Adapter_Chat(List<Module_Chat> mData) {
        this.mData = mData;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.module_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            Context context = holder.itemView.getContext();
            for (Object payload : payloads) {
                if (payload instanceof Bundle) {
                    Bundle bundle = (Bundle) payload;

                    if (bundle.containsKey("AUDIO_PROGRESS")) {
                        continue;
                    }

                    if (bundle.containsKey("UPDATE_PROGRESS")) {
                        Module_Chat msg = mData.get(position);
                        if (msg.getFromUID() != App.UID) {
                            if (holder.ProgressBarLeft != null) {
                                holder.ProgressBarLeft.setProgress(msg.get_Progress());
                                holder.ProgressBarLeft.setVisibility(msg.isDownloading ? View.VISIBLE : View.GONE);
                            }
                        } else {
                            if (holder.ProgressBarRight != null) {
                                holder.ProgressBarRight.setProgress(msg.get_Progress());
                                holder.ProgressBarRight.setVisibility(msg.isDownloading ? View.VISIBLE : View.GONE);
                            }
                        }
                    }

                    if (bundle.containsKey("_IsRead")) {
                        int isReadStatus = bundle.getInt("_IsRead");
                        Module_Chat msg = mData.get(position);
                        String SEND_TIME = "";
                        try {
                            if (msg.getTime() != null && !msg.getTime().isEmpty()) {
                                SEND_TIME = DateTimeUtils.getTimeAgo(Long.parseLong(msg.getTime()));
                            }
                        } catch (Exception ignored) {}

                        if (isReadStatus == 3) {
                            if (holder.RightYidu != null) {
                                holder.RightYidu.setVisibility(View.VISIBLE);
                                holder.RightYidu.setImageResource(R.drawable.yidu);
                            }
                            if (holder.RightError != null) holder.RightError.setVisibility(View.GONE);
                            if (holder.txtError != null) holder.txtError.setVisibility(View.GONE);
                            if (holder.txtTimeRight != null) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                                holder.txtTimeRight.setText(SEND_TIME);
                            }
                        } else if (isReadStatus == 0) {
                            if (holder.RightYidu != null) holder.RightYidu.setVisibility(View.GONE);
                            if (holder.RightError != null) holder.RightError.setVisibility(View.GONE);
                            if (holder.txtError != null) holder.txtError.setVisibility(View.GONE);

                            if (holder.txtTimeRight != null) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                                holder.txtTimeRight.setText(context.getString(R.string.chat_msg_sending));
                            }
                        } else if (isReadStatus == 6) {
                            if (holder.RightYidu != null) {
                                holder.RightYidu.setVisibility(View.VISIBLE);
                                holder.RightYidu.setImageResource(R.drawable.lixian);
                            }
                            if (holder.RightError != null) holder.RightError.setVisibility(View.GONE);
                            if (holder.txtError != null) holder.txtError.setVisibility(View.GONE);
                            if (holder.txtTimeRight != null) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                                holder.txtTimeRight.setText(context.getString(R.string.chat_msg_offline_delivered));
                            }
                        } else if (isReadStatus == 7) {
                            if (holder.RightYidu != null) {
                                holder.RightYidu.setVisibility(View.VISIBLE);
                                holder.RightYidu.setImageResource(R.drawable.error);
                            }
                            if (holder.RightError != null) holder.RightError.setVisibility(View.GONE);
                            if (holder.txtError != null) holder.txtError.setVisibility(View.GONE);
                            if (holder.txtTimeRight != null) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                                holder.txtTimeRight.setText(context.getString(R.string.chat_msg_send_failed_retry, SEND_TIME));
                            }
                        } else if (isReadStatus == 5) {
                            if (holder.rightBox != null) holder.rightBox.setVisibility(View.VISIBLE);
                            if (holder.rightMediaBox != null) holder.rightMediaBox.setVisibility(View.GONE);
                            if (holder.rightLocationBox != null) holder.rightLocationBox.setVisibility(View.GONE);
                            if (holder.leftLocationBox != null) holder.leftLocationBox.setVisibility(View.GONE);
                            if (holder.txtYingyongRight != null) {
                                holder.txtYingyongRight.setVisibility(View.VISIBLE);
                                holder.txtYingyongRight.setText(Html.fromHtml(context.getString(R.string.chat_msg_recalled_with_edit)));
                            }
                            if (holder.txtMessageRight != null) holder.txtMessageRight.setVisibility(View.GONE);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;

        boolean isActive = (position == PlayPosition);

        holder.setData(this.mData.get(position).getID(), this.mData.get(position).getMessage(), this.mData.get(position).getMessageId(), this.mData.get(position).getUID(), this.mData.get(position).getFromUID(), this.mData.get(position).getThumb(), this.mData.get(position).getTime(), this.mData.get(position).getYingyong(), this.mData.get(position).getMediaurl(), this.mData.get(position).get_IsRead(), this.mData.get(position).get_Progress(), isActive);

        final String _Message = this.mData.get(position).getMessage();
        final String _Yingyong = this.mData.get(position).getYingyong();
        int _ID = this.mData.get(position).getID();
        String _MessageID = this.mData.get(position).getMessageId();
        int _FromUID = this.mData.get(position).getFromUID();
        int _ToUID = this.mData.get(position).getUID();
        int _ISRead = this.mData.get(position).get_IsRead();
        String _Time = this.mData.get(position).getTime();
        String _Mediaurl = this.mData.get(position).getMediaurl();

        holder.LeftImage.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent it = new Intent(context, activity_friendsetting.class);
            Bundle bundle = new Bundle();
            bundle.putInt("friend_uid", _FromUID);
            it.putExtras(bundle);
            context.startActivity(it);
        });

        holder.txtYingyongRight.setOnClickListener(v -> {
            if (_ISRead == 5) activity_chat.edit_message.setText(_Message);
        });

        holder.RightYidu.setOnClickListener(v -> {
            if (_ISRead == 6 || _ISRead == 7) {
                resendMessage(_MessageID);
            }
        });

        holder.txtTimeRight.setOnClickListener(v -> {
            if (_ISRead == 6 || _ISRead == 7) {
                resendMessage(_MessageID);
            }
        });

        holder.txtMessage.setOnClickListener(v -> {
            notifyItemChanged(cPosition); cPosition = -1;
            ClickItem(_ID, _MessageID, _Message, _Mediaurl, _FromUID, _ToUID, holder, position);
        });

        holder.leftMediaBox.setOnClickListener(v -> {
            notifyItemChanged(cPosition); cPosition = -1;
            ClickItem(_ID, _MessageID, _Message, _Mediaurl, _FromUID, _ToUID, holder, position);
        });

        holder.txtMessageRight.setOnClickListener(v -> {
            notifyItemChanged(cPosition); cPosition = -1;
            ClickItem(_ID, _MessageID, _Message, _Mediaurl, _FromUID, _ToUID, holder, position);
        });

        holder.rightMediaBox.setOnClickListener(v -> {
            notifyItemChanged(cPosition); cPosition = -1;
            ClickItem(_ID, _MessageID, _Message, _Mediaurl, _FromUID, _ToUID, holder, position);
        });

        holder.txtMessage.setOnLongClickListener(v -> {
            View popupView = LayoutInflater.from(that).inflate(R.layout.popup_chat_bottom, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

            TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
            menu_langdu.setOnClickListener(v1 -> { Langdu(_Message); popupWindow.dismiss(); });

            @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
            menu_yinyong.setOnClickListener(v1 -> { Yinyong(_Message); popupWindow.dismiss(); });

            TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
            menu_chehui.setVisibility(View.GONE);

            TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
            menu_fuzhi.setOnClickListener(v1 -> { Fuzhi(_Message); popupWindow.dismiss(); });

            TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);
            menu_shanchu.setOnClickListener(v1 -> { Shanchu(_MessageID, position); popupWindow.dismiss(); });

            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.showAsDropDown(v);
            return true;
        });

        holder.leftMediaBox.setOnLongClickListener(v -> {
            View popupView = LayoutInflater.from(that).inflate(R.layout.popup_chat_bottom, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

            TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
            @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
            TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
            TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
            TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);

            menu_langdu.setVisibility(View.GONE);
            menu_yinyong.setVisibility(View.GONE);
            menu_chehui.setVisibility(View.GONE);
            menu_fuzhi.setVisibility(View.GONE);

            menu_shanchu.setOnClickListener(v1 -> { Shanchu(_MessageID, position); popupWindow.dismiss(); });

            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.showAsDropDown(v);
            return true;
        });

        holder.txtMessageRight.setOnLongClickListener(v -> {
            View popupView = LayoutInflater.from(App.AppContext).inflate(R.layout.popup_chat_bottom, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

            TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
            menu_langdu.setOnClickListener(v1 -> { Langdu(_Message); popupWindow.dismiss(); });

            @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
            menu_yinyong.setOnClickListener(v1 -> { Yinyong(_Message); popupWindow.dismiss(); });

            TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
            menu_chehui.setOnClickListener(v1 -> { Chehui(_ID, _ToUID, _FromUID, _Message, _MessageID, _Time, position); popupWindow.dismiss(); });

            TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
            menu_fuzhi.setOnClickListener(v1 -> { Fuzhi(_Message); popupWindow.dismiss(); });

            TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);
            menu_shanchu.setOnClickListener(v1 -> { Shanchu(_MessageID, position); popupWindow.dismiss(); });

            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.showAsDropDown(v);
            return true;
        });

        holder.rightMediaBox.setOnLongClickListener(v -> {
            View popupView = LayoutInflater.from(that).inflate(R.layout.popup_chat_bottom, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

            TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
            @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
            TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
            TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
            TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);

            menu_langdu.setVisibility(View.GONE);
            menu_yinyong.setVisibility(View.GONE);
            menu_chehui.setVisibility(View.GONE);
            menu_fuzhi.setVisibility(View.GONE);

            menu_shanchu.setOnClickListener(v1 -> { Shanchu(_MessageID, position); popupWindow.dismiss(); });
            menu_chehui.setOnClickListener(v1 -> { Chehui(_ID, _ToUID, _FromUID, _Message, _MessageID, _Time, position); popupWindow.dismiss(); });

            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.showAsDropDown(v);
            return true;
        });

        if (holder.leftLocationBox != null) {
            holder.leftLocationBox.setOnLongClickListener(v -> {
                View popupView = LayoutInflater.from(that).inflate(R.layout.popup_chat_bottom, null);
                PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
                TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
                TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
                TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
                TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);

                menu_langdu.setVisibility(View.GONE);
                menu_yinyong.setVisibility(View.GONE);
                menu_chehui.setVisibility(View.GONE);
                menu_fuzhi.setVisibility(View.GONE);

                menu_shanchu.setOnClickListener(v1 -> {
                    Shanchu(_MessageID, position);
                    popupWindow.dismiss();
                });

                popupWindow.setOutsideTouchable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                popupWindow.showAsDropDown(v);
                return true;
            });
        }

        if (holder.rightLocationBox != null) {
            holder.rightLocationBox.setOnLongClickListener(v -> {
                View popupView = LayoutInflater.from(App.AppContext).inflate(R.layout.popup_chat_bottom, null);
                PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                TextView menu_langdu = popupView.findViewById(R.id.menu_langdu);
                TextView menu_yinyong = popupView.findViewById(R.id.menu_yinyong);
                TextView menu_chehui = popupView.findViewById(R.id.menu_chehui);
                TextView menu_fuzhi = popupView.findViewById(R.id.menu_fuzhi);
                TextView menu_shanchu = popupView.findViewById(R.id.menu_shanchu);

                menu_langdu.setVisibility(View.GONE);
                menu_yinyong.setVisibility(View.GONE);
                menu_chehui.setVisibility(View.GONE);
                menu_fuzhi.setVisibility(View.GONE);

                menu_chehui.setOnClickListener(v1 -> {
                    Chehui(_ID, _ToUID, _FromUID, _Message, _MessageID, _Time, position);
                    popupWindow.dismiss();
                });

                menu_shanchu.setOnClickListener(v1 -> {
                    Shanchu(_MessageID, position);
                    popupWindow.dismiss();
                });

                popupWindow.setOutsideTouchable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                popupWindow.showAsDropDown(v);
                return true;
            });
        }
    }

    private String shortenUrlsForDisplay(String text) {
        if (text == null || text.isEmpty()) return text;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(http|https)://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullUrl = matcher.group();
            String shortUrl = fullUrl;
            if (fullUrl.length() > 30) {
                shortUrl = fullUrl.substring(0, 30) + "...";
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(shortUrl));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void applyUrlSpans(TextView textView, boolean isRightSide) {
        CharSequence text = textView.getText();
        android.text.Spannable spannable;
        if (text instanceof android.text.Spannable) {
            spannable = (android.text.Spannable) text;
        } else {
            spannable = new android.text.SpannableString(text);
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(http|https)://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+(?:\\.\\.\\.)?");
        java.util.regex.Matcher matcher = pattern.matcher(spannable);
        boolean hasSpans = false;
        while (matcher.find()) {
            hasSpans = true;
            int color = isRightSide ? Color.parseColor("#87CEFA") : Color.parseColor("#1E90FF");
            spannable.setSpan(new android.text.style.ForegroundColorSpan(color), matcher.start(), matcher.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new android.text.style.UnderlineSpan(), matcher.start(), matcher.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (hasSpans) {
            textView.setText(spannable);
        }
    }

    private void resendMessage(String targetMessageId) {
        int _id = 0, _ToUid = 0, _FromUid = 0;
        String _Inputtime = "", _Yingyong_error = "", _Message_error = "", _MessageId = "";
        String _Mediaurl_error = "", _Thumb_error = "";
        int targetIndex = -1;

        for (int i = 0; i < activity_chat.Datas_Chat.size(); i++) {
            String _messageId = activity_chat.Datas_Chat.get(i).getMessageId();
            if (_messageId.equals(targetMessageId)) {
                targetIndex = i;
                _MessageId = targetMessageId;

                App.db.execSQL("update chat set IsRead=0 where MessageId='" + targetMessageId + "'");

                Module_Chat currentChat = activity_chat.Datas_Chat.get(i);
                _id = currentChat.getID();
                _ToUid = currentChat.getUID();
                _FromUid = currentChat.getFromUID();
                _Inputtime = currentChat.getTime();
                _Yingyong_error = currentChat.getYingyong();
                _Message_error = currentChat.getMessage();
                _Mediaurl_error = currentChat.getMediaurl();
                _Thumb_error = currentChat.getThumb();

                activity_chat.Datas_Chat.set(i, new Module_Chat(
                        _id, _Message_error, _messageId, _ToUid, _FromUid, _Thumb_error, _Inputtime + "", _Yingyong_error, _Mediaurl_error, 0, 0
                ));
                break;
            }
        }

        if (targetIndex != -1 && that != null && !that.isFinishing() && !that.isDestroyed()) {
            final int finalIndex = targetIndex;
            that.runOnUiThread(() -> {
                if (activity_chat.mAdapter_Chat != null) {
                    Bundle payload = new Bundle();
                    payload.putInt("_IsRead", 0);
                    payload.putBoolean("IS_RESENDING", true);
                    activity_chat.mAdapter_Chat.notifyItemChanged(finalIndex, payload);
                }
            });
        }

        JSONObject jo = new JSONObject();
        try {
            String encryptedMessage = null, encryptedYingyong = "", encryptedMediaurl = "";
            try {
                encryptedMessage = AESUtils.encrypt(App.AppContext, _Message_error);
                if (!_Yingyong_error.equals("")) {
                    encryptedYingyong = AESUtils.encrypt(App.AppContext, _Yingyong_error);
                }
                if (_Mediaurl_error != null && !_Mediaurl_error.isEmpty() && !_Mediaurl_error.equals("null")) {
                    encryptedMediaurl = AESUtils.encrypt(App.AppContext, _Mediaurl_error);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            long time = System.currentTimeMillis();

            if (AutoReconnectWebSocket.isGroupChat) {
                jo.put("type", "GroupMessage");
                jo.put("send_uid", App.UID);
                jo.put("send_time", time);
                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", _MessageId);
                jo_data.put("_Message", encryptedMessage);
                jo_data.put("_Yingyong", encryptedYingyong);
                jo_data.put("_Mediaurl", encryptedMediaurl);
                jo_data.put("_GroupId", AutoReconnectWebSocket.currentGroupId);
                jo_data.put("_ToUid", 0);
                jo_data.put("_FromUID", App.UID);
                jo_data.put("_Inputtime", time);
                jo_data.put("_ID", 0);
                jo_data.put("_IsRead", 0);
                jo_data.put("_Progress", 0);
                jo.put("data", jo_data);
            } else {
                jo.put("type", "ChatMessage");
                jo.put("send_uid", App.UID);
                jo.put("send_time", time);
                JSONObject jo_data = new JSONObject();
                jo_data.put("_MessageId", _MessageId);
                jo_data.put("_Message", encryptedMessage);
                jo_data.put("_Yingyong", encryptedYingyong);
                jo_data.put("_Mediaurl", encryptedMediaurl);
                jo_data.put("_ToUid", _ToUid);
                jo_data.put("_FromUID", App.UID);
                jo_data.put("_Inputtime", time);
                jo_data.put("_ID", 0);
                jo_data.put("_IsRead", 0);
                jo_data.put("_Progress", 0);
                jo.put("data", jo_data);
            }

            if (AutoReconnectWebSocket.socket != null) {
                AutoReconnectWebSocket.socket.sendMessage(jo.toString());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getItemCount() {
        if (this.mData == null) return 0;

        if (this.mData.size() > 0) {
            Module_Chat lastItem = this.mData.get(this.mData.size() - 1);
            if ((lastItem.getMessage() == null || lastItem.getMessage().isEmpty())
                    && (lastItem.getMediaurl() == null || lastItem.getMediaurl().isEmpty() || lastItem.getMediaurl().equals("null"))) {
                return this.mData.size() - 1;
            }
        }
        return this.mData.size();
    }

    public int getPosition() { return mPosition; }
    public void setmPosition(int mPosition) { this.mPosition = mPosition; }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout Show_Chat_Left, leftBox, rightBox;
        public LinearLayout leftMediaBox, rightMediaBox;

        public TextView txtMessage;
        public TextView txtTime, leftMediaContent;
        public TextView txtYingyong;
        public TextView txtTimeCountLeft;
        public ImageView left_file_icon, ImageLeft, RightError, RightImage, LeftImage, right_file_icon;
        public ProgressBar ProgressBarRight, ProgressBarLeft;
        public LinearLayout Show_Chat_Right;
        public ApngTextView txtMessageRight;
        public TextView txtTimeRight, rightMediaContent;
        public TextView txtYingyongRight;
        public TextView txtTimeCountRight, txtError;
        public TextView txtSenderNicknameRight;
        public ImageView RightYidu, btnLeftPauseConfirm, btnRightPauseConfirm;
        public TextView txtSenderNickname;

        public LinearLayout leftLocationBox, rightLocationBox;
        public TextView tvLeftLocTitle, tvLeftLocAddress, tvRightLocTitle, tvRightLocAddress;
        public ImageView ivLeftLocMap, ivRightLocMap;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            Show_Chat_Left = itemView.findViewById(R.id.Show_Chat_Left);
            leftBox = itemView.findViewById(R.id.leftBox);
            rightBox = itemView.findViewById(R.id.rightBox);
            leftMediaBox = itemView.findViewById(R.id.leftMediaBox);
            rightMediaBox = itemView.findViewById(R.id.rightMediaBox);

            txtMessage = itemView.findViewById(R.id.txtMessage);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtTimeCountLeft = itemView.findViewById(R.id.txtTimeCountLeft);
            txtYingyong = itemView.findViewById(R.id.txtYingyongLeft);
            ProgressBarLeft = itemView.findViewById(R.id.ProgressBarLeft);
            leftMediaContent = itemView.findViewById(R.id.leftMediaContent);
            left_file_icon = itemView.findViewById(R.id.left_file_icon);
            btnLeftPauseConfirm = itemView.findViewById(R.id.btnLeftPauseConfirm);
            txtSenderNickname = itemView.findViewById(R.id.txtSenderNickname);
            txtSenderNicknameRight = itemView.findViewById(R.id.txtSenderNicknameRight);

            Show_Chat_Right = itemView.findViewById(R.id.Show_Chat_Right);
            txtMessageRight = itemView.findViewById(R.id.txtMessageRight);
            txtTimeRight = itemView.findViewById(R.id.txtTimeRight);
            txtYingyongRight = itemView.findViewById(R.id.txtYingyongRight);
            txtTimeCountRight = itemView.findViewById(R.id.txtTimeCountRight);
            RightError = itemView.findViewById(R.id.RightError);
            RightImage = itemView.findViewById(R.id.right_thumb);
            LeftImage = itemView.findViewById(R.id.left_thumb);
            RightYidu = itemView.findViewById(R.id.RightYidu);
            txtError = itemView.findViewById(R.id.txtError);
            rightMediaContent = itemView.findViewById(R.id.rightMediaContent);
            right_file_icon = itemView.findViewById(R.id.right_file_icon);
            btnRightPauseConfirm = itemView.findViewById(R.id.btnRightPauseConfirm);

            leftLocationBox = itemView.findViewById(R.id.leftLocationBox);
            tvLeftLocTitle = itemView.findViewById(R.id.tvLeftLocTitle);
            tvLeftLocAddress = itemView.findViewById(R.id.tvLeftLocAddress);
            ivLeftLocMap = itemView.findViewById(R.id.ivLeftLocMap);

            rightLocationBox = itemView.findViewById(R.id.rightLocationBox);
            tvRightLocTitle = itemView.findViewById(R.id.tvRightLocTitle);
            tvRightLocAddress = itemView.findViewById(R.id.tvRightLocAddress);
            ivRightLocMap = itemView.findViewById(R.id.ivRightLocMap);

            if (ivLeftLocMap != null) {
                ivLeftLocMap.setClickable(false);
                ivLeftLocMap.setLongClickable(false);
            }
            if (ivRightLocMap != null) {
                ivRightLocMap.setClickable(false);
                ivRightLocMap.setLongClickable(false);
            }
        }

        private void loadLocationMap(Context context, double lat, double lng, ImageView imageView) {
            if (lat == 0 || lng == 0) return;

            String fileName = "loc_" + String.valueOf(lat).replace(".", "_") + "_" + String.valueOf(lng).replace(".", "_") + ".png";
            File cacheDir = new File(App.Folder + "/Cache/");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File localFile = new File(cacheDir, fileName);

            if (localFile.exists()) {
                Glide.with(context)
                        .load(localFile)
                        .placeholder(R.drawable.bg_input_box)
                        .error(R.drawable.bg_input_box)
                        .into(imageView);
            } else {
                String mapUrl = getStaticMapUrl(lat, lng);
                Glide.with(context)
                        .load(mapUrl)
                        .placeholder(R.drawable.bg_input_box)
                        .error(R.drawable.bg_input_box)
                        .into(imageView);

                new Thread(() -> {
                    try {
                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder().url(mapUrl).build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            FileOutputStream fos = new FileOutputStream(localFile);
                            fos.write(response.body().bytes());
                            fos.flush();
                            fos.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }

        public void setData(int _ID, String _Message, String _MessageId, int _UID, int _FromUID, String _Thumb, String _Time, String _Yingyong, String _MediaurlInput, int _IsRead, int _Progress, boolean isActive) {
            Context context = itemView.getContext();
            String SEND_TIME = DateTimeUtils.getTimeAgo(Long.parseLong(_Time));

            String _Mediaurl = _MediaurlInput;
            boolean isLocationMsg = false;
            String locTitle = context.getString(R.string.chat_loc_title);
            String locAddress = context.getString(R.string.chat_loc_unknown_address);
            double locLat = 0.0;
            double locLng = 0.0;

            if (_Message != null) {
                if (_Message.startsWith("{") && _Message.contains("\"type\":\"LOCATION\"")) {
                    try {
                        JSONObject locObj = new JSONObject(_Message);
                        locTitle = locObj.optString("title", context.getString(R.string.chat_loc_title));
                        locAddress = locObj.optString("address", "");
                        locLat = locObj.optDouble("lat", 0.0);
                        locLng = locObj.optDouble("lng", 0.0);
                        isLocationMsg = true;
                    } catch (Exception ignored) {}
                } else if (_Message.startsWith("[位置]")) {
                    isLocationMsg = true;
                    locAddress = _Message.replace("[位置]", "").trim();
                    locTitle = locAddress;
                    try {
                        locLat = Double.parseDouble(SPUtils.getInstance().get("lat", "0"));
                        locLng = Double.parseDouble(SPUtils.getInstance().get("lng", "0"));
                    } catch (Exception ignored) {}
                }
            }

            if (!isLocationMsg && _Mediaurl != null && _Mediaurl.startsWith("{") && _Mediaurl.contains("\"type\":\"LOCATION\"")) {
                try {
                    JSONObject locObj = new JSONObject(_Mediaurl);
                    locTitle = locObj.optString("title", context.getString(R.string.chat_loc_title));
                    locAddress = locObj.optString("address", "");
                    locLat = locObj.optDouble("lat", 0.0);
                    locLng = locObj.optDouble("lng", 0.0);
                    isLocationMsg = true;
                    _Mediaurl = "";
                } catch (Exception ignored) {}
            }

            final double finalLat = locLat;
            final double finalLng = locLng;
            final String finalTitle = locTitle;

            if (_FromUID != App.UID) {
                Show_Chat_Left.setVisibility(View.VISIBLE);
                Show_Chat_Right.setVisibility(View.GONE);
                LeftImage.setVisibility(View.VISIBLE);

                if (this.txtSenderNickname != null) {
                    this.txtSenderNickname.setVisibility(View.VISIBLE);
                    String displayNick = context.getString(R.string.chat_member_prefix, String.valueOf(_FromUID));
                    String leftAvatarUrl = "";
                    try {
                        android.database.Cursor cur = App.db.rawQuery("select Nickname, _Nickname, Image from friend where Friend_UID=" + _FromUID + " limit 1", null);
                        if (cur.getCount() > 0) {
                            cur.moveToFirst();
                            String nick = cur.getString(cur.getColumnIndexOrThrow("_Nickname"));
                            if (nick == null || nick.isEmpty() || nick.equals("null")) {
                                nick = cur.getString(cur.getColumnIndexOrThrow("Nickname"));
                            }
                            if (nick != null && !nick.isEmpty() && !nick.equals("null")) {
                                displayNick = nick;
                            }
                            leftAvatarUrl = cur.getString(cur.getColumnIndexOrThrow("Image"));
                        }
                        cur.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (!socket.AutoReconnectWebSocket.isGroupChat && displayNick.startsWith(context.getString(R.string.chat_member_prefix, "").trim())) {
                        displayNick = SPUtils.getInstance().get("friend_nickname", context.getString(R.string.chat_default_friend));
                        if (leftAvatarUrl == null || leftAvatarUrl.isEmpty()) {
                            leftAvatarUrl = SPUtils.getInstance().get("friend_image", "");
                        }
                    }
                    this.txtSenderNickname.setText(displayNick);

                    Glide.with(context)
                            .load(leftAvatarUrl)
                            .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(), new com.bumptech.glide.load.resource.bitmap.CircleCrop())
                            .into(LeftImage);
                }

                if (!_Yingyong.equals("")) {
                    this.txtYingyong.setText("#" + _Yingyong);
                    this.txtYingyong.setVisibility(View.VISIBLE);
                } else {
                    this.txtYingyong.setVisibility(View.GONE);
                }

                this.leftBox.setVisibility(View.VISIBLE);

                if (!_Mediaurl.equals("null") && !_Mediaurl.equals("") && !_Mediaurl.equals("{}") && !_Mediaurl.startsWith("https://")) {
                    this.leftBox.setVisibility(View.GONE);
                    if (this.leftLocationBox != null) this.leftLocationBox.setVisibility(View.GONE);

                    String path, thumb_path = "", type, url, thumb = "";
                    int duration = 0;
                    try {
                        JSONObject jsonObject = new JSONObject(_Mediaurl);
                        type = jsonObject.getString("type");
                        path = jsonObject.getString("path");
                        url = jsonObject.getString("url");
                        duration = jsonObject.getInt("duration");
                        if (jsonObject.has("thumb_path")) thumb_path = jsonObject.getString("thumb_path");
                        if (jsonObject.has("thumb")) thumb = jsonObject.getString("thumb");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    if (type.equals("IMAGE") || type.equals("GIF") || type.equals("VIDEO") || type.equals("AUDIO")) {
                        this.leftMediaBox.setVisibility(View.VISIBLE);
                        if (type.equals("IMAGE")) {
                            this.left_file_icon.setImageDrawable(that.getDrawable(R.drawable.chat_tupian));
                        } else if (type.equals("GIF")) {
                            this.left_file_icon.setImageDrawable(that.getDrawable(R.drawable.chat_gif));
                        } else if (type.equals("VIDEO")) {
                            this.leftMediaContent.setText(duration / 1000 + "'");
                            this.left_file_icon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.chat_shiping));
                        } else if (type.equals("AUDIO")) {
                            this.leftMediaContent.setText(duration / 1000 + "'");
                            if (isActive && exoPlayer != null) {
                                if (exoPlayer.isPlaying()) {
                                    this.btnLeftPauseConfirm.setVisibility(View.GONE);
                                    Glide.with(context).asGif().load(R.raw.playsound).into(this.left_file_icon);
                                } else {
                                    this.btnLeftPauseConfirm.setVisibility(View.VISIBLE);
                                    this.btnLeftPauseConfirm.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_video_play));
                                    Glide.with(context).load(ContextCompat.getDrawable(context, R.drawable.sound)).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.left_file_icon);
                                }
                            } else {
                                this.btnLeftPauseConfirm.setVisibility(View.VISIBLE);
                                this.btnLeftPauseConfirm.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_video_play));
                                Glide.with(context).load(ContextCompat.getDrawable(context, R.drawable.sound)).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.left_file_icon);
                            }
                        }

                        if (!type.equals("AUDIO")) {
                            this.btnLeftPauseConfirm.setVisibility(View.VISIBLE);
                            this.btnLeftPauseConfirm.setImageDrawable(that.getDrawable(R.drawable.yanjing));
                            File thumb_file = new File(App.Folder + "/" + thumb_path);
                            if (!thumb_file.exists()) {
                                if (thumb != null && !thumb.isEmpty() && thumb.startsWith("http")) {
                                    ImageLoadUtils.loadEncryptedImage(that, thumb, this.left_file_icon, thumb_path);
                                } else {
                                    this.left_file_icon.setImageResource(R.drawable.chat_shiping);
                                }
                            } else {
                                Glide.with(activity_chat.that).load(App.Folder + "/" + thumb_path).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.left_file_icon);
                            }
                        }
                    }

                } else if (isLocationMsg) {
                    this.leftBox.setVisibility(View.GONE);
                    this.leftMediaBox.setVisibility(View.GONE);
                    this.txtMessage.setVisibility(View.GONE);

                    if (this.leftLocationBox != null) {
                        this.leftLocationBox.setVisibility(View.VISIBLE);
                        this.tvLeftLocTitle.setText(finalTitle);
                        this.tvLeftLocAddress.setText(locAddress);

                        loadLocationMap(context, finalLat, finalLng, this.ivLeftLocMap);

                        this.leftLocationBox.setOnClickListener(v -> openNavigation(context, finalLat, finalLng, finalTitle));
                    }
                } else {
                    this.leftMediaBox.setVisibility(View.GONE);
                    if (this.leftLocationBox != null) this.leftLocationBox.setVisibility(View.GONE);

                    this.txtMessage.setVisibility(View.VISIBLE);
                    this.txtMessage.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                    String displayMsg = shortenUrlsForDisplay(_Message);
                    ApngTextView.setText(App.AppContext, txtMessage, displayMsg, 80);
                    applyUrlSpans(txtMessage, false);
                    if (_IsRead == 2 || _IsRead == 5) {
                        this.txtMessage.setTextColor(that.getResources().getColor(R.color.gay));
                    }
                }

                txtTime.setText(SEND_TIME);

                if (_IsRead == 5) {
                    this.leftMediaBox.setVisibility(View.GONE);
                    this.leftBox.setVisibility(View.VISIBLE);
                    if (this.leftLocationBox != null) this.leftLocationBox.setVisibility(View.GONE);
                    this.txtYingyong.setVisibility(View.VISIBLE);
                    this.txtMessage.setVisibility(View.GONE);
                    this.txtYingyong.setText(Html.fromHtml(context.getString(R.string.chat_msg_recalled)));
                }

                boolean switch_zidongfenhui = SPUtils.getInstance().get("switch_zidongfenhui", false);
                if (switch_zidongfenhui) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> ChatUtils.autoDeleteMessageById(_MessageId), 15000);
                }

            } else {
                Show_Chat_Right.setVisibility(View.VISIBLE);
                Show_Chat_Left.setVisibility(View.GONE);

                if (_Progress > 0 && _Progress < 100 && !_Mediaurl.equals("")) {
                    this.rightMediaContent.setText(_Progress + "%");
                }

                if (!_Yingyong.equals("")) {
                    this.txtYingyongRight.setText("#" + StringUtils.safeSubstring(_Yingyong, 0, 20));
                    this.txtYingyongRight.setVisibility(View.VISIBLE);
                } else {
                    this.txtYingyongRight.setVisibility(View.GONE);
                }

                this.rightBox.setVisibility(View.VISIBLE);

                if (!_Mediaurl.equals("null") && !_Mediaurl.equals("") && !_Mediaurl.equals("{}") && !_Mediaurl.startsWith("https://")) {
                    this.rightBox.setVisibility(View.GONE);
                    if (this.rightLocationBox != null) this.rightLocationBox.setVisibility(View.GONE);

                    int duration = 0;
                    try {
                        String path = "", thumb_path = "", type, url, thumb = "";
                        JSONObject jsonObject = new JSONObject(_Mediaurl);
                        type = jsonObject.getString("type");
                        if (jsonObject.has("path")) path = jsonObject.getString("path");
                        if (jsonObject.has("url")) url = jsonObject.getString("url");
                        if (jsonObject.has("duration")) duration = jsonObject.getInt("duration");
                        if (jsonObject.has("thumb_path")) thumb_path = jsonObject.getString("thumb_path");
                        if (jsonObject.has("thumb")) thumb = jsonObject.getString("thumb");

                        if (type.equals("IMAGE") || type.equals("GIF") || type.equals("VIDEO") || type.equals("AUDIO")) {
                            this.rightMediaBox.setVisibility(View.VISIBLE);

                            if (type.equals("IMAGE")) {
                                this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                this.btnRightPauseConfirm.setImageDrawable(that.getDrawable(R.drawable.yanjing));
                            } else if (type.equals("GIF")) {
                                this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                this.btnRightPauseConfirm.setImageDrawable(that.getDrawable(R.drawable.yanjing));
                            } else if (type.equals("VIDEO")) {
                                this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                this.btnRightPauseConfirm.setImageDrawable(that.getDrawable(R.drawable.yanjing));
                                if (_Progress == 0) this.rightMediaContent.setText(duration / 1000 + "'");
                            } else if (type.equals("AUDIO")) {
                                this.rightMediaContent.setText(duration / 1000 + "'");
                                if (isActive && exoPlayer != null) {
                                    if (exoPlayer.isPlaying()) {
                                        this.btnRightPauseConfirm.setVisibility(View.GONE);
                                        Glide.with(context).asGif().load(R.raw.playsound).into(this.right_file_icon);
                                    } else {
                                        this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                        this.btnRightPauseConfirm.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_video_play));
                                        Glide.with(context).load(ContextCompat.getDrawable(context, R.drawable.sound)).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.right_file_icon);
                                    }
                                } else {
                                    this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                    this.btnRightPauseConfirm.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_video_play));
                                    Glide.with(context).load(ContextCompat.getDrawable(context, R.drawable.sound)).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.right_file_icon);
                                }
                            }

                            if (!type.equals("AUDIO")) {
                                this.btnRightPauseConfirm.setVisibility(View.VISIBLE);
                                this.btnRightPauseConfirm.setImageDrawable(that.getDrawable(R.drawable.yanjing));
                                File thumb_file = new File(App.Folder + "/" + thumb_path);
                                if (!thumb_file.exists()) {
                                    if (thumb != null && !thumb.isEmpty() && thumb.startsWith("http")) {
                                        ImageLoadUtils.loadEncryptedImage(that, thumb, this.right_file_icon, thumb_path);
                                    } else {
                                        this.right_file_icon.setImageResource(R.drawable.chat_shiping);
                                    }
                                } else {
                                    Glide.with(activity_chat.that).load(App.Folder + "/" + thumb_path).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(this.right_file_icon);
                                }
                            }
                        }

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else if (isLocationMsg) {
                    this.rightBox.setVisibility(View.GONE);
                    this.rightMediaBox.setVisibility(View.GONE);
                    this.txtMessageRight.setVisibility(View.GONE);

                    if (this.rightLocationBox != null) {
                        this.rightLocationBox.setVisibility(View.VISIBLE);
                        this.tvRightLocTitle.setText(finalTitle);
                        this.tvRightLocAddress.setText(locAddress);

                        loadLocationMap(context, finalLat, finalLng, this.ivRightLocMap);

                        this.rightLocationBox.setOnClickListener(v -> openNavigation(context, finalLat, finalLng, finalTitle));
                    }
                } else {
                    this.rightMediaBox.setVisibility(View.GONE);
                    if (this.rightLocationBox != null) this.rightLocationBox.setVisibility(View.GONE);
                    this.txtMessageRight.setVisibility(View.VISIBLE);
                    String displayMsg = shortenUrlsForDisplay(_Message);
                    ApngTextView.setText(App.AppContext, txtMessageRight, displayMsg, 80);
                    applyUrlSpans(txtMessageRight, true);
                }

                this.RightYidu.setVisibility(View.GONE);
                this.RightError.setVisibility(View.GONE);
                this.txtError.setVisibility(View.GONE);

                if (_IsRead == 0) {
                    SEND_TIME = context.getString(R.string.chat_msg_sending);
                } else if (_IsRead == 3) {
                    SEND_TIME = DateTimeUtils.getTimeAgo(Long.parseLong(_Time));
                    this.RightYidu.setVisibility(View.VISIBLE);
                    this.RightYidu.setImageResource(R.drawable.yidu);
                } else if (_IsRead == 5) {
                    this.rightBox.setVisibility(View.VISIBLE);
                    this.rightMediaBox.setVisibility(View.GONE);
                    if (this.rightLocationBox != null) this.rightLocationBox.setVisibility(View.GONE);
                    this.txtYingyongRight.setVisibility(View.VISIBLE);
                    this.txtMessageRight.setVisibility(View.GONE);
                    this.txtYingyongRight.setText(Html.fromHtml(context.getString(R.string.chat_msg_recalled_with_edit)));
                } else if (_IsRead == 6) {
                    SEND_TIME = context.getString(R.string.chat_msg_offline_delivered);
                    this.RightYidu.setVisibility(View.VISIBLE);
                    this.RightYidu.setImageResource(R.drawable.lixian);
                } else if (_IsRead == 7) {
                    SEND_TIME = context.getString(R.string.chat_msg_send_failed_retry, DateTimeUtils.getTimeAgo(Long.parseLong(_Time)));
                    this.RightYidu.setVisibility(View.VISIBLE);
                    this.RightYidu.setImageResource(R.drawable.error);
                }

                this.txtTimeRight.setVisibility(View.VISIBLE);
                this.txtTimeRight.setText(SEND_TIME);
                this.RightImage.setVisibility(View.VISIBLE);

                String Image = SPUtils.getInstance().get("image", "");
                Glide.with(context)
                        .load(Image)
                        .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(), new com.bumptech.glide.load.resource.bitmap.CircleCrop())
                        .into(RightImage);
            }
        }
    }

    private String getStaticMapUrl(double lat, double lng) {
        if (lat == 0 || lng == 0) return "";
        String amapWebKey = "735deb4162049ae6c753de33ff853371";
        return "https://restapi.amap.com/v3/staticmap?"
                + "key=" + amapWebKey
                + "&location=" + lng + "," + lat + "&zoom=15"
                + "&size=440*200"
                + "&scale=2"
                + "&markers=mid,,:" + lng + "," + lat;
    }

    private static void openNavigation(Context context, double lat, double lng, String name) {
        try {
            String amapUri = "amapuri://route/plan/?dlat=" + lat + "&dlon=" + lng + "&dname=" + Uri.encode(name) + "&dev=0&t=0";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(amapUri));
            intent.setPackage("com.autonavi.minimap");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Uri geoUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + Uri.encode(name));
                Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(geoIntent);
            } catch (Exception ex) {
                try {
                    String webUrl = "https://uri.amap.com/marker?position=" + lng + "," + lat + "&name=" + Uri.encode(name);
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(webIntent);
                } catch (Exception ex2) {
                    FBMessage.Show(context, context.getString(R.string.chat_loc_no_map_app));
                }
            }
        }
    }

    public void Shanchu(String _MessageID, int position) {
        App.db.execSQL("DELETE FROM chat where MessageId='" + _MessageID + "'");
        mData.remove(position);
        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
            that.runOnUiThread(() -> notifyDataSetChanged());
        }
    }

    public void ClickItem(int _ID, String _MessageID, String _Message, String _Mediaurl, int _FromUID, int _ToUID, ViewHolder holder, int position) {
        Context context = that != null ? that : App.AppContext;
        if (!_Mediaurl.equals("null") && !_Mediaurl.equals("") && !_Mediaurl.equals("{}") && !_Mediaurl.startsWith("https://")) {
            String path, type, url;
            try {
                JSONObject jsonObject = new JSONObject(_Mediaurl);
                type = jsonObject.getString("type");
                path = jsonObject.getString("path");
                Module_Chat msg = mData.get(position);

                if (App.UID == _FromUID) {
                    File file = new File(App.Folder + "/" + path);
                    if (file.exists()) {
                        if (type.equals("AUDIO")) {
                            final String finalPath = App.Folder + "/" + path;
                            if (_ToUID != App.UID) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                            } else {
                                holder.txtTime.setVisibility(View.VISIBLE);
                            }

                            new Thread(() -> {
                                try {
                                    byte[] enc = AudioRecorderUtils.readEncryptedFile(finalPath);
                                    byte[] dec = AESUtils.decrypt_byte(App.AppContext,enc);
                                    String tempfile = App.Folder + "/Cache/temp.mp3";
                                    File tempFile = new File(tempfile);
                                    FileOutputStream fos = new FileOutputStream(tempFile);
                                    fos.write(dec);
                                    fos.close();

                                    if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                                        that.runOnUiThread(() -> PlayAudio(tempfile, "", position));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    if (that != null) that.runOnUiThread(() -> FBMessage.Show(that, that.getString(R.string.chat_audio_parse_failed)));
                                }
                            }).start();
                        } else {
                            url = jsonObject.getString("url");
                            Intent it = new Intent();
                            it.setClass(App.AppContext, activity_media.class);
                            Bundle bundle_chat = new Bundle();
                            bundle_chat.putString("Mediaurl", path);
                            bundle_chat.putString("Url", url);
                            it.putExtras(bundle_chat);
                            startActivity(it);
                        }
                    } else {
                        FBMessage.Show(that, that.getString(R.string.chat_file_expired));
                    }
                } else {
                    if (msg.isDownloading) {
                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            Toast.makeText(that, that.getString(R.string.chat_file_downloading), Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    File f = new File(App.Folder + "/" + path);
                    if (f.exists()) {
                        if (type.equals("AUDIO")) {
                            final String finalPath = App.Folder + "/" + path;
                            if (_ToUID != App.UID) {
                                holder.txtTimeRight.setVisibility(View.VISIBLE);
                            } else {
                                holder.txtTime.setVisibility(View.VISIBLE);
                            }

                            new Thread(() -> {
                                try {
                                    byte[] enc = AudioRecorderUtils.readEncryptedFile(finalPath);
                                    byte[] dec = AESUtils.decrypt_byte(App.AppContext,enc);
                                    String tempfile = App.Folder + "/Cache/temp.mp3";
                                    File tempFile = new File(tempfile);
                                    FileOutputStream fos = new FileOutputStream(tempFile);
                                    fos.write(dec);
                                    fos.close();

                                    if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                                        that.runOnUiThread(() -> PlayAudio(tempfile, "", position));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    if (that != null) that.runOnUiThread(() -> FBMessage.Show(that, that.getString(R.string.chat_audio_parse_failed)));
                                }
                            }).start();
                        } else {
                            url = jsonObject.getString("url");
                            Intent it = new Intent();
                            it.setClass(App.AppContext, activity_media.class);
                            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Bundle bundle_chat = new Bundle();
                            bundle_chat.putString("Mediaurl", path);
                            bundle_chat.putString("Url", url);
                            it.putExtras(bundle_chat);
                            startActivity(it);
                        }
                    } else {
                        if (type.equals("AUDIO")) {
                            url = jsonObject.getString("url");
                            msg.isDownloading = true;
                            msg._Progress = 0;
                            startVideoDownload(msg, position, url, type);
                        } else {
                            url = jsonObject.getString("url");
                            msg.isDownloading = true;
                            msg._Progress = 0;
                            Bundle payload = new Bundle();
                            payload.putString("UPDATE_PROGRESS", "true");
                            notifyItemChanged(position, payload);
                            startVideoDownload(msg, position, url, type);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("TBA", "ERROR=" + e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            if (_ToUID != App.UID) {
                holder.txtTimeRight.setVisibility(View.VISIBLE);
            } else {
                holder.txtTime.setVisibility(View.VISIBLE);
            }
        }

        if (_Mediaurl == null || _Mediaurl.equals("") || _Mediaurl.equals("null") || _Mediaurl.equals("{}")) {
            if (_Message != null && !_Message.isEmpty()) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(http|https)://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+");
                java.util.regex.Matcher matcher = pattern.matcher(_Message);
                if (matcher.find()) {
                    String extractedUrl = matcher.group();
                    if (extractedUrl != null && !extractedUrl.isEmpty()) {
                        Context activityContext = holder.itemView.getContext();
                        Intent it = new Intent(activityContext, activity_web.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("url", extractedUrl);
                        it.putExtras(bundle);
                        activityContext.startActivity(it);
                        return;
                    }
                }
            }
        }
    }

    public void Langdu(String _Message) { cPosition = -1; }

    public void Yinyong(String _Message) {
        activity_chat.YingyongBox.setVisibility(View.VISIBLE);
        activity_chat.txtYingyong.setText(_Message);
        try {
            Thread.sleep(100);
            InputMethodManager imm = (InputMethodManager) App.AppContext.getSystemService(App.AppContext.INPUT_METHOD_SERVICE);
            imm.showSoftInput(activity_chat.edit_message, InputMethodManager.SHOW_IMPLICIT);
            cPosition = -1;
        } catch (Exception e) {}
    }

    public void Fuzhi(String _Message) {
        ClipboardManager clipboard = (ClipboardManager) that.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", _Message);
        clipboard.setPrimaryClip(clip);
        FBMessage.Show(that, that.getString(R.string.chat_copy_success));
        cPosition = -1;
    }

    public void Chehui(int _ID, int _ToUID, int _FromUID, String _Message, String _MessageID, String _Time, int position) {
        cPosition = -1;
        App.db.execSQL("update chat set IsRead=5 where MessageId='" + _MessageID + "'");
        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
            that.runOnUiThread(() -> {
                activity_chat.Datas_Chat.set(position, new Module_Chat(_ID, _Message, _MessageID, _ToUID, _FromUID, "", _Time, _Message, "", 5, 0));
                notifyDataSetChanged();
            });
        }

        if (Data.MemberBillingBridge.get() == null) return;

        Data.MemberBillingBridge.get().recallMessage(that, App.UID, _MessageID, new Data.IMemberBilling.RecallCallback() {
            @Override
            public void onSuccess() {
                try {
                    JSONObject jo = new JSONObject();
                    jo.put("type", "Chehui");
                    jo.put("uid", App.UID);
                    JSONObject jo_data = new JSONObject();
                    jo_data.put("_FromUID", App.UID);
                    jo_data.put("_ToUid", _ToUID);
                    jo_data.put("_MessageId", _MessageID);
                    jo.put("data", jo_data);

                    if (AutoReconnectWebSocket.socket != null) {
                        AutoReconnectWebSocket.socket.sendMessage(jo.toString());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e("AUTH", "撤回消息服务端失败: " + errorMessage);
            }
        });
    }

    public static void PlayAudio(String path, String url, final int targetPosition) {
        if (exoPlayer != null && PlayPosition == targetPosition && targetPosition != -1) {
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
                stopAudioProgress();
            } else {
                exoPlayer.play();
                startAudioProgress();
            }
            return;
        }

        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                stopAudioProgress();
            } catch (Exception e) {}

            final int oldPlayPos = PlayPosition;
            PlayPosition = -1;
            if (oldPlayPos != -1 && oldPlayPos < activity_chat.Datas_Chat.size()) {
                if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                    if (activity_chat.mAdapter_Chat != null) {
                        activity_chat.mAdapter_Chat.notifyItemChanged(oldPlayPos);
                    }
                }
            }
        } else {
            exoPlayer = new ExoPlayer.Builder(App.AppContext).build();
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (PlayPosition != -1 && activity_chat.mAdapter_Chat != null) {
                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            that.runOnUiThread(() -> {
                                activity_chat.mAdapter_Chat.notifyItemChanged(PlayPosition);
                            });
                        }
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopAudioProgress();
                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            that.runOnUiThread(() -> {
                                if (activity_chat.mAdapter_Chat != null && PlayPosition != -1) {
                                    int oldPos = PlayPosition;
                                    PlayPosition = -1;
                                    activity_chat.mAdapter_Chat.notifyItemChanged(oldPos);
                                }
                            });
                        }
                    } else if (playbackState == Player.STATE_READY) {
                        if (exoPlayer.getPlayWhenReady()) {
                            startAudioProgress();
                        }
                    }
                }
            });
        }

        PlayPosition = targetPosition;

        File file = new File(path);
        if (!file.exists()) {
            path = url;
            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    Upload upload = new Upload(0);
                    upload.Download_IMG(url, "");
                    Looper.loop();
                }
            }.start();
        }

        MediaItem mediaItem = MediaItem.fromUri(path);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    private void startVideoDownload(Module_Chat msg, int position, String url, String type) {
        Log.e("Media", "isDown=" + msg.isDownloading);

        String fileName = url.substring(url.lastIndexOf('/') + 1);
        File targetFile = new File(App.Folder + "/Cache/", fileName);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                msg.isDownloading = false;
                if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                    that.runOnUiThread(() -> {
                        Bundle payload = new Bundle();
                        payload.putString("UPDATE_PROGRESS", "true");
                        notifyItemChanged(position, payload);
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    msg.isDownloading = false;
                    if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                        that.runOnUiThread(() -> {
                            Bundle payload = new Bundle();
                            payload.putString("UPDATE_PROGRESS", "true");
                            notifyItemChanged(position, payload);
                            FBMessage.Show(that, that.getString(R.string.chat_network_error, response.code()));
                        });
                    }
                    return;
                }

                InputStream is = null;
                FileOutputStream fos = null;
                long lastRefreshTime = System.currentTimeMillis();

                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();
                    fos = new FileOutputStream(targetFile);
                    long sum = 0;
                    int len;
                    byte[] buf = new byte[4096];

                    while ((len = is.read(buf)) != -1) {
                        sum += len;
                        fos.write(buf, 0, len);
                        int progress = (int) (sum * 100.0f / total);

                        if (System.currentTimeMillis() - lastRefreshTime > 100 || progress == 100) {
                            msg._Progress = progress;
                            lastRefreshTime = System.currentTimeMillis();
                            if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                                that.runOnUiThread(() -> {
                                    Bundle payload = new Bundle();
                                    payload.putString("UPDATE_PROGRESS", "true");
                                    notifyItemChanged(position, payload);
                                });
                            }
                        }
                    }
                    fos.flush();
                    fos.close();

                    FileManage.getMedia(targetFile.getAbsolutePath());

                    msg.isDownloading = false;
                    msg.isDownloaded = true;
                    msg.localFilePath = targetFile.getAbsolutePath();

                    if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                        that.runOnUiThread(() -> {
                            Bundle payload = new Bundle();
                            payload.putString("UPDATE_PROGRESS", "true");
                            notifyItemChanged(position, payload);
                        });
                    }

                    if ("AUDIO".equals(type)) {
                        byte[] enc = AudioRecorderUtils.readEncryptedFile(targetFile.getAbsolutePath());
                        byte[] dec = AESUtils.decrypt_byte(App.AppContext,enc);
                        String tempfile = App.Folder + "/Cache/temp.mp3";
                        File tempFile = new File(tempfile);
                        FileOutputStream fos2 = new FileOutputStream(tempFile);
                        fos2.write(dec);
                        fos2.close();

                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            that.runOnUiThread(() -> PlayAudio(tempfile, "", position));
                        }
                    } else {
                        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                            that.runOnUiThread(() -> {
                                Intent it = new Intent();
                                it.setClass(App.AppContext, activity_media.class);
                                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Bundle bundle_chat = new Bundle();
                                bundle_chat.putString("Mediaurl", "Cache/" + fileName);
                                it.putExtras(bundle_chat);
                                startActivity(it);
                            });
                        }
                    }
                } catch (Exception e) {
                    msg.isDownloading = false;
                    if (that != null && !that.isFinishing() && !that.isDestroyed()) {
                        that.runOnUiThread(() -> {
                            Bundle payload = new Bundle();
                            payload.putString("UPDATE_PROGRESS", "true");
                            notifyItemChanged(position, payload);
                            FBMessage.Show(that, that.getString(R.string.chat_write_failed, e.getMessage()));
                            Log.e("TBA", "写入失败" + e.getMessage());
                        });
                    }
                } finally {
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}