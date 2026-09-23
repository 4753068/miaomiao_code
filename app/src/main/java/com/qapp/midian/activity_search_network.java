package com.qapp.midian;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.location.AMapLocation;
import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Utils.FBMessage;
import Utils.LocationUtils;
import Utils.SPUtils;
import Utils.StatusBarUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class activity_search_network extends AppCompatActivity {

    private static final String TAG = "SearchNetwork";
    public static activity_search_network that = null;

    private ImageView btnBack;
    private EditText etSearchInput;
    private TextView btnSearch;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TextView tvEmptyText;

    private TextView tabNearby, tabSearch;

    private SearchAdapter adapter;
    private List<SearchModel> dataList = new ArrayList<>();
    private final OkHttpClient httpClient = new OkHttpClient();

    private int currentMode = 0; // 0: 附近的人, 1: 全网搜索

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_network);
        StatusBarUtil.setStatusBarMode(this, true, R.color.white);
        that = this;

        initViews();
        setupListeners();

        // 默认进入界面加载“附近的人”
        loadNearbyUsers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        that = null;
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etSearchInput = findViewById(R.id.etSearchInput);
        btnSearch = findViewById(R.id.btnSearch);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        tvEmptyText = findViewById(R.id.tvEmptyText);

        tabNearby = findViewById(R.id.tabNearby);
        tabSearch = findViewById(R.id.tabSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // 切换为“附近的人”
        tabNearby.setOnClickListener(v -> {
            if (currentMode != 0) {
                currentMode = 0;
                updateTabStyle();
                loadNearbyUsers();
            }
        });

        // 切换为“全网搜索”
        tabSearch.setOnClickListener(v -> {
            if (currentMode != 1) {
                currentMode = 1;
                updateTabStyle();
                dataList.clear();
                adapter.notifyDataSetChanged();
                tvEmptyText.setText("请输入关键字搜索用户或群聊");
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);

                etSearchInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        btnSearch.setOnClickListener(v -> {
            currentMode = 1;
            updateTabStyle();
            executeSearch();
        });

        etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                currentMode = 1;
                updateTabStyle();
                executeSearch();
                return true;
            }
            return false;
        });
    }

    private void updateTabStyle() {
        if (currentMode == 0) {
            tabNearby.setTextColor(Color.parseColor("#1A65FF"));
            tabNearby.setTypeface(null, Typeface.BOLD);
            tabSearch.setTextColor(Color.parseColor("#888888"));
            tabSearch.setTypeface(null, Typeface.NORMAL);
        } else {
            tabSearch.setTextColor(Color.parseColor("#1A65FF"));
            tabSearch.setTypeface(null, Typeface.BOLD);
            tabNearby.setTextColor(Color.parseColor("#888888"));
            tabNearby.setTypeface(null, Typeface.NORMAL);
        }
    }

    /**
     * 获取当前经纬度并执行回调（优先高德实时定位，失败时读取 SP 缓存兜底）
     */
    private interface LocationCallback {
        void onLocationReady(double lat, double lng);
    }

    private void fetchCurrentLocation(LocationCallback callback) {
        LocationUtils.getInstance().startSingleLocation(this, new LocationUtils.OnLocationResultListener() {
            @Override
            public void onSuccess(AMapLocation location) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                if (lat > 0 && lng > 0) {
                    SPUtils.getInstance().put("lat", String.valueOf(lat));
                    SPUtils.getInstance().put("lng", String.valueOf(lng));
                    if (!TextUtils.isEmpty(location.getAddress())) {
                        SPUtils.getInstance().put("address", location.getAddress());
                    }
                }
                callback.onLocationReady(lat, lng);
            }

            @Override
            public void onFailure(int errorCode, String errorInfo) {
                Log.w(TAG, "高德定位失败 (" + errorCode + "): " + errorInfo + "，使用本地缓存坐标");
                double lat = 0.0;
                double lng = 0.0;
                try {
                    lat = Double.parseDouble(SPUtils.getInstance().get("lat", "0"));
                    lng = Double.parseDouble(SPUtils.getInstance().get("lng", "0"));
                } catch (Exception ignored) {}
                callback.onLocationReady(lat, lng);
            }
        });
    }

    /**
     * 获取附近的人
     */
    /**
     * 获取附近的人（委托给 AAR）
     */
    private void loadNearbyUsers() {
        hideKeyboard();
        tvEmptyText.setText("正在搜寻附近的人...");
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        if (Data.MemberBillingBridge.get() == null) {
            tvEmptyText.setText("服务未就绪");
            return;
        }

        fetchCurrentLocation((lat, lng) -> {
            Data.MemberBillingBridge.get().getNearbyUsers(this, App.UID, lat, lng, 50, 1, 30, new Data.IMemberBilling.NetworkSearchCallback() {
                @Override
                public void onSuccess(JSONArray data) {
                    bindSearchData(data, "附近暂无其他用户");
                }

                @Override
                public void onFailure(String errorMessage) {
                    tvEmptyText.setText(errorMessage);
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * 全网关键字搜索（委托给 AAR）
     */
    private void executeSearch() {
        String keyword = etSearchInput.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            FBMessage.Show(this, "请输入搜索关键字");
            return;
        }

        hideKeyboard();

        tvEmptyText.setText("正在搜索中...");
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        if (Data.MemberBillingBridge.get() == null) {
            tvEmptyText.setText("服务未就绪");
            return;
        }

        fetchCurrentLocation((lat, lng) -> {
            Data.MemberBillingBridge.get().searchNetwork(this, App.UID, keyword, lat, lng, new Data.IMemberBilling.NetworkSearchCallback() {
                @Override
                public void onSuccess(JSONArray data) {
                    bindSearchData(data, "没有找到相关的用户或群聊");
                }

                @Override
                public void onFailure(String errorMessage) {
                    tvEmptyText.setText(errorMessage);
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * 统一绑定并解析 JSONArray 数据
     */
    private void bindSearchData(JSONArray array, String emptyMsg) {
        dataList.clear();
        if (array != null && array.length() > 0) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                SearchModel model = new SearchModel();
                model.type = obj.optInt("type", 1);
                model.id = obj.optString("id", "");

                String name = obj.optString("name", "").trim();
                model.name = TextUtils.isEmpty(name) ? "喵友" : name;

                model.avatar = obj.optString("avatar", "");
                model.subtitle = obj.optString("subtitle", "");
                model.sex = obj.optInt("sex", 0);
                model.city = obj.optString("city", "").trim();
                dataList.add(model);
            }
        }

        if (dataList.isEmpty()) {
            tvEmptyText.setText(emptyMsg);
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 统一处理服务器返回的数据结构并绑定到列表
     */
    private void handleResponse(Response response, String emptyMsg) throws IOException {
        if (response.isSuccessful() && response.body() != null) {
            try {
                String jsonStr = response.body().string();
                JSONObject jsonObject = new JSONObject(jsonStr);

                if (jsonObject.optInt("code") == 1) {
                    JSONArray array = jsonObject.optJSONArray("data");
                    dataList.clear();

                    if (array != null && array.length() > 0) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            SearchModel model = new SearchModel();
                            model.type = obj.optInt("type", 1);
                            model.id = obj.optString("id", "");

                            // 昵称为空时兜底显示“喵友”
                            String name = obj.optString("name", "").trim();
                            model.name = TextUtils.isEmpty(name) ? "喵友" : name;

                            model.avatar = obj.optString("avatar", "");
                            model.subtitle = obj.optString("subtitle", "");
                            model.sex = obj.optInt("sex", 0);
                            model.city = obj.optString("city", "").trim(); // 距离（例如 "1.5Km" 或 "未知"）
                            dataList.add(model);
                        }
                    }

                    runOnUiThread(() -> {
                        if (dataList.isEmpty()) {
                            tvEmptyText.setText(emptyMsg);
                            emptyView.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvEmptyText.setText("数据解析异常"));
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearchInput.getWindowToken(), 0);
        }
    }

    class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.module_search_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SearchModel model = dataList.get(position);

            holder.tvName.setText(model.name);

            // 整行条目不响应点击
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setFocusable(false);

            if (model.type == 2) {
                // 群聊：显示公告，隐藏性别，展示群标识
                holder.tvSubtitle.setText(model.subtitle);
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText("群");
                if (holder.ivSex != null) {
                    holder.ivSex.setVisibility(View.GONE);
                }
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("加入");
                holder.btnAction.setOnClickListener(v -> {
                    Intent intent = new Intent(activity_search_network.this, activity_group_join.class);
                    intent.putExtra("group_id", model.id);
                    startActivity(intent);
                });
            } else {
                // 用户：脱敏账号 + 距离（Km）
                String subText = model.subtitle;
                if (TextUtils.isEmpty(subText)) {
                    String displayId = model.id;
                    if (displayId != null && displayId.matches("^1\\d{10}$")) {
                        displayId = displayId.substring(0, 7) + "****";
                    }
                    subText = "账号: " + displayId;
                }

                if (!TextUtils.isEmpty(model.city)) {
                    subText += " · " + model.city;
                }
                holder.tvSubtitle.setText(subText);

                // 隐藏群标识
                holder.tvBadge.setVisibility(View.GONE);

                // 性别展示（0为男，1为女）
                if (holder.ivSex != null) {
                    if (model.sex == 0) {
                        holder.ivSex.setVisibility(View.VISIBLE);
                        holder.ivSex.setImageResource(R.drawable.man);
                    } else if (model.sex == 1) {
                        holder.ivSex.setVisibility(View.VISIBLE);
                        holder.ivSex.setImageResource(R.drawable.women);
                    } else {
                        holder.ivSex.setVisibility(View.GONE);
                    }
                }

                // 保持添加按钮常显，点击直接跳转用户资料详情页
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("添加");
                holder.btnAction.setOnClickListener(v -> {
                    Intent intent = new Intent(that, activity_user_profile.class);
                    intent.putExtra("target_userid", model.id);
                    startActivity(intent);
                });
            }

            // 头像加载
            if (model.avatar != null && !model.avatar.isEmpty()) {
                String fullUrl = model.avatar.startsWith("http") ? model.avatar : App.DataServiceUrl + "/" + model.avatar;
                Glide.with(activity_search_network.this)
                        .load(fullUrl)
                        .placeholder(R.drawable.user_default)
                        .into(holder.ivAvatar);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.user_default);
            }
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            RoundedImageView ivAvatar;
            ImageView ivSex;
            TextView tvName, tvSubtitle, tvBadge, btnAction;

            public VH(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                ivSex = itemView.findViewById(R.id.ivSex);
                tvName = itemView.findViewById(R.id.tvName);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
                tvBadge = itemView.findViewById(R.id.tvBadge);
                btnAction = itemView.findViewById(R.id.btnAction);
            }
        }
    }

    class SearchModel {
        int type;
        String id;
        String name;
        String avatar;
        String subtitle;
        String city;
        int sex;
    }
}