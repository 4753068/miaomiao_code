package com.qapp.midian;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import Utils.StatusBarUtil;
import socket.AutoReconnectWebSocket;

public class activity_media extends AppCompatActivity {
    public static activity_media that = null;

    private ViewPager2 viewPager;
    private MediaPagerAdapter pagerAdapter;
    private final List<MediaPagerAdapter.MediaItemData> mediaDataList = new ArrayList<>();

    public static TextView DownloadTis;
    public static String Mediaurl = null, Url = null;
    private int currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 禁止截屏和录屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_showmedia);
        StatusBarUtil.setStatusBarMode(this, true, R.color.black);
        that = this;

        DownloadTis = findViewById(R.id.DownloadTis);
        viewPager = findViewById(R.id.viewPager);

        // 1. 获取从聊天界面点击传过来的当前媒体数据路径
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Mediaurl = bundle.getString("Mediaurl");
            Url = bundle.getString("Url");
            Log.e("MEDIA===", App.Folder + "/" + Mediaurl);
        }

        // 2. 从数据库加载完整的聊天媒体文件队列，并精确定位当前点击的图片位置
        loadMediaListAndInitViewPager();
    }

    /**
     * 从数据库正序装载媒体队列，并初始化 ViewPager2
     */
    private void loadMediaListAndInitViewPager() {
        mediaDataList.clear();
        int clickedIndex = 0;
        int positionCounter = 0;

        // ✨ 核心修复点 1：兼容群聊与单聊的查询条件
        String sql_count;
        if (AutoReconnectWebSocket.isGroupChat) {
            sql_count = "SELECT * FROM chat WHERE ToUid=" + activity_chat.Friend_UID + " AND Mediaurl<>'' AND Mediaurl<>'null' ORDER BY id ASC";
        } else {
            sql_count = "SELECT * FROM chat WHERE ((ToUid=" + App.UID + " AND FromUid=" + activity_chat.Friend_UID + ") OR (ToUid=" + activity_chat.Friend_UID + " AND FromUid=" + App.UID + ")) AND Mediaurl<>'' AND Mediaurl<>'null' ORDER BY id ASC";
        }

        Cursor cursor_count = App.db.rawQuery(sql_count, null);

        if (cursor_count != null) {
            while (cursor_count.moveToNext()) {
                String mediaUrlJson = cursor_count.getString(cursor_count.getColumnIndexOrThrow("Mediaurl"));
                if (mediaUrlJson != null && !mediaUrlJson.isEmpty() && !mediaUrlJson.equals("{}")) {
                    try {
                        org.json.JSONObject jsonObject = new org.json.JSONObject(mediaUrlJson);
                        String path = jsonObject.optString("path");
                        String _type = jsonObject.optString("type");

                        // ✨ 核心修复点 2：使用 File 构造函数安全规范路径，避免双斜杠
                        File tempFile = new File(App.Folder, path);
                        String _path = tempFile.getAbsolutePath();

                        if (!path.isEmpty() && !_type.equals("AUDIO") && tempFile.exists()) {
                            mediaDataList.add(new MediaPagerAdapter.MediaItemData(_path, _type));

                            // 路径对比定位索引
                            if (Mediaurl != null && path.trim().equals(Mediaurl.trim())) {
                                clickedIndex = positionCounter;
                            }
                            positionCounter++;
                        }
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            cursor_count.close();
        }

        // 兜底策略：如果数据库查询为空，但当前传递进来的文件确实存在，将其作为单张展示
        if (mediaDataList.isEmpty() && Mediaurl != null) {
            File fallbackFile = new File(App.Folder, Mediaurl);
            if (fallbackFile.exists()) {
                mediaDataList.add(new MediaPagerAdapter.MediaItemData(fallbackFile.getAbsolutePath(), "IMAGE"));
            }
        }

        // 驱动 ViewPager2 显示
        if (!mediaDataList.isEmpty()) {
            pagerAdapter = new MediaPagerAdapter(this, mediaDataList);
            viewPager.setAdapter(pagerAdapter);

            // 定位到点击的那张图
            viewPager.setCurrentItem(clickedIndex, false);
            currentPosition = clickedIndex;

            // 监听翻页事件
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    currentPosition = position;

                    MediaPagerAdapter.MediaItemData item = mediaDataList.get(position);
                    if ("VIDEO".equals(item.type)) {
                        triggerVideoPlay(position);
                    } else {
                        if (pagerAdapter != null) {
                            pagerAdapter.stopVideo();
                        }
                    }
                }
            });

            // 首次进入时触发播放判定
            final int finalClickedIndex = clickedIndex;
            viewPager.post(() -> {
                if (finalClickedIndex < mediaDataList.size()) {
                    MediaPagerAdapter.MediaItemData item = mediaDataList.get(finalClickedIndex);
                    if ("VIDEO".equals(item.type)) {
                        triggerVideoPlay(finalClickedIndex);
                    }
                }
            });
        }
    }

    /**
     * 统一视频播放触发函数
     */
    private void triggerVideoPlay(int position) {
        if (viewPager == null || pagerAdapter == null) return;

        viewPager.post(() -> {
            try {
                if (viewPager.getChildCount() > 0) {
                    androidx.recyclerview.widget.RecyclerView recyclerView =
                            (androidx.recyclerview.widget.RecyclerView) viewPager.getChildAt(0);
                    if (recyclerView != null) {
                        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                                recyclerView.findViewHolderForAdapterPosition(position);

                        if (holder instanceof MediaPagerAdapter.ViewHolder) {
                            pagerAdapter.playVideoAtPosition(position, (MediaPagerAdapter.ViewHolder) holder);
                        } else {
                            recyclerView.postDelayed(() -> {
                                androidx.recyclerview.widget.RecyclerView.ViewHolder retryHolder =
                                        recyclerView.findViewHolderForAdapterPosition(position);
                                if (retryHolder instanceof MediaPagerAdapter.ViewHolder) {
                                    pagerAdapter.playVideoAtPosition(position, (MediaPagerAdapter.ViewHolder) retryHolder);
                                }
                            }, 100);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pagerAdapter != null) pagerAdapter.stopVideo();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pagerAdapter != null) pagerAdapter.stopVideo();
        mediaDataList.clear();
        Mediaurl = null;
        Url = null;
        that = null;
    }
}