package com.qapp.midian;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;

import Utils.SPUtils;

public class activity_password extends AppCompatActivity {

    // 传统方式：声明所有的 UI 控件变量
    private TextView tvPageTitle;
    private TextView tvPageSubtitle;
    private LinearLayout llFormGroup;
    private TextView tvHintText;
    private LinearLayout llActionArea;
    private Button btnPrimary;

    // 业务状态定义
    private int pageType = 1;              // 1: 安全密码, 2: 虚拟密码, 4: 登录密码
    private boolean isVerifyMode = false;  // 是否为纯验证模式

    private boolean hasExistingPassword = false;
    private String currentSavedMd5 = "";
    private String oppositeSavedMd5 = "";

    // 存储文件名统一改为与其他弹窗一致的 setting_deletetime，防止作用域隔离
    private static final String PREFS_NAME = "app_config";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_VIRTUAL_PASSWORD = "virtualpassword";

    // 假设 SP 中存储 UID 和 sign 的键名，请根据实际情况调整
    private static final String KEY_UID = "uid";
    private static final String KEY_SIGN = "sign";

    // 动态存储各个输入组中 6 个 EditText 框的集合
    private final ArrayList<EditText> verifyBoxes = new ArrayList<>();
    private final ArrayList<EditText> oldBoxes = new ArrayList<>();
    private final ArrayList<EditText> newBoxes = new ArrayList<>();
    private final ArrayList<EditText> confirmBoxes = new ArrayList<>();

    // 全局布局监听，用于动态处理键盘遮挡
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    private int previousHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用传统方式绑定布局
        setContentView(R.layout.activity_password);

        // 初始化控件引用
        initViews();
        // 解析传递过来的模式参数
        parseIntentParams();
        // 读取本地已经加密存储的密码状态
        initData();
        // 动态构建和装配 UI 界面
        setupUI();
        // 注册键盘高度监听自适应避让器
        setupKeyboardAdjustment();
    }

    /**
     * 传统 findViewById 初始化
     */
    private void initViews() {
        tvPageTitle = findViewById(R.id.tvPageTitle);
        tvPageSubtitle = findViewById(R.id.tvPageSubtitle);
        llFormGroup = findViewById(R.id.llFormGroup);
        tvHintText = findViewById(R.id.tvHintText);
        llActionArea = findViewById(R.id.llActionArea);
        btnPrimary = findViewById(R.id.btnPrimary);
    }

    private void parseIntentParams() {
        pageType = getIntent().getIntExtra("type", 1);
        String action = getIntent().getStringExtra("action");

        isVerifyMode = "verify".equals(action) || pageType == 3;
        if (pageType == 3) {
            pageType = 1;
        }
    }

    private void initData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 登录密码 (pageType == 4) 不需要本地原密码校验
        if (pageType == 4) {
            hasExistingPassword = false;
            return;
        }

        String bridgeKey = (pageType == 2) ? KEY_VIRTUAL_PASSWORD : KEY_PASSWORD;
        String oppositeKey = (pageType == 2) ? KEY_PASSWORD : KEY_VIRTUAL_PASSWORD;

        currentSavedMd5 = prefs.getString(bridgeKey, "");
        oppositeSavedMd5 = prefs.getString(oppositeKey, "");
        hasExistingPassword = !currentSavedMd5.isEmpty();
    }

    private void setupUI() {
        String pwdTypeName = "安全密码";
        if (pageType == 2) pwdTypeName = "虚拟密码";
        if (pageType == 4) pwdTypeName = "登录密码";

        if (isVerifyMode) {
            tvPageTitle.setText("安全验证");
            tvPageSubtitle.setText("请输入 6 位数字密码进行验证");
            llActionArea.setVisibility(View.GONE);

            createPwdGroupView("输入密码", verifyBoxes);
        } else {
            tvPageTitle.setText(hasExistingPassword ? "重置" + pwdTypeName : "设置" + pwdTypeName);
            tvPageSubtitle.setText("请输入 6 位数字以安全访问隐私控制台");
            llActionArea.setVisibility(View.VISIBLE);

            if (hasExistingPassword && pageType != 4) {
                createPwdGroupView("验证原" + pwdTypeName, oldBoxes);
            }
            createPwdGroupView("输入新" + pwdTypeName, newBoxes);
            createPwdGroupView("再次确认新" + pwdTypeName, confirmBoxes);
        }

        btnPrimary.setOnClickListener(v -> handlePasswordSubmit());
    }

    /**
     * 动态生成并向容器中添加单组 6 位密码方格
     */
    private void createPwdGroupView(String label, ArrayList<EditText> boxList) {
        View groupView = LayoutInflater.from(this).inflate(R.layout.view_pwd_group, llFormGroup, false);

        TextView tvLabel = groupView.findViewById(R.id.tvGroupLabel);
        tvLabel.setText(label);

        int[] resIds = {R.id.etBox0, R.id.etBox1, R.id.etBox2, R.id.etBox3, R.id.etBox4, R.id.etBox5};
        for (int i = 0; i < 6; i++) {
            EditText et = groupView.findViewById(resIds[i]);

            // ================= 【针对小米/华为安全键盘的硬核强刷】 =================
            et.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_NORMAL);
            // ====================================================================

            boxList.add(et);
            setupBoxRowLogic(boxList, i);
        }
        llFormGroup.addView(groupView);
    }

    /**
     * 联动流转机制：包含焦点拦截清除、自动向后移位、大退格删除、满位自动提交
     */
    private void setupBoxRowLogic(ArrayList<EditText> boxList, final int index) {
        final EditText currentBox = boxList.get(index);

        // 焦点监听
        currentBox.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.postDelayed(() -> {
                    ScrollView scrollView = findViewById(R.id.rootScrollView);
                    if (scrollView != null) {
                        int targetY = llFormGroup.getTop() + ((View) v.getParent()).getTop() - 30;
                        scrollView.smoothScrollTo(0, targetY);
                    }
                }, 150);

                boolean hasContent = false;
                for (int i = index; i < 6; i++) {
                    if (boxList.get(i).getText().length() > 0) {
                        hasContent = true;
                        break;
                    }
                }
                if (hasContent) {
                    for (int i = index; i < 6; i++) {
                        boxList.get(i).setText("");
                    }
                }
            }
        });

        // 输入监听
        currentBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    if (index < 5) {
                        boxList.get(index + 1).requestFocus();
                    } else if (index == 5 && isVerifyMode) {
                        triggerVerification();
                    }
                }
            }
        });

        // 按键监听
        currentBox.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (currentBox.getText().length() == 0 && index > 0) {
                    boxList.get(index - 1).setText("");
                    boxList.get(index - 1).requestFocus();
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 暴力计算视窗高度差，在沉浸式环境下手工垫起 Padding 避让键盘
     */
    private void setupKeyboardAdjustment() {
        final View rootView = findViewById(android.R.id.content);
        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();

                // 计算键盘占用高度
                int keypadHeight = screenHeight - r.bottom;

                if (keypadHeight != previousHeight) {
                    previousHeight = keypadHeight;

                    ScrollView sv = findViewById(R.id.rootScrollView);
                    if (sv != null) {
                        if (keypadHeight > screenHeight * 0.15) {
                            int bottomPadding = keypadHeight - 80;
                            sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(), Math.max(bottomPadding, keypadHeight));

                            View currentFocusView = getCurrentFocus();
                            if (currentFocusView != null && currentFocusView.getParent() != null) {
                                currentFocusView.postDelayed(() -> {
                                    int targetY = llFormGroup.getTop() + ((View) currentFocusView.getParent()).getTop() - 30;
                                    sv.smoothScrollTo(0, targetY);
                                }, 100);
                            }
                        } else {
                            int originalBottom = (int) (40 * getResources().getDisplayMetrics().density);
                            sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(), originalBottom);
                        }
                    }
                }
            }
        };
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (globalLayoutListener != null) {
            findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
        }
    }

    /**
     * 纯验证模式下的核心校验触发
     */
    private void triggerVerification() {
        String pwd = getBoxesText(verifyBoxes);
        if (pwd.length() == 6) {
            showStatus("正在验证中...", "success");
            String inputMd5 = getMd5(pwd);
            if (inputMd5.equals(currentSavedMd5)) {
                showStatus("验证成功", "success");
                finish();
            } else {
                showStatus("密码错误，验证失败", "error");
                clearBoxes(verifyBoxes);
            }
        }
    }

    /**
     * 创建/重置模式下的确认提交逻辑
     */
    /**
     * 创建/重置模式下的确认提交逻辑
     */
    private void handlePasswordSubmit() {
        String newStr = getBoxesText(newBoxes);
        String confirmStr = getBoxesText(confirmBoxes);

        // 修改登录密码 (pageType == 4)
        if (pageType == 4) {
            if (newStr.length() < 6) {
                showStatus("登录密码未填满，必须为 6 位数字", "error");
                return;
            }
            if (!newStr.equals(confirmStr)) {
                showStatus("两次输入的密码不一致", "error");
                return;
            }
            submitLoginPassword(newStr);
            return;
        }

        // 常规安全密码 / 虚拟密码逻辑
        if (newStr.length() < 6) {
            String name = (pageType == 2) ? "虚拟密码" : "安全密码";
            showStatus(name + "未填满，必须为 6 位数字", "error");
            return;
        }

        if (!newStr.equals(confirmStr)) {
            showStatus("两次输入的密码不一致", "error");
            return;
        }

        if (Data.MemberBillingBridge.get() == null) {
            showStatus("安全服务未就绪", "error");
            return;
        }

        String oldStr = getBoxesText(oldBoxes);

        // 委托给闭源 AAR 执行权限校验、比对与持久化
        Data.MemberBillingBridge.get().savePinPassword(this, App.UID, pageType, oldStr, newStr, new Data.IMemberBilling.PasswordActionCallback() {
            @Override
            public void onSuccess() {
                showStatus("密码建立成功", "success");
                llFormGroup.postDelayed(activity_password.this::finish, 500);
            }

            @Override
            public void onFailure(String errorMessage) {
                if ("VIP_REQUIRED".equals(errorMessage)) {
                    showStatus("虚拟密码为 VIP 独享特权功能", "error");
                    Data.MemberBillingBridge.get().showSubscriptionDialog(activity_password.this);
                } else {
                    showStatus(errorMessage, "error");
                    if (errorMessage.contains("原密码")) {
                        clearBoxes(oldBoxes);
                    }
                }
            }
        });
    }

    /**
     * 发送网络请求修改登录密码
     */
    /**
     * 发送网络请求修改登录密码（委托 AAR 闭源验签提交）
     */
    private void submitLoginPassword(String newPassword) {
        showStatus("正在提交新密码...", "success");
        btnPrimary.setEnabled(false);

        if (Data.MemberBillingBridge.get() == null) {
            showStatus("安全服务未就绪", "error");
            btnPrimary.setEnabled(true);
            return;
        }

        Data.MemberBillingBridge.get().resetLoginPassword(this, App.UID, newPassword, new Data.IMemberBilling.PasswordActionCallback() {
            @Override
            public void onSuccess() {
                showStatus("登录密码修改成功", "success");
                llFormGroup.postDelayed(activity_password.this::finish, 500);
            }

            @Override
            public void onFailure(String errorMessage) {
                showStatus(errorMessage, "error");
                btnPrimary.setEnabled(true);
            }
        });
    }

    private String getBoxesText(ArrayList<EditText> boxList) {
        StringBuilder sb = new StringBuilder();
        for (EditText et : boxList) {
            sb.append(et.getText().toString());
        }
        return sb.toString().trim();
    }

    private void clearBoxes(ArrayList<EditText> boxList) {
        for (EditText et : boxList) {
            et.setText("");
        }
        if (!boxList.isEmpty()) {
            boxList.get(0).requestFocus();
        }
    }

    private void showStatus(String msg, String type) {
        tvHintText.setVisibility(View.VISIBLE);
        tvHintText.setText(msg);
        if ("error".equals(type)) {
            tvHintText.setTextColor(Color.parseColor("#e54d42"));
        } else if ("success".equals(type)) {
            tvHintText.setTextColor(Color.parseColor("#38a169"));
        } else {
            tvHintText.setTextColor(Color.parseColor("#999999"));
        }
    }

    private String getMd5(String toEncrypt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(toEncrypt.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}