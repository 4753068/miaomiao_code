package Data;

import static android.content.Context.VIBRATOR_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_biometric;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;
import com.qapp.midian.activity_main;
import com.qapp.midian.activity_pin;
import com.qapp.midian.activity_web;

import Utils.BiometricPrompt;
import Utils.FBMessage;
import Utils.SPUtils;

public class CheckUserUtil {
    public static BiometricManager biometricManager = null;
    public static int ClickMkey = 0, LONGCLICKMKEY = 0, YanzhengMode = 0, Friend_Uid = 0, Friend_Userid = 0;

    public static void Biombet() {
        /** 监听指纹 **/
        biometricManager = BiometricManager.from(App.AppContext);
        int yanzhengMode = SPUtils.getInstance().get("setting_yanzhengmode", 0);
        switch (biometricManager.canAuthenticate()) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                BiometricPrompt.showBiometricPromptTag = 0;
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Toast.makeText(App.AppContext, "该设备上没有搭载可用的生物特征功能", Toast.LENGTH_SHORT).show();
                if (yanzhengMode == 2) {
                    SPUtils.getInstance().put("setting_yanzhengmode", 1);
                }
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Toast.makeText(App.AppContext, "生物识别功能当前不可用", Toast.LENGTH_SHORT).show();
                if (yanzhengMode == 2) {
                    SPUtils.getInstance().put("setting_yanzhengmode", 1);
                }
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Toast.makeText(App.AppContext, "用户没有录入生物识别数据", Toast.LENGTH_SHORT).show();
                if (yanzhengMode == 2) {
                    SPUtils.getInstance().put("setting_yanzhengmode", 1);
                }
                break;
        }
    }

    public static void ClickMkey() {
        ClickMkey = SPUtils.getInstance().get("setting_clickmkey", 0);
        YanzhengMode = SPUtils.getInstance().get("setting_yanzhengmode", 0);
        Friend_Uid = SPUtils.getInstance().get("friend_uid", 0);
        Friend_Userid = SPUtils.getInstance().get("friend_userid", 0);

        if (YanzhengMode == 2) {
            if (!App.FriendPass) {
                BiometricPrompt.showBiometricPrompt(activity_biometric.that);
            } else {
                if (ClickMkey == 0) {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_friend.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                } else {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_chat.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                }
            }
        } else if (YanzhengMode == 1) {
            String password = SPUtils.getInstance().get("password", "");
            if (SPUtils.getInstance().contains("password") && !password.isEmpty() && !App.FriendPass) {
                Intent it = new Intent();
                it.setClass(App.AppContext, activity_pin.class);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.AppContext.startActivity(it);
            } else {
                if (SPUtils.getInstance().contains("password")) {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_friend.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                } else {
                    Intent it = new Intent();
                    it.setClass(App.AppContext, activity_web.class);
                    it.putExtra("url", "file:///android_asset/password.html?action=verify");
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                }
            }
        } else if (YanzhengMode == 0) {
            Intent it = new Intent();
            if (ClickMkey == 1) {
                if (Friend_Uid == 0) {
                    it.setClass(App.AppContext, activity_friend.class);
                } else {
                    it.setClass(App.AppContext, activity_chat.class);
                    Bundle bundle_chat = new Bundle();
                    bundle_chat.putInt("userid", Friend_Userid);
                    bundle_chat.putInt("uid", Friend_Uid);
                    it.putExtras(bundle_chat);
                }
            } else {
                it.setClass(App.AppContext, activity_friend.class);
            }
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.AppContext.startActivity(it);
        }
    }

    @SuppressLint("Range")
    public static void LongClickMkey() {
        boolean longClickMKeyOpen = SPUtils.getInstance().get("setting_longclickmkey", false);

        Vibrator vibrator = (Vibrator) App.AppContext.getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(100);

        if (MemberBillingBridge.get() != null) {
            // 委托给闭源 AAR 进行 VIP 鉴权并清空聊天记录
            boolean success = MemberBillingBridge.get().executeLongClickMkeyAction(App.db, longClickMKeyOpen);
            if (success) {
                SPUtils.getInstance().remove("friend_uid");
                SPUtils.getInstance().remove("friend_userid");
                SPUtils.getInstance().remove("friend_nickname");
                SPUtils.getInstance().remove("friend_image");
            }
        }
    }

    public static void ShowPin(Context context) {
        // 1. 加载布局
        View view = LayoutInflater.from(context).inflate(R.layout.activity_pin, null);

        // 2. 初始化 6个验证码输入框 和 按钮
        EditText[] editTexts = new EditText[]{
                view.findViewById(R.id.etCode1),
                view.findViewById(R.id.etCode2),
                view.findViewById(R.id.etCode3),
                view.findViewById(R.id.etCode4),
                view.findViewById(R.id.etCode5),
                view.findViewById(R.id.etCode6)
        };
        Button btnContinue = view.findViewById(R.id.btnContinue);
        TextView tvResend = view.findViewById(R.id.tvResend);

        tvResend.setOnClickListener(v -> {
            if (activity_friend.that != null) {
                activity_friend.that.finish();
            }
        });

        // 3. 创建标准圆角无多余边框的 AlertDialog 容器
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        // 强制点击外部不可取消，必须验证或按返回键
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }

        // 4. 为输入框绑定联动逻辑（自动前进与后退）
        for (int i = 0; i < editTexts.length; i++) {
            final int currentIndex = i;

            editTexts[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null && s.length() == 1) {
                        if (currentIndex < editTexts.length - 1) {
                            editTexts[currentIndex + 1].requestFocus();
                        } else {
                            btnContinue.performClick();
                        }
                    }
                }
            });

            editTexts[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (editTexts[currentIndex].getText().toString().isEmpty() && currentIndex > 0) {
                        editTexts[currentIndex - 1].requestFocus();
                        editTexts[currentIndex - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
        }

        // 5. 确定按钮：执行安全验证与业务逻辑
        btnContinue.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (EditText editText : editTexts) {
                sb.append(editText.getText().toString().trim());
            }
            String inputCode = sb.toString();

            if (inputCode.length() < 6) {
                FBMessage.Show(context, "请输入六位数安全密码");
                return;
            }

            if (MemberBillingBridge.get() == null) {
                FBMessage.Show(context, "鉴权模块未就绪");
                return;
            }

            // 委托 AAR 闭源判定真实密码或虚假防查岗密码
            int verifyResult = MemberBillingBridge.get().verifyPinCode(inputCode);

            if (verifyResult == IMemberBilling.PIN_RESULT_REAL) {
                // 真实密码验证成功：展示正常页面
                App.FriendPass = true;
                if (activity_friend.biobackground != null) activity_friend.biobackground.setVisibility(View.GONE);
                if (activity_friend.RecyclerView != null) activity_friend.RecyclerView.setVisibility(View.VISIBLE);
                if (activity_friend.tips != null) activity_friend.tips.setVisibility(View.GONE);
                dialog.dismiss();

            } else if (verifyResult == IMemberBilling.PIN_RESULT_VIRTUAL) {
                // VIP 虚假密码验证成功：进入防查岗模式（不展示正常好友列表）
                App.FriendPass = true;
                if (activity_friend.RecyclerView != null) activity_friend.RecyclerView.setVisibility(View.GONE);
                if (activity_friend.tips != null) activity_friend.tips.setVisibility(View.VISIBLE);
                if (activity_friend.biobackground != null) activity_friend.biobackground.setVisibility(View.GONE);
                dialog.dismiss();

            } else {
                // 密码错误
                for (EditText editText : editTexts) {
                    editText.setText("");
                }
                editTexts[0].requestFocus();
                FBMessage.Show(context, "密码错误，请重新输入");
            }
        });

        // 6. 拦截弹窗的物理返回键（还原原 Activity 模式下的后退清理栈行为）
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                Intent intent = new Intent(context, activity_main.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(intent);

                if (context instanceof Activity) {
                    ((Activity) context).finish();
                }
                dialog.dismiss();
                return true;
            }
            return false;
        });

        // 7. 显示弹窗
        dialog.show();

        // 自动弹出软键盘
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        editTexts[0].requestFocus();
    }
}