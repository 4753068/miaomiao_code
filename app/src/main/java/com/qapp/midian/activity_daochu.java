package com.qapp.midian;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.blankj.utilcode.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import Data.ChatUtils;
import Utils.RecycleViewDivider;
import Utils.StatusBarUtil;
import adapter.Adapter_daochu;
import adapter.Module_Daochu;

public class activity_daochu extends AppCompatActivity {
    public static activity_daochu that;

    public static TextView btnUpdate = null, beifen, daoru;
    private static String DownloadFile = null;
    private static String friend_nickname;
    private static int friend_uid;

    public static androidx.recyclerview.widget.RecyclerView RecyclerView = null;
    public static List<Module_Daochu> Datas;
    public static Adapter_daochu mAdapter;

    public static Handler downloadHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (that == null || that.isFinishing() || that.isDestroyed()) {
                return false;
            }

            switch (msg.what) {
                case 1:
                    daoru.setText(that.getString(R.string.daochu_progress_format, String.valueOf(msg.obj), ChatUtils.DaoruDaochuCount));
                    try {
                        if (Integer.parseInt(String.valueOf(msg.obj)) == ChatUtils.DaoruDaochuCount - 1) {
                            daoru.setText(that.getString(R.string.daochu_import_complete));
                        }
                    } catch (Exception ignored) {}
                    break;
                case 2:
                    beifen.setText(that.getString(R.string.daochu_progress_format, String.valueOf(msg.obj), ChatUtils.DaoruDaochuCount));
                    try {
                        if (Integer.parseInt(String.valueOf(msg.obj)) == ChatUtils.DaoruDaochuCount) {
                            beifen.setText(that.getString(R.string.daochu_backup_complete));
                        }
                    } catch (Exception ignored) {}
                    break;
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_daochu);
        StatusBarUtil.setStatusBarMode(this, true, R.color.background);
        that = this;

        daoru = findViewById(R.id.daoru);
        beifen = findViewById(R.id.beifen);

        Datas = new ArrayList<Module_Daochu>();

        btnUpdate = findViewById(R.id.btnUpdate);

        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Data.MemberBillingBridge.get() == null) return;

                btnUpdate.setEnabled(false);
                beifen.setText("正在准备备份...");

                Data.MemberBillingBridge.get().exportChatHistory(
                        activity_daochu.this,
                        App.UID,
                        friend_uid,
                        friend_nickname,
                        App.db,
                        new Data.IMemberBilling.ExportChatCallback() {

                            public void onProgress(int current, int total) {
                                beifen.setText(getString(R.string.daochu_progress_format, String.valueOf(current), total));
                            }

                            @Override
                            public void onProgress(int progress) {

                            }

                            @Override
                            public void onSuccess(String exportFilePath) {
                                btnUpdate.setEnabled(true);
                                beifen.setText(getString(R.string.daochu_backup_complete));
                                // 刷新本地列表
                                GetFile(friend_uid);
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                btnUpdate.setEnabled(true);
                                if ("VIP_REQUIRED".equals(errorMessage)) {
                                    beifen.setText("仅限VIP会员使用");
                                    Data.MemberBillingBridge.get().showSubscriptionDialog(activity_daochu.this);
                                } else {
                                    beifen.setText(errorMessage);
                                }
                            }
                        }
                );
            }
        });

        /******************* 列表 **************************/
        RecyclerView = findViewById(R.id.recycler);
        RecyclerView.setLayoutManager(new LinearLayoutManager(that));
        RecyclerView.addItemDecoration(new RecycleViewDivider(LinearLayoutManager.VERTICAL, 0, getResources().getColor(R.color.white)));

        mAdapter = new Adapter_daochu(Datas);
        RecyclerView.setAdapter(mAdapter);
        ((SimpleItemAnimator) RecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            friend_nickname = bundle.getString("friend_nickname");
            friend_uid = bundle.getInt("friend_uid");

            GetFile(friend_uid);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        that = null;
    }

    public static void GetFile(int friend_uid) {
        String savePath = Environment.getExternalStorageDirectory().getPath() + "/Download/midian/" + friend_uid;
        List<File> fileList = FileUtils.listFilesInDir(savePath);
        if (fileList == null) {
            fileList = new ArrayList<>();
        }
        Object[] array = fileList.toArray();

        if (Datas.size() > 0) Datas.clear();
        Arrays.sort(array, Collections.reverseOrder());
        for (int i = 0; i < array.length; i++) {
            String t = array[i].toString();
            String[] parts = t.split("/");
            Datas.add(new Module_Daochu(parts[parts.length - 1], t, friend_uid));
        }
        if (that != null && !that.isFinishing() && !that.isDestroyed()) {
            that.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity_daochu.mAdapter != null) {
                        activity_daochu.mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }
}