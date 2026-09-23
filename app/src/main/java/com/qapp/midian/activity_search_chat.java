package com.qapp.midian;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Utils.StatusBarUtil;

public class activity_search_chat extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etSearchKeyword;
    private ImageView btnPickDate;
    private LinearLayout layoutDateFilterTag;
    private TextView tvDateFilterText;
    private ImageView btnClearDate;
    private RecyclerView rvSearchResults;
    private TextView tvEmpty;

    private int friendUid;
    private String selectedDate = ""; // 格式 "yyyy-MM-dd"
    private List<ChatMessageItem> messageList = new ArrayList<>();
    private ChatSearchAdapter adapter;

    public static class ChatMessageItem {
        public long msgId;
        public int fromUid;
        public String content;
        public long time;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_chat);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        friendUid = getIntent().getIntExtra("friend_uid", 0);

        initViews();
        initEvents();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        etSearchKeyword = findViewById(R.id.et_search_keyword);
        btnPickDate = findViewById(R.id.btn_pick_date);
        layoutDateFilterTag = findViewById(R.id.layout_date_filter_tag);
        tvDateFilterText = findViewById(R.id.tv_date_filter_text);
        btnClearDate = findViewById(R.id.btn_clear_date);
        rvSearchResults = findViewById(R.id.rv_search_results);
        tvEmpty = findViewById(R.id.tv_empty);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatSearchAdapter();
        rvSearchResults.setAdapter(adapter);
    }

    private void initEvents() {
        btnBack.setOnClickListener(v -> finish());

        // 输入关键词实时检索
        etSearchKeyword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                queryChatMessages();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 日期选择器
        btnPickDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                layoutDateFilterTag.setVisibility(View.VISIBLE);
                tvDateFilterText.setText("筛选日期: " + selectedDate);
                queryChatMessages();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });

        // 清除日期筛选
        btnClearDate.setOnClickListener(v -> {
            selectedDate = "";
            layoutDateFilterTag.setVisibility(View.GONE);
            queryChatMessages();
        });
    }

    /**
     * 核心数据库查询（结合关键词模糊匹配与日期范围）
     * 已严格按照数据库截图结构修复列名：FromUid, ToUid, Message, Inputtime
     */
    private void queryChatMessages() {
        String keyword = etSearchKeyword.getText().toString().trim();
        messageList.clear();

        // 没输入关键词且没选日期时，清空列表
        if (TextUtils.isEmpty(keyword) && TextUtils.isEmpty(selectedDate)) {
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(View.GONE);
            return;
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM chat WHERE ");
            // ⚠️ 严格匹配大小写：FromUid, ToUid
            sql.append("((FromUid=").append(App.UID).append(" AND ToUid=").append(friendUid).append(") OR ");
            sql.append("(FromUid=").append(friendUid).append(" AND ToUid=").append(App.UID).append("))");

            // ⚠️ 文本模糊匹配，列名为 Message
            if (!TextUtils.isEmpty(keyword)) {
                sql.append(" AND Message LIKE '%").append(keyword.replace("'", "''")).append("%'");
            }

            // ⚠️ 日期范围过滤，列名为 Inputtime，单位为毫秒
            if (!TextUtils.isEmpty(selectedDate)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date d = sdf.parse(selectedDate);
                if (d != null) {
                    long startTimestamp = d.getTime();
                    long endTimestamp = startTimestamp + 24 * 60 * 60 * 1000L - 1;
                    sql.append(" AND (Inputtime >= ").append(startTimestamp).append(" AND Inputtime <= ").append(endTimestamp).append(")");
                }
            }

            sql.append(" ORDER BY Inputtime DESC");

            // Log.d("SearchChat", "执行SQL: " + sql.toString()); // 调试用，可删除

            Cursor cursor = App.db.rawQuery(sql.toString(), null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ChatMessageItem item = new ChatMessageItem();
                    item.msgId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));

                    // ⚠️ 使用正确的列名取值
                    item.fromUid = cursor.getInt(cursor.getColumnIndexOrThrow("FromUid"));

                    String msg = cursor.getString(cursor.getColumnIndexOrThrow("Message"));
                    item.content = msg != null ? msg : "";

                    item.time = cursor.getLong(cursor.getColumnIndexOrThrow("Inputtime"));

                    // 过滤掉纯媒体占位符（可选）
                    if (item.content.equals("[/MEDIA]")) {
                        item.content = "[图片/视频]";
                    }

                    messageList.add(item);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(messageList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 长按跳转到聊天界面的对应位置
     */
    private void jumpToChatPosition(ChatMessageItem item) {
        new AlertDialog.Builder(this)
                .setTitle("定位到聊天记录")
                .setMessage("是否前往该消息在聊天记录中的位置？")
                .setPositiveButton("前往", (dialog, which) -> {
                    Intent intent = new Intent(this, activity_chat.class);
                    intent.putExtra("target_msg_id", item.msgId);
                    intent.putExtra("friend_uid", friendUid);
                    // 复用已有的 activity_chat
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // RecyclerView 适配器
    private class ChatSearchAdapter extends RecyclerView.Adapter<ChatSearchAdapter.VH> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_chat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatMessageItem item = messageList.get(position);
            holder.tvSender.setText(item.fromUid == App.UID ? "我" : "对方");
            holder.tvTime.setText(sdf.format(new Date(item.time)));

            // 搜索关键词高亮显示
            String keyword = etSearchKeyword.getText().toString().trim();
            if (!TextUtils.isEmpty(keyword) && item.content != null && item.content.contains(keyword)) {
                SpannableString spannable = new SpannableString(item.content);
                int index = item.content.indexOf(keyword);
                while (index >= 0) {
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#1AAD19")), index, index + keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = item.content.indexOf(keyword, index + keyword.length());
                }
                holder.tvContent.setText(spannable);
            } else {
                holder.tvContent.setText(item.content);
            }

            // 长按定位跳转
            holder.itemView.setOnLongClickListener(v -> {
                jumpToChatPosition(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return messageList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvSender, tvTime, tvContent;
            public VH(@NonNull View itemView) {
                super(itemView);
                tvSender = itemView.findViewById(R.id.tv_sender);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvContent = itemView.findViewById(R.id.tv_content);
            }
        }
    }
}