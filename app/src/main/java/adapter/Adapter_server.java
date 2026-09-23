package adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_service_input;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import Dialog.SettingDialog;
import Utils.SPUtils;
import socket.AutoReconnectWebSocket;

public class Adapter_server extends RecyclerView.Adapter<Adapter_server.ViewHolder> {
    private List<Module_server> mData;
    private Activity activity;
    private EditText mEditText;
    private static String sn;

    public Adapter_server(Activity activity, List<Module_server> mData) {
        this.activity = activity;
        this.mData = mData;
    }

    @NonNull
    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.module_server, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        holder.setData(
                this.mData.get(position).getId(),
                this.mData.get(position).getServer_name(),
                this.mData.get(position).getServer_content(),
                this.mData.get(position).getSocket_url(),
                this.mData.get(position).getServer_port(),
                this.mData.get(position).isOnline()
        );

        final int server_id = this.mData.get(position).getId();
        final int server_port = this.mData.get(position).getServer_port();
        final String server_name = this.mData.get(position).getServer_name();
        final String server_content = this.mData.get(position).getServer_content();
        final String socket_url = this.mData.get(position).getSocket_url();
        final boolean isOnline = this.mData.get(position).isOnline();

        // 直接从实体类获取 uid 和 type
        final int type = this.mData.get(position).getType();
        final int uid = this.mData.get(position).getUid();

        // 单击事件：连接 Socket
        // 单击事件：连接 Socket
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("Range")
            @Override
            public void onClick(View v) {
                // 1. 清洗 URL，防止自带 wss:// 或已经带了端口
                String pureUrl = socket_url.replace("wss://", "").replace("ws://", "");
                if (pureUrl.contains(":")) {
                    pureUrl = pureUrl.substring(0, pureUrl.indexOf(":"));
                }
                String finalPort = String.valueOf(server_port);

                // 2. 规范保存
                SPUtils.getInstance().put("SocketName", server_name);
                SPUtils.getInstance().put("SocketUrl", "wss://" + pureUrl);
                SPUtils.getInstance().put("SocketPort", finalPort);
                notifyDataSetChanged();

                // 3. 后台安全重连
                if (App.UID > 0) {
                    final String fullUrl = "wss://" + pureUrl + ":" + finalPort;
                    new Thread(() -> {
                        try {
                            AutoReconnectWebSocket.switchUrl(fullUrl);
                            if (AutoReconnectWebSocket.instance != null) {
                                AutoReconnectWebSocket.instance.connect();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        });

        // 长按事件：判断 type，弹出选项框
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (type == 1) {
                    String[] options = {
                            activity.getString(R.string.server_option_edit),
                            activity.getString(R.string.server_option_share),
                            activity.getString(R.string.server_option_delete)
                    };

                    AlertDialog optionDialog = new AlertDialog.Builder(activity)
                            .setItems(options, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == 0) {
                                        // 1. 编辑节点
                                        Intent intent = new Intent(activity, activity_service_input.class);
                                        intent.putExtra("id", server_id);
                                        intent.putExtra("server_name", server_name);
                                        intent.putExtra("server_content", server_content);
                                        intent.putExtra("socket_url", socket_url);
                                        intent.putExtra("server_port", server_port);
                                        activity.startActivity(intent);
                                    } else if (which == 1) {
                                        // 2. 分享节点
                                        String password = "";
                                        try {
                                            // 🌟 使用参数化查询避免 SQL 注入隐患
                                            Cursor cursor = App.db.rawQuery("SELECT password FROM server WHERE id=?", new String[]{String.valueOf(server_id)});
                                            if (cursor != null) {
                                                if (cursor.moveToFirst()) {
                                                    int pwdIndex = cursor.getColumnIndex("password");
                                                    if (pwdIndex != -1) {
                                                        password = cursor.getString(pwdIndex);
                                                    }
                                                }
                                                cursor.close();
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        if (password == null || password.isEmpty()) {
                                            Toast.makeText(activity, activity.getString(R.string.server_toast_get_pwd_failed), Toast.LENGTH_LONG).show();
                                        } else {
                                            SettingDialog.showShareDialog(activity, server_name, password);
                                        }
                                    } else if (which == 2) {
                                        // 3. 删除节点
                                        AlertDialog deleteDialog = new AlertDialog.Builder(activity)
                                                .setTitle(activity.getString(R.string.server_dialog_title))
                                                .setMessage(activity.getString(R.string.server_dialog_delete_confirm))
                                                .setPositiveButton(activity.getString(R.string.server_dialog_btn_confirm), new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialogConfirm, int whichConfirm) {
                                                        try {
                                                            // 🌟 使用参数化查询避免 SQL 注入隐患
                                                            App.db.execSQL("DELETE FROM server WHERE id=?", new Object[]{server_id});

                                                            int currentPosition = holder.getBindingAdapterPosition();
                                                            if (currentPosition != RecyclerView.NO_POSITION) {
                                                                mData.remove(currentPosition);
                                                                notifyItemRemoved(currentPosition);
                                                                notifyItemRangeChanged(currentPosition, mData.size());
                                                            }

                                                            Toast.makeText(activity, activity.getString(R.string.server_toast_delete_success), Toast.LENGTH_SHORT).show();
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                            Toast.makeText(activity, activity.getString(R.string.server_toast_delete_failed), Toast.LENGTH_SHORT).show();
                                                        }
                                                    }
                                                })
                                                .setNegativeButton(activity.getString(R.string.server_dialog_btn_cancel), null)
                                                .create();

                                        deleteDialog.show();
                                        setDialogRoundedCorners(deleteDialog);
                                    }
                                }
                            })
                            .create();

                    optionDialog.show();
                    setDialogRoundedCorners(optionDialog);

                    return true;
                }
                return false;
            }
        });
    }

    /**
     * 统一设置 Dialog 为圆角白色背景的辅助方法
     */
    private void setDialogRoundedCorners(AlertDialog dialog) {
        if (dialog != null && dialog.getWindow() != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.WHITE);
            drawable.setCornerRadius(40f);
            dialog.getWindow().setBackgroundDrawable(drawable);
        }
    }

    @Override
    public int getItemCount() {
        return this.mData != null ? this.mData.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView _server_name = null, _server_content = null;
        private ImageView _gou = null;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            _server_name = itemView.findViewById(R.id.server_name);
            _server_content = itemView.findViewById(R.id.server_content);
            _gou = itemView.findViewById(R.id.gou);
        }

        public void setData(int id, String server_name, String server_content, String socket_url, int server_port, boolean isOnline) {
            _server_name.setText(server_name);
            _server_content.setText(server_content);

            sn = "wss://" + socket_url;
            String SocketUrl = SPUtils.getInstance().get("SocketUrl", "wss://socket.am930.cn:8083");
            boolean isSelected = SocketUrl.equals(sn);

            if (!isOnline) {
                _server_name.setTextColor(Color.RED);
                _gou.setVisibility(View.VISIBLE);
                _gou.setImageResource(R.drawable.error);
            } else {
                _server_name.setTextColor(Color.parseColor("#333333"));
                if (isSelected) {
                    _gou.setVisibility(View.VISIBLE);
                    _gou.setImageResource(R.drawable.yidu);
                } else {
                    _gou.setVisibility(View.GONE);
                }
            }
        }
    }
}