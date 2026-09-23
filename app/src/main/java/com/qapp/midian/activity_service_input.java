package com.qapp.midian;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import Data.IMemberBilling;
import Data.MemberBillingBridge;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class activity_service_input extends AppCompatActivity {
    public static activity_service_input that;
    private EditText etServerName, etServerContent, etSocketUrl, etServerPort, etLine, etPassword;
    private Switch switchType;
    private Button btnSubmit, btnTestConnection;

    private int currentServerId = -1;

    // 记录连接是否测试成功
    private boolean isConnectionSuccessful = false;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_input);
        that=this;
        initViews();
        checkEditMode();
        setupListeners();
    }

    private void initViews() {
        etServerName = findViewById(R.id.etServerName);
        etServerContent = findViewById(R.id.etServerContent);
        etSocketUrl = findViewById(R.id.etSocketUrl);
        etServerPort = findViewById(R.id.etServerPort);
        etPassword = findViewById(R.id.etPassword);
        switchType = findViewById(R.id.switchType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnTestConnection = findViewById(R.id.btnTestConnection);
    }

    private void checkEditMode() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("id")) {
            currentServerId = intent.getIntExtra("id", -1);
            if (currentServerId > 0) {
                etServerName.setText(intent.getStringExtra("server_name"));
                etServerContent.setText(intent.getStringExtra("server_content"));
                etSocketUrl.setText(intent.getStringExtra("socket_url"));
                etServerPort.setText(String.valueOf(intent.getIntExtra("server_port", 0)));

                btnSubmit.setText("保存修改");

                // 如果是编辑现有的节点，默认原本是连通的，允许直接保存
                isConnectionSuccessful = true;

                // 【新增】从本地数据库读取真实密码
                try {
                    Cursor cursor = App.db.rawQuery("SELECT password FROM server WHERE id=" + currentServerId, null);
                    if (cursor.moveToFirst()) {
                        int pwdIndex = cursor.getColumnIndex("password");
                        if (pwdIndex != -1) {
                            String savedPassword = cursor.getString(pwdIndex);
                            if (savedPassword != null && !savedPassword.isEmpty()) {
                                etPassword.setText(savedPassword);
                            }
                        }
                    }
                    cursor.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 【新增】禁止密码框编辑，并改变文字颜色给用户视觉提示
                etPassword.setEnabled(false);
                etPassword.setFocusable(false);
                etPassword.setFocusableInTouchMode(false);
                etPassword.setTextColor(Color.parseColor("#999999"));
            }
        }

        // 如果密码框仍然为空（新增节点的情况），则自动生成默认密码
        if (etPassword.getText().toString().isEmpty()) {
            setupDefaultValues();
        }
    }

    private void setupDefaultValues() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        etPassword.setText(md5(timestamp));
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> submitData());
        if(btnTestConnection != null){
            btnTestConnection.setOnClickListener(v -> testSocketConnection());
        }

        // 监听地址和端口的输入变化。一旦修改了连接信息，需重新测试
        TextWatcher connectionWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isConnectionSuccessful = false;
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        etSocketUrl.addTextChangedListener(connectionWatcher);
        etServerPort.addTextChangedListener(connectionWatcher);
    }

    private void testSocketConnection() {
        String host = etSocketUrl.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "请先填写 Socket 链接和端口号", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口号格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTestConnection.setText("测试中...");
        btnTestConnection.setEnabled(false);
        // 开始测试前，将状态置为 false
        isConnectionSuccessful = false;

        OkHttpClient pingClient = new OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String wssUrl = "wss://" + host + ":" + port;
        wssUrl = wssUrl.replace("wss://wss://", "wss://").replace("wss://ws://", "wss://");

        Request request = new Request.Builder().url(wssUrl).build();

        pingClient.newWebSocket(request, new okhttp3.WebSocketListener() {
            private boolean hasDetermined = false;

            @Override
            public void onOpen(okhttp3.WebSocket webSocket, Response response) { }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, String text) {
                if (!hasDetermined && text.contains("\"welcome\"")) {
                    hasDetermined = true;
                    // 测试成功，允许提交
                    isConnectionSuccessful = true;

                    runOnUiThread(() -> {
                        btnTestConnection.setEnabled(true);
                        btnTestConnection.setText("测试 Socket 连接");
                        Toast.makeText(activity_service_input.this, "连接成功！节点可用", Toast.LENGTH_SHORT).show();
                    });
                    webSocket.close(1000, "Ping Success");
                }
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                if (!hasDetermined) {
                    hasDetermined = true;
                    isConnectionSuccessful = false;
                    runOnUiThread(() -> {
                        btnTestConnection.setEnabled(true);
                        btnTestConnection.setText("测试 Socket 连接");
                        Toast.makeText(activity_service_input.this, "连接失败，请检查地址、端口或网络状态", Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                if (!hasDetermined) {
                    hasDetermined = true;
                    isConnectionSuccessful = false;
                    runOnUiThread(() -> {
                        btnTestConnection.setEnabled(true);
                        btnTestConnection.setText("测试 Socket 连接");
                        Toast.makeText(activity_service_input.this, "连接被过早关闭，节点不可用", Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void submitData() {
        if (!isConnectionSuccessful) {
            Toast.makeText(this, "节点还未连接成功，无法提交，请成功连接后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etServerName.getText().toString().trim();
        String content = etServerContent.getText().toString().trim();
        String url = etSocketUrl.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty() || url.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "请填写所有星号标注的必填项", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口号格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        if (MemberBillingBridge.get() == null) {
            Toast.makeText(this, "核心服务未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("保存中...");

        // 委托给闭源 AAR 发起带签名的安全提交
        MemberBillingBridge.get().submitServer(this, currentServerId, name, content, url, port, password, new IMemberBilling.ServerSubmitCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(activity_service_input.this, message, Toast.LENGTH_SHORT).show();
                resetSubmitButton();
                finish();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(activity_service_input.this, errorMessage, Toast.LENGTH_SHORT).show();
                resetSubmitButton();
            }
        });
    }


    private void resetSubmitButton() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText(currentServerId > 0 ? "保存修改" : "保存节点");
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes(StandardCharsets.UTF_8));
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}