package com.qapp.midian;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import Data.MemberUtils;
import Utils.FBMessage;
import Utils.SPUtils;
import Utils.StatusBarUtil;

public class activity_pin extends AppCompatActivity {
    private EditText[] editTexts;
    private Button btnContinue;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        StatusBarUtil.setStatusBarMode(this, true, R.color.background);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 当用户按下返回键或滑动返回时，会执行这里的代码

                Intent intent = new Intent(activity_pin.this, activity_main.class);
                // 清除 activity_main 顶部的所有 Activity（包括 activity_friend）
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);

                // 关闭当前的 activity_pin
                finish();
            }
        });

        btnContinue = findViewById(R.id.btnContinue);

        // 将 6 个输入框放入数组，方便循环遍历处理
        editTexts = new EditText[]{
                findViewById(R.id.etCode1),
                findViewById(R.id.etCode2),
                findViewById(R.id.etCode3),
                findViewById(R.id.etCode4),
                findViewById(R.id.etCode5),
                findViewById(R.id.etCode6)
        };

        setupOtpInputs();

        // 点击按钮进行验证
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateCode();
            }
        });
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        // 1. 创建指向 activity_main 的 Intent

        Intent intent = new Intent(this, activity_main.class);

        // 2. 核心魔法：设置 Flags
        // FLAG_ACTIVITY_CLEAR_TOP 会将任务栈中在 activity_main 上面的所有页面全部销毁（包括 activity_friend）
        // FLAG_ACTIVITY_SINGLE_TOP 确保如果 activity_main 已经在栈里，就不会重新创建一个新的实例，而是复用现有的
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 3. 启动 activity_main
        startActivity(intent);

        // 4. 关闭当前的 activity_pin
        finish();
    }


    @Override
    protected void onResume() {

        super.onResume();
    }

    /**
     * 设置验证码输入框的联动逻辑（自动前进与后退）
     */
    private void setupOtpInputs() {
        for (int i = 0; i < editTexts.length; i++) {
            final int currentIndex = i;

            // 1. 监听文本变化，输满 1 位自动跳到下一个框
            editTexts[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null && s.length() == 1) {
                        // 如果不是最后一个框，输满 1 位自动跳到下一个框
                        if (currentIndex < editTexts.length - 1) {
                            editTexts[currentIndex + 1].requestFocus();
                        } else {
                            // 【新增功能 2】：如果是最后一个输入框且输满了，直接调用验证方法，无需点击按钮
                            validateCode();
                        }
                    }
                }
            });

            // 2. 监听键盘删除键（Backspace），实现空白时回退焦点
            editTexts[i].setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                        // 如果当前框已经是空的，且不是第一个框，按删除键就回退到上一个框并清空内容
                        if (editTexts[currentIndex].getText().toString().isEmpty() && currentIndex > 0) {
                            editTexts[currentIndex - 1].requestFocus();
                            editTexts[currentIndex - 1].setText("");
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }

    /**
     * 获取 6 位输入并委托 AAR 执行闭源密码验证
     */
    private void validateCode() {
        // 1. 拼接 6 个输入框的数字
        StringBuilder sb = new StringBuilder();
        for (EditText editText : editTexts) {
            sb.append(editText.getText().toString().trim());
        }
        String inputCode = sb.toString();

        // 2. 校验长度是否满 6 位
        if (inputCode.length() < 6) {
            FBMessage.Show(this, "请输入六位数安全密码");
            return;
        }

        if (Data.MemberBillingBridge.get() == null) {
            FBMessage.Show(this, "鉴权模块未就绪");
            return;
        }

        // 3. 委托闭源 AAR 进行真实密码/VIP虚拟防查岗密码的集中校验
        int verifyResult = Data.MemberBillingBridge.get().verifyPinCode(inputCode);

        if (verifyResult == Data.IMemberBilling.PIN_RESULT_REAL) {
            // 真实密码验证通过
            App.FriendPass = true;
            App.isErrorPassword = false;
            finish();
        } else if (verifyResult == Data.IMemberBilling.PIN_RESULT_VIRTUAL) {
            // VIP 虚拟密码验证通过（进入防查岗模式，标记假密码状态）
            App.FriendPass = true;
            App.isErrorPassword = true;
            finish();
        } else {
            // 验证失败，清空输入并提示
            clearAllInputs();
            FBMessage.Show(this, "密码错误，请重新输入");
        }
    }

    /**
     * MD5 标准加密算法 (Java 实现)
     */
    private String convertToMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());

            // 将字节数组转换为 16 进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 清空所有输入框并重置焦点
     */
    private void clearAllInputs() {
        for (EditText editText : editTexts) {
            editText.setText("");
        }
        editTexts[0].requestFocus();
    }


}
