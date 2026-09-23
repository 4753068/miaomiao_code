package com.qapp.midian;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import Data.MemberBillingBridge;
import Dialog.SettingDialog;
import Utils.RecycleViewDivider;
import Utils.StatusBarUtil;
import adapter.Adapter_server;
import adapter.Module_server;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class activity_service extends AppCompatActivity {
    public static activity_service that = null;
    public static androidx.recyclerview.widget.RecyclerView RecyclerView = null;
    public static List<Module_server> mDatas;
    public static Adapter_server mAdapter;
    private static LinearLayoutManager layoutManager;

    private ImageView btnMenu;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result == null || result.getContents() == null) {
                    Toast.makeText(activity_service.this, "已取消扫码", Toast.LENGTH_SHORT).show();
                } else {
                    String scanResult = result.getContents().trim();
                    if (!scanResult.isEmpty()) {
                        handleServerIdAdded(scanResult);
                    } else {
                        Toast.makeText(activity_service.this, "扫码内容为空", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        that = this;
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPopupMenu(v);
                }
            });
        }

        RecyclerView = findViewById(R.id.recycler_chat_list);
        if (RecyclerView != null) {
            RecyclerView.setLayoutManager(new LinearLayoutManager(that));
            RecyclerView.addItemDecoration(new RecycleViewDivider(LinearLayoutManager.VERTICAL, 0, getResources().getColor(R.color.white)));            mDatas = new ArrayList<>();
            mAdapter = new Adapter_server(that, mDatas);
            RecyclerView.setAdapter(mAdapter);

            if (RecyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) RecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            }
            layoutManager = (LinearLayoutManager) RecyclerView.getLayoutManager();
            RecyclerView.setItemAnimator(null);
        }

        // 一进页面，优先加载本地数据库的数据，避免白屏
        Get_Server();
        // 然后再异步去云端同步最新节点数据
        SaveServer();
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenu().add(0, 1, 0, "添加共享节点");
        popupMenu.getMenu().add(0, 2, 0, "建立我的节点");
        popupMenu.getMenu().add(0, 3, 0, "刷新所有节点");

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == 1) {
                    SettingDialog.showAddServiceDialog(that);
                    return true;
                }
                else if (id == 2) {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_service_input.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                    return true;
                }
                else if (id == 3) {
                    App.db.execSQL("DELETE FROM server");
                    SaveServer();
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    // 公开扫码方法，允许 SettingDialog 调用
    public void startQrCodeScan() {
        try {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("请将二维码对准识别框");
            options.setCameraId(0);
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(false);
            options.setOrientationLocked(true);
            barcodeLauncher.launch(options);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "无法启动相机，请检查相机权限", Toast.LENGTH_SHORT).show();
        }
    }

    // 扫码成功后的处理：自动填充到输入框或自动弹窗
    private void handleServerIdAdded(String id) {
        if (SettingDialog.currentDialogEditText != null) {
            // 情景 A：弹窗当前是打开的（用户是在弹窗里点的扫码），直接把结果填入输入框
            SettingDialog.currentDialogEditText.setText(id);
            Toast.makeText(this, "密钥已识别并填入", Toast.LENGTH_SHORT).show();
        } else {
            // 情景 B：弹窗没打开，自动为他打开弹窗并将扫到的密钥填进去
            SettingDialog.showAddServiceDialog(this);
            if (SettingDialog.currentDialogEditText != null) {
                SettingDialog.currentDialogEditText.setText(id);
                Toast.makeText(this, "密钥已识别并填入", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void SaveServer() {
        if (MemberBillingBridge.get() == null || App.db == null || that == null) {
            return;
        }
        // 委托给闭源 AAR 安全拉取并写入 server 表
        MemberBillingBridge.get().syncServers(that, App.UID, App.db, new Runnable() {
            @Override
            public void run() {
                // 同步落盘完毕后，直接调用本类原有的本地渲染和测速
                Get_Server();
            }
        });
    }

    public static void Get_Server() {
        String sql = "select * from server order by id asc";
        Cursor cursor = App.db.rawQuery(sql, null);

        // 使用临时列表在子线程中装载数据，解决线程安全问题
        List<Module_server> tempList = new ArrayList<>();

        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String server_name = cursor.getString(cursor.getColumnIndexOrThrow("server_name"));
                String socket_url = cursor.getString(cursor.getColumnIndexOrThrow("socket_url"));
                String server_content = cursor.getString(cursor.getColumnIndexOrThrow("server_content"));
                int server_port = cursor.getInt(cursor.getColumnIndexOrThrow("server_port"));
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));

                int uid = 0;
                int type = 0;
                int uidIndex = cursor.getColumnIndex("uid");
                int typeIndex = cursor.getColumnIndex("type");

                if(uidIndex != -1) uid = cursor.getInt(uidIndex);
                if(typeIndex != -1) type = cursor.getInt(typeIndex);

                tempList.add(new Module_server(id, server_name, server_content, socket_url, server_port, uid, type));
            }
        }
        cursor.close();

        // 切换回主线程（UI线程）更新真正的 mDatas 和刷新适配器
        if (that != null) {
            that.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mDatas != null) {
                        mDatas.clear();
                        mDatas.addAll(tempList); // 在主线程安全地赋值
                    }
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();

                    // 渲染完毕后，启动异步探测任务
                    checkServerConnections();
                }
            });
        }
    }

    // ✨ 异步检测所有服务器的连通性
    private static void checkServerConnections() {
        OkHttpClient pingClient = new OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        if (mDatas == null || mDatas.isEmpty()) return;

        // 使用 AtomicInteger 记录还需要检测的节点数量
        final AtomicInteger pendingChecks = new AtomicInteger(mDatas.size());

        for (int i = 0; i < mDatas.size(); i++) {
            // 按对象引用传递，而不是按 index，防范排序导致 index 错乱
            final Module_server targetServer = mDatas.get(i);
            String socket_url = targetServer.getSocket_url();
            int port = targetServer.getServer_port();

            if (socket_url != null && !socket_url.isEmpty()) {
                String wssUrl = "wss://" + socket_url + ":" + port;
                wssUrl = wssUrl.replace("wss://wss://", "wss://").replace("wss://ws://", "wss://");

                Request request = new Request.Builder().url(wssUrl).build();
                pingClient.newWebSocket(request, new okhttp3.WebSocketListener() {
                    private boolean hasDetermined = false;

                    // 统一处理检测结果
                    private void handleResult(boolean isOnline) {
                        if (!hasDetermined) {
                            hasDetermined = true;
                            targetServer.setOnline(isOnline);

                            // 每次检测完一个，计数器减 1
                            int remaining = pendingChecks.decrementAndGet();

                            if (remaining == 0) {
                                // 所有节点都检测完毕，执行排序并全局刷新
                                sortAndRefreshUI();
                            } else {
                                // 还没全部检测完，先局部刷新当前节点状态 (给用户实时反馈)
                                updateSingleServerUI(targetServer);
                            }
                        }
                    }

                    @Override
                    public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                    }

                    @Override
                    public void onMessage(okhttp3.WebSocket webSocket, String text) {
                        if (!hasDetermined && text.contains("\"welcome\"")) {
                            handleResult(true);
                            webSocket.close(1000, "Ping Success");
                        }
                    }

                    @Override
                    public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                        handleResult(false);
                    }

                    @Override
                    public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                        handleResult(false);
                    }
                });
            } else {
                // 如果 URL 为空，直接按失败处理，并扣减计数
                pendingChecks.decrementAndGet();
            }
        }
    }

    // ✨ 局部刷新单个节点（在全部检测完之前给用户的过渡状态）
    private static void updateSingleServerUI(Module_server server) {
        if (that != null && mDatas != null) {
            that.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int index = mDatas.indexOf(server);
                    if (index != -1 && mAdapter != null) {
                        mAdapter.notifyItemChanged(index);
                    }
                }
            });
        }
    }

    // ✨ 所有节点检测完毕后，重新排序（在线的在前）并全局重绘
    private static void sortAndRefreshUI() {
        if (that != null && mDatas != null) {
            that.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 按照 isOnline 排序：true(在线)排前面，false(离线)排后面
                    Collections.sort(mDatas, new Comparator<Module_server>() {
                        @Override
                        public int compare(Module_server s1, Module_server s2) {
                            int v1 = s1.isOnline() ? 1 : 0;
                            int v2 = s2.isOnline() ? 1 : 0;
                            // 降序排列
                            return Integer.compare(v2, v1);
                        }
                    });

                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                        Toast.makeText(that, "节点测速完毕，已自动优选", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}