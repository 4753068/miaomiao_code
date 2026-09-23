package adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Data.ChatUtils;
import Data.FriendUtils;
import Dialog.DialogUtils;
import Utils.DateTimeUtils;
import Utils.FBMessage;
import Utils.SPUtils;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_friend;
import com.qapp.midian.activity_friendsetting;
import com.qapp.midian.activity_group_profile;

public class Adapter_Friend extends RecyclerView.Adapter<Adapter_Friend.ViewHolder> {
    private List<Module_Friend> mData;

    // 存储好友在线状态的 Map，Key 为 Friend_Uid，Value 为是否在线
    private final Map<Integer, Boolean> onlineStatusMap = new HashMap<>();

    // 预创建灰度滤镜常量，避免反复创建造成 GC 压力
    private static final ColorMatrixColorFilter GRAYSCALE_FILTER;
    static {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        GRAYSCALE_FILTER = new ColorMatrixColorFilter(matrix);
    }

    public Adapter_Friend(List<Module_Friend> mData) {
        this.mData = mData;
        sortData();
    }

    /**
     * 排序规则：
     * 1. 新好友申请 (Type == 0) 绝对最优先置顶
     * 2. 用户手动置顶 (通过 SPUtils 存储的状态)，置顶项之间按最后更新时间降序
     * 3. 其他好友和群组，按最后更新时间 (UpdateTime) 降序
     */
    private void sortData() {
        if (this.mData == null || this.mData.isEmpty()) return;

        Collections.sort(this.mData, new Comparator<Module_Friend>() {
            @Override
            public int compare(Module_Friend o1, Module_Friend o2) {
                // 1. 新申请绝对优先
                boolean o1IsApply = (o1.getType() == 0);
                boolean o2IsApply = (o2.getType() == 0);
                if (o1IsApply != o2IsApply) {
                    return o1IsApply ? -1 : 1;
                }

                // 2. 🌟 手动置顶优先（不论是群组还是普通好友）
                boolean o1IsTop = SPUtils.getInstance().get("friend_top_" + o1.getFriendUid(), false);
                boolean o2IsTop = SPUtils.getInstance().get("friend_top_" + o2.getFriendUid(), false);
                if (o1IsTop != o2IsTop) {
                    return o1IsTop ? -1 : 1; // o1 是置顶则排前面
                }

                // 3. 剩余的所有项（包括多个置顶项之间，或非置顶的好友与群组之间），均按最新聊天时间降序排列
                return Long.compare(o2.getUpdateTime(), o1.getUpdateTime());
            }
        });
    }
    public void updateOnlineStatuses(Map<Integer, Boolean> newStatuses) {
        if (newStatuses != null) {
            this.onlineStatusMap.putAll(newStatuses);
            sortData();
            notifyDataSetChanged();
        }
    }

    public void updateSingleOnlineStatus(int targetUid, boolean isOnline) {
        if (this.mData == null) return;

        Boolean prevStatus = this.onlineStatusMap.get(targetUid);
        this.onlineStatusMap.put(targetUid, isOnline);

        if (prevStatus == null || prevStatus != isOnline) {
            sortData();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.module_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            for (Object payload : payloads) {
                if ("ONLINE_STATUS_ONLY".equals(payload)) {
                    int friendId = this.mData.get(position).getFriendUid();
                    int type = this.mData.get(position).getType();
                    holder.applyAvatarFilter(friendId, type);
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;

        if (mData == null || position < 0 || position >= mData.size()) {
            return;
        }

        holder.setData(
                this.mData.get(position).getUid(),
                this.mData.get(position).getFriendUid(),
                this.mData.get(position).getMessage(),
                this.mData.get(position).getUserid(),
                this.mData.get(position).getNickname(),
                this.mData.get(position).getType(),
                this.mData.get(position).getThumb(),
                this.mData.get(position).getMessageNumber(),
                this.mData.get(position).getUpdateTime(),
                this.mData.get(position).getUrl()
        );

        int _FriendId = this.mData.get(position).getFriendUid();
        int _Type = this.mData.get(position).getType();
        String _Nickname = this.mData.get(position).getNickname();
        String UserId = this.mData.get(position).getUserid();

        // 🌟 读取是否置顶，改变背景色作为区分 (浅灰色/白色)
        boolean isTop = SPUtils.getInstance().get("friend_top_" + _FriendId, false);
        holder.itemView.setBackgroundColor(isTop ? Color.parseColor("#F7F8FA") : Color.WHITE);

        holder.thumb.setOnClickListener(v -> {
            Context context = v.getContext();
            if (_Type != 0) {
                if (_Type == 30) {
                    Intent it = new Intent(activity_friend.that, activity_group_profile.class);
                    it.putExtra("group_id", UserId);
                    it.putExtra("local_friend_uid", _FriendId);
                    activity_friend.that.startActivity(it);
                } else {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_friendsetting.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Bundle bundle_chat = new Bundle();
                    bundle_chat.putInt("friend_uid", _FriendId);
                    it.putExtras(bundle_chat);
                    App.AppContext.startActivity(it);
                }
            } else {
                FBMessage.Show(activity_friend.that, context.getString(R.string.friend_not_approved_tip));
            }
        });

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            if (_Type != 0) {
                ChatUtils.ShowChatActivity(_FriendId);
            } else {
                FBMessage.Show(activity_friend.that, context.getString(R.string.friend_not_approved_tip));
            }
        });

        holder.btnTongyi.setOnClickListener(v -> {
            FriendUtils.UpdateType(2, _FriendId);
            holder.btnTongyi.setVisibility(View.GONE);
            holder.btnJujue.setVisibility(View.GONE);
        });

        holder.btnJujue.setOnClickListener(v -> {
            FriendUtils.UpdateType(6, _FriendId);
            holder.btnTongyi.setVisibility(View.GONE);
            holder.btnJujue.setVisibility(View.GONE);
        });

        holder.itemView.setOnLongClickListener(v -> {
            View popupView = LayoutInflater.from(activity_friend.that).inflate(R.layout.popup_friend_list, null);
            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );

            // 🌟 处理置顶逻辑
            TextView friend_top = popupView.findViewById(R.id.friend_top);
            if (friend_top != null) {
                friend_top.setText(isTop ? "取消置顶" : "置顶");
                friend_top.setOnClickListener(v1 -> {
                    // 状态取反并保存
                    SPUtils.getInstance().put("friend_top_" + _FriendId, !isTop);
                    // 重新排序并刷新列表
                    sortData();
                    notifyDataSetChanged();
                    popupWindow.dismiss();
                    FBMessage.Show(activity_friend.that, isTop ? "已取消置顶" : "置顶成功");
                });
            }

            TextView friend_delete = popupView.findViewById(R.id.friend_delete);
            if (friend_delete != null) {
                friend_delete.setOnClickListener(v1 -> {
                    DialogUtils.showDeleteFriendDialog(activity_friend.that, _FriendId);
                    FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                    popupWindow.dismiss();
                });
            }

            TextView friend_clearmessage = popupView.findViewById(R.id.friend_clearmessage);
            if (friend_clearmessage != null) {
                friend_clearmessage.setOnClickListener(v1 -> {
                    DialogUtils.showClearChatDialog(activity_friend.that, _FriendId);
                    popupWindow.dismiss();
                });
            }

            TextView friend_backup = popupView.findViewById(R.id.friend_backup);
            if (friend_backup != null) {
                friend_backup.setOnClickListener(v1 -> {
                    ChatUtils.Daochu_Chat(_FriendId, _Nickname);
                    FBMessage.Show(activity_friend.that, activity_friend.that.getString(R.string.friend_export_success_tip));
                    popupWindow.dismiss();
                });
            }

            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.showAsDropDown(v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return this.mData != null ? this.mData.size() : 0;
    }

    public void updateData(List<Module_Friend> newData) {
        this.mData.clear();
        this.mData.addAll(newData);
        sortData();
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nickname = null, txtupdatetime;
        private TextView message = null;
        private TextView message_number = null, btnTongyi = null, btnJujue;
        private ImageView thumb = null;
        private ImageView jinzhilianxi = null;
        private ImageView ivGroupBadge = null;
        private LinearLayout btnTongyiBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nickname = itemView.findViewById(R.id.nickname);
            thumb = itemView.findViewById(R.id.thumb);
            jinzhilianxi = itemView.findViewById(R.id.jinzhilianxi);
            ivGroupBadge = itemView.findViewById(R.id.ivGroupBadge);
            message_number = itemView.findViewById(R.id.message_number);
            message = itemView.findViewById(R.id.message);
            btnTongyi = itemView.findViewById(R.id.btnTongyi);
            btnJujue = itemView.findViewById(R.id.btnJujue);
            btnTongyiBox = itemView.findViewById(R.id.TongyiBox);
            txtupdatetime = itemView.findViewById(R.id.updatetime);
        }

        // 根据在线状态与关系类型统一控制头像滤镜与模糊
        public void applyAvatarFilter(int friendUid, int type) {
            // 群聊 (type == 30)：保持彩色原图
            if (type == 30) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    thumb.setRenderEffect(null);
                }
                thumb.clearColorFilter();
                thumb.setAlpha(1.0f);
                return;
            }

            // 被拉黑/暂停联系 (type == 3 或 4)：较重的高斯模糊 (5f) + 黑白
            if (type == 3 || type == 4) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RenderEffect blackWhiteEffect = RenderEffect.createColorFilterEffect(GRAYSCALE_FILTER);
                    RenderEffect blurEffect = RenderEffect.createBlurEffect(5f, 5f, Shader.TileMode.CLAMP);
                    thumb.setRenderEffect(RenderEffect.createChainEffect(blurEffect, blackWhiteEffect));
                } else {
                    thumb.setColorFilter(GRAYSCALE_FILTER);
                }
                thumb.setAlpha(1.0f);
                return;
            }

            // 判断好友是否在线
            boolean isOnline = onlineStatusMap.containsKey(friendUid) && Boolean.TRUE.equals(onlineStatusMap.get(friendUid));

            if (isOnline) {
                // 在线：清晰、彩色原图
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    thumb.setRenderEffect(null);
                }
                thumb.clearColorFilter();
                thumb.setAlpha(1.0f);
            } else {
                // 不在线：黑白 + 轻微模糊 (2.5f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RenderEffect blackWhiteEffect = RenderEffect.createColorFilterEffect(GRAYSCALE_FILTER);
                    RenderEffect lightBlurEffect = RenderEffect.createBlurEffect(2.5f, 2.5f, Shader.TileMode.CLAMP);
                    thumb.setRenderEffect(RenderEffect.createChainEffect(lightBlurEffect, blackWhiteEffect));
                    thumb.setAlpha(1.0f);
                } else {
                    thumb.setColorFilter(GRAYSCALE_FILTER);
                    thumb.setAlpha(0.6f); // Android 12 以下设备降级为轻度透明弱化
                }
            }
        }

        public void setData(int Uid, int Friend_Uid, String _Message, String _Userid, String _Nickanme, int _Type, String image, int messageNumber, long updatetime, String url) {
            Context context = itemView.getContext();
            nickname.setText(_Nickanme);
            jinzhilianxi.setVisibility(View.GONE);

            if (_Type == 30) {
                ivGroupBadge.setVisibility(View.VISIBLE);
            } else {
                ivGroupBadge.setVisibility(View.GONE);
            }

            if (_Type == 0) {
                message.setText(context.getString(R.string.friend_new_apply));
                btnTongyiBox.setVisibility(View.VISIBLE);
                btnTongyi.setVisibility(View.VISIBLE);
                btnJujue.setVisibility(View.VISIBLE);
            } else if (_Type == 1) {
                message.setText(context.getString(R.string.friend_wait_approval));
            } else if (_Type == 3 || _Type == 4) {
                jinzhilianxi.setVisibility(View.VISIBLE);
            } else {
                String displayMessage = _Message != null ? _Message : "";
                if (displayMessage.startsWith("{") && displayMessage.contains("\"type\":\"LOCATION\"")) {
                    try {
                        JSONObject locObj = new JSONObject(displayMessage);
                        String defaultLocTitle = context.getString(R.string.friend_loc_default);
                        String address = locObj.optString("title", defaultLocTitle);
                        if (TextUtils.isEmpty(address) || address.equals("我的位置")) {
                            address = locObj.optString("address", defaultLocTitle);
                        }
                        displayMessage = context.getString(R.string.friend_loc_prefix_format, address);
                    } catch (Exception ignored) {
                        displayMessage = context.getString(R.string.friend_loc_prefix);
                    }
                }
                message.setText(displayMessage);

                String SEND_TIME = DateTimeUtils.getTimeAgo(updatetime);
                txtupdatetime.setText(SEND_TIME);
            }

            // 设置头像黑白/模糊/彩色状态
            applyAvatarFilter(Friend_Uid, _Type);

            Object loadSource;
            if (image != null && image.startsWith("http")) {
                loadSource = image;
            } else if (image != null && image.startsWith("/uploads/")) {
                loadSource = App.DataServiceUrl + image;
            } else {
                loadSource = new File(image != null ? image : "");
            }

            Glide.with(thumb.getContext())
                    .load(loadSource)
                    .centerCrop()
                    .transform(new RoundedCorners(8))
                    .placeholder(R.drawable.user_default)
                    .error(R.drawable.user_default)
                    .into(thumb);

            if (messageNumber > 0) {
                message_number.setVisibility(View.VISIBLE);
                if (messageNumber >= 99) {
                    message_number.setText("99+");
                } else {
                    message_number.setText(String.valueOf(messageNumber));
                }
                message_number.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD);
            } else {
                message_number.setVisibility(View.GONE);
            }
        }
    }
}