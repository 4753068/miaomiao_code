package Utils;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;
import com.qapp.midian.activity_main;

import java.util.concurrent.Executor;

import Data.CheckUserUtil;

public class BiometricPrompt {
    public static int showBiometricPromptTag = 0;

    public static void showBiometricPrompt(AppCompatActivity that) {
        if (that == null || that.isFinishing() || that.isDestroyed()) {
            return;
        }

        androidx.biometric.BiometricPrompt.PromptInfo promptInfo =
                new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("指纹验证")
                        .setNegativeButtonText("取消")
                        .setAllowedAuthenticators(BIOMETRIC_WEAK)
                        .setConfirmationRequired(true)
                        .build();

        Executor executor = ContextCompat.getMainExecutor(that);

        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(that,
                executor, new androidx.biometric.BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // 使用类名规范引用常量，避免编译器警告
                if (errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                        || errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                        activity_friend.that.finish();
                    }
                }
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                int friendUid = SPUtils.getInstance().get("friend_uid", 0);
                int friendUserid = SPUtils.getInstance().get("friend_userid", 0);
                int yanzhengMode = SPUtils.getInstance().get("setting_yanzhengmode", 0);

                App.FriendPass = true;
                App.isErrorPassword = false;

                if (yanzhengMode == 0) {
                    int clickMkey = SPUtils.getInstance().get("setting_clickmkey", 0);
                    Intent it = new Intent();
                    if (clickMkey == 0) {
                        it.setClass(that, activity_friend.class);
                    } else {
                        if (friendUid > 0) {
                            it.setClass(that, activity_chat.class);
                            Bundle bundle = new Bundle();
                            bundle.putInt("userid", friendUserid);
                            bundle.putInt("uid", friendUid);
                            it.putExtras(bundle);
                        } else {
                            it.setClass(that, activity_friend.class);
                        }
                    }
                    it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    App.AppContext.startActivity(it);
                    showBiometricPromptTag = 1;
                } else {
                    if (activity_main.that != null && !activity_main.that.isFinishing()) {
                        FBMessage.Show(activity_main.that, "指纹信息正确");
                    }
                    if (activity_friend.biobackground != null) {
                        activity_friend.biobackground.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                    CheckUserUtil.ShowPin(activity_friend.that);
                }
                Toast.makeText(App.AppContext, "指纹信息错误，请输入安全密码", Toast.LENGTH_SHORT).show();
            }
        });

        prompt.authenticate(promptInfo);
    }
}