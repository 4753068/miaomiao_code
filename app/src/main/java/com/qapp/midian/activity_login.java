package com.qapp.midian;

import static Utils.WindowUtils.setupTransparentStatusBarWithBlackText;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import Data.IMemberBilling;
import Data.MemberBillingBridge;
import Utils.FBMessage;
import Utils.SPUtils;
import socket.AutoReconnectWebSocket;

public class activity_login extends AppCompatActivity {

    private EditText mobileInput;
    private EditText passwordInput;
    private ImageView togglePassword;
    private CheckBox agreementInput;
    private Button loginBtn;
    private LinearLayout btnzhuce;
    private boolean isPasswordVisible = false;
    private RelativeLayout btn_find_pwd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setupTransparentStatusBarWithBlackText(this);
        initViews();
        bindEvents();
    }

    private void initViews() {
        mobileInput = findViewById(R.id.mobileInput);
        passwordInput = findViewById(R.id.passwordInput);
        togglePassword = findViewById(R.id.togglePassword);
        agreementInput = findViewById(R.id.agreementInput);
        loginBtn = findViewById(R.id.loginBtn);
        btnzhuce = findViewById(R.id.btnzhuce);
        btn_find_pwd = findViewById(R.id.btn_find_pwd);
    }

    private void bindEvents() {
        togglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setAlpha(0.5f);
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setAlpha(1.0f);
            }
            isPasswordVisible = !isPasswordVisible;
            passwordInput.setSelection(passwordInput.getText().length());
        });

        loginBtn.setOnClickListener(v -> {
            String mobile = mobileInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (TextUtils.isEmpty(mobile) || TextUtils.isEmpty(password)) {
                FBMessage.Show(activity_login.this, getString(R.string.login_tip_fill_complete));
                return;
            }

            if (!agreementInput.isChecked()) {
                agreementInput.setChecked(true);
                FBMessage.Show(activity_login.this, getString(R.string.login_tip_auto_agree_agreement));
            }

            executeLogin(mobile, password);
        });

        btnzhuce.setOnClickListener(v -> {
            Intent it_setting = new Intent(this, activity_web.class);
            it_setting.putExtra("url", App.DataServiceUrl + "/app/midian/apphtml/zhuce.html");
            startActivity(it_setting);
        });

        btn_find_pwd.setOnClickListener(v -> {
            Intent it_setting = new Intent(this, activity_web.class);
            it_setting.putExtra("url", App.DataServiceUrl + "/app/midian/apphtml/find_pwd.html");
            startActivity(it_setting);
        });
    }

    private void executeLogin(String mobile, String password) {
        if (MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "服务未初始化");
            return;
        }

        ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.login_loading_message));
        loadingDialog.setCancelable(false);
        loadingDialog.show();
        loginBtn.setEnabled(false);

        // 委托给闭源桥接模块执行核心鉴权
        MemberBillingBridge.get().executeLogin(this, mobile, password, new IMemberBilling.LoginCallback() {
            @Override
            public void onSuccess() {
                loadingDialog.dismiss();
                loginBtn.setEnabled(true);
                FBMessage.Show(activity_login.this, getString(R.string.login_success_entering));

                // 1. 从本地获取 AAR 已写入的 UID，并同步给全局内存变量
                App.UID = SPUtils.getInstance().get("uid", 0);

                // 2. 启动长连接
                if (App.UID > 0) {
                    AutoReconnectWebSocket.startSocket();
                }

                // 3. 登录成功跳转主页并销毁当前登录页
                Intent intent = new Intent(activity_login.this, activity_main.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(String errorMessage) {
                loadingDialog.dismiss();
                loginBtn.setEnabled(true);
                FBMessage.Show(activity_login.this, errorMessage);
            }
        });
    }
}