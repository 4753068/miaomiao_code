package Dialog;

import static Data.MemberUtils.ShowVipDialog;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_service;
import com.qapp.midian.activity_setting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import Data.MemberUtils;
import Utils.RingUtils;
import Utils.SPUtils;

public class SettingDialog {

    // 提供给 activity_service 扫码后回填文本的全局引用
    public static EditText currentDialogEditText = null;

    public static void ShowTimeDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_burn_setting, null);
        Switch switchBurn = view.findViewById(R.id.switchBurn);
        TextView tvTimeLabel = view.findViewById(R.id.tvTimeLabel);
        LinearLayout layoutTimeBox = view.findViewById(R.id.layoutTimeBox);
        TextView tvDisplayTime = view.findViewById(R.id.tvDisplayTime);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        final int[] chosenHour = {22};
        final int[] chosenMinute = {30};

        String savedTime = SPUtils.getInstance().get("setting_deletetime","0000");
        boolean isBurnOpen = SPUtils.getInstance().get("deletetime_open", false);

        switchBurn.setChecked(isBurnOpen);
        if (savedTime.length() == 4) {
            try {
                chosenHour[0] = Integer.parseInt(savedTime.substring(0, 2));
                chosenMinute[0] = Integer.parseInt(savedTime.substring(2, 4));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        tvDisplayTime.setText(String.format("%02d : %02d", chosenHour[0], chosenMinute[0]));

        toggleTimeClickView(isBurnOpen, layoutTimeBox, tvTimeLabel, tvDisplayTime);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        switchBurn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleTimeClickView(isChecked, layoutTimeBox, tvTimeLabel, tvDisplayTime);
        });

        layoutTimeBox.setOnClickListener(v -> {
            if (switchBurn.isChecked()) {
                TimePickerDialog timePickerDialog = new TimePickerDialog(context,
                        (view1, hourOfDay, minute) -> {
                            chosenHour[0] = hourOfDay;
                            chosenMinute[0] = minute;
                            tvDisplayTime.setText(String.format("%02d : %02d", hourOfDay, minute));
                        }, chosenHour[0], chosenMinute[0], true);
                timePickerDialog.show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (!MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                return;
            }
            boolean isOpen = switchBurn.isChecked();
            if (isOpen) {
                String formatTime = String.format("%02d%02d", chosenHour[0], chosenMinute[0]);
                SPUtils.getInstance().put("setting_deletetime",formatTime);
                SPUtils.getInstance().put("deletetime_open",true);
                Toast.makeText(context, "定时焚毁已开启，执行时间：" + String.format("%02d:%02d", chosenHour[0], chosenMinute[0]), Toast.LENGTH_SHORT).show();
            } else {
                SPUtils.getInstance().remove("setting_deletetime");
                SPUtils.getInstance().put("deletetime_open",false);
                Toast.makeText(context, "定时焚毁已关闭", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private static void toggleTimeClickView(boolean isEnabled, LinearLayout layoutTimeBox, TextView tvTimeLabel, TextView tvDisplayTime) {
        layoutTimeBox.setEnabled(isEnabled);
        layoutTimeBox.setClickable(isEnabled);
        int labelColor = isEnabled ? Color.parseColor("#000000") : Color.parseColor("#C0C4CC");
        tvTimeLabel.setTextColor(labelColor);
        layoutTimeBox.setAlpha(isEnabled ? 1.0f : 0.4f);
    }

    public static void Zidongfenhui(Context context) {

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_instant_burn, null);
        Switch switchZidongfenhui = view.findViewById(R.id.switchZidongfenhui);
        Switch switchDuhoufenhui = view.findViewById(R.id.switchDuhoufenhui);
        Switch switchBackdelete = view.findViewById(R.id.switchBackdelete);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        boolean isZidongfenhui =SPUtils.getInstance().get("switch_zidongfenhui", false);
        boolean isDuhoufenhui = SPUtils.getInstance().get("switch_duhoufenhui", false);
        boolean isBackdelete = SPUtils.getInstance().get("switch_backdelete", false);

        switchZidongfenhui.setChecked(isZidongfenhui);
        switchDuhoufenhui.setChecked(isDuhoufenhui);
        switchBackdelete.setChecked(isBackdelete);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (!MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                return;
            }

            boolean currentZidong = switchZidongfenhui.isChecked();
            boolean currentDuhou = switchDuhoufenhui.isChecked();
            boolean currentBack = switchBackdelete.isChecked();

            SPUtils.getInstance().put("switch_zidongfenhui", currentZidong);
            SPUtils.getInstance().put("switch_duhoufenhui", currentDuhou);
            SPUtils.getInstance().put("switch_backdelete", currentBack);

            Toast.makeText(context, "即时焚毁配置已更新", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showSoundDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_notice_setting, null);
        Switch switchSound = view.findViewById(R.id.switchSound);
        Switch switchVibrate = view.findViewById(R.id.switchVibrate);
        Switch switchShortVibrate = view.findViewById(R.id.switchShortVibrate);
        Switch switchFriendShortVibrate = view.findViewById(R.id.switchFriendShortVibrate);
        Switch switchCloseNotice = view.findViewById(R.id.switchCloseNotice);

        List<CheckBox> cbList = new ArrayList<>();
        cbList.add(view.findViewById(R.id.cbSound1));
        cbList.add(view.findViewById(R.id.cbSound2));
        cbList.add(view.findViewById(R.id.cbSound3));
        cbList.add(view.findViewById(R.id.cbSound4));
        cbList.add(view.findViewById(R.id.cbSound5));
        cbList.add(view.findViewById(R.id.cbSound6));

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        boolean openSound = SPUtils.getInstance().get("OpenSound", false);
        boolean openVibrate = SPUtils.getInstance().get("OpenVibrate", false);
        boolean shortVibrate = SPUtils.getInstance().get("ShortVibrate", false);
        boolean FriendShortVibrate = SPUtils.getInstance().get("FriendShortVibrate", false);
        boolean CloseNotice = SPUtils.getInstance().get("CloseNotice", false);
        int currentSoundIndex =  SPUtils.getInstance().get("sound", 1);

        switchSound.setChecked(openSound);
        switchVibrate.setChecked(openVibrate);
        switchShortVibrate.setChecked(shortVibrate);
        switchFriendShortVibrate.setChecked(FriendShortVibrate);
        switchCloseNotice.setChecked(CloseNotice);
        if (currentSoundIndex >= 1 && currentSoundIndex <= 6) {
            cbList.get(currentSoundIndex - 1).setChecked(true);
        }

        for (int i = 0; i < cbList.size(); i++) {
            final CheckBox currentCb = cbList.get(i);
            final int soundIndex = i + 1;

            currentCb.setOnClickListener(v -> {
                if (currentCb.isChecked()) {
                    for (CheckBox cb : cbList) {
                        if (cb != currentCb) {
                            cb.setChecked(false);
                        }
                    }
                    RingUtils.PlayRing(soundIndex);
                } else {
                    currentCb.setChecked(true);
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int selectedSound = 1;
            for (int i = 0; i < cbList.size(); i++) {
                if (cbList.get(i).isChecked()) {
                    selectedSound = i + 1;
                    break;
                }
            }
            boolean soundState = switchSound.isChecked();
            boolean vibrateState = switchVibrate.isChecked();
            boolean isShortVibrateChecked = switchShortVibrate.isChecked();
            boolean isFriendShortVibrateChecked = switchFriendShortVibrate.isChecked();
            boolean isCloseNotice = switchCloseNotice.isChecked();

            SPUtils.getInstance().put("OpenSound", soundState);
            SPUtils.getInstance().put("OpenVibrate", vibrateState);
            SPUtils.getInstance().put("ShortVibrate", isShortVibrateChecked);
            SPUtils.getInstance().put("FriendShortVibrate", isFriendShortVibrateChecked);
            SPUtils.getInstance().put("CloseNotice", isCloseNotice);
            SPUtils.getInstance().put("sound", selectedSound);

            Toast.makeText(context, "提醒设置已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showSecurityModeDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_security_verify, null);
        List<CheckBox> cbList = new ArrayList<>();
        cbList.add(view.findViewById(R.id.cbModeNone));
        cbList.add(view.findViewById(R.id.cbModePassword));
        cbList.add(view.findViewById(R.id.cbModeFingerprint));

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        int savedMode =SPUtils.getInstance().get("setting_yanzhengmode", 0);

        if (savedMode >= 0 && savedMode < cbList.size()) {
            cbList.get(savedMode).setChecked(true);
        }

        for (int i = 0; i < cbList.size(); i++) {
            final CheckBox currentCb = cbList.get(i);
            currentCb.setOnClickListener(v -> {
                if (currentCb.isChecked()) {
                    for (CheckBox cb : cbList) {
                        if (cb != currentCb) {
                            cb.setChecked(false);
                        }
                    }
                } else {
                    currentCb.setChecked(true);
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (!MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                return;
            }
            int selectedMode = 0;
            for (int i = 0; i < cbList.size(); i++) {
                if (cbList.get(i).isChecked()) {
                    selectedMode = i;
                    break;
                }
            }
            SPUtils.getInstance().put("setting_yanzhengmode", selectedMode);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showChatLockDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_chat_lock_setting, null);
        Switch switchHideChat = view.findViewById(R.id.switchHideChat);
        Switch switchEnterSend = view.findViewById(R.id.switchEnterSend);
        Switch switchHomeLock = view.findViewById(R.id.switchHomeLock);
        Switch switchBackLock = view.findViewById(R.id.switchBackLock);
        Switch switchSensorLock = view.findViewById(R.id.switchSensorLock);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        boolean isHideChat =SPUtils.getInstance().get("switch_hidechat", false);
        boolean isEnterSend = SPUtils.getInstance().get("enterkey_sendmessage", false);
        boolean isHomeLock =SPUtils.getInstance().get("switch_homelock", false);
        boolean isBackLock =SPUtils.getInstance().get("switch_backlock", false);
        boolean isSensorLock =SPUtils.getInstance().get("switch_sensorlock", false);

        switchHideChat.setChecked(isHideChat);
        switchEnterSend.setChecked(isEnterSend);
        switchHomeLock.setChecked(isHomeLock);
        switchBackLock.setChecked(isBackLock);
        switchSensorLock.setChecked(isSensorLock);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            boolean currentHideChat = switchHideChat.isChecked();
            boolean currentEnterSend = switchEnterSend.isChecked();
            boolean currentHomeLock = switchHomeLock.isChecked();
            boolean currentBackLock = switchBackLock.isChecked();
            boolean currentSensorLock = switchSensorLock.isChecked();

            SPUtils.getInstance().put("switch_hidechat", currentHideChat);
            SPUtils.getInstance().put("enterkey_sendmessage", currentEnterSend);
            SPUtils.getInstance().put("switch_homelock", currentHomeLock);
            SPUtils.getInstance().put("switch_backlock", currentBackLock);
            SPUtils.getInstance().put("switch_sensorlock", currentSensorLock);

            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showMkeyDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_click_mkey_setting, null);
        List<CheckBox> cbList = new ArrayList<>();
        cbList.add(view.findViewById(R.id.cbActionContacts));
        cbList.add(view.findViewById(R.id.cbActionRecentChat));
        Switch switchLongClickBurn = view.findViewById(R.id.switchLongClickBurn);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        int savedClickValue = 0;
        try {
            savedClickValue = SPUtils.getInstance().get("setting_clickmkey", 0);
        } catch (ClassCastException e) {
            SPUtils.getInstance().remove("setting_clickmkey");
            SPUtils.getInstance().put("setting_clickmkey", 0);
        }
        boolean savedLongClickValue = SPUtils.getInstance().get("setting_longclickmkey", false);

        if (savedClickValue >= 0 && savedClickValue < cbList.size()) {
            cbList.get(savedClickValue).setChecked(true);
        }
        switchLongClickBurn.setChecked(savedLongClickValue);

        switchLongClickBurn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed() && isChecked) {
                if (!MemberUtils.GetVipStatic()) {
                    ShowVipDialog(activity_setting.that);
                    buttonView.setChecked(false);
                }
            }
        });

        for (int i = 0; i < cbList.size(); i++) {
            final CheckBox currentCb = cbList.get(i);
            currentCb.setOnClickListener(v -> {
                if (currentCb.isChecked()) {
                    for (CheckBox cb : cbList) {
                        if (cb != currentCb) {
                            cb.setChecked(false);
                        }
                    }
                } else {
                    currentCb.setChecked(true);
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int finalClickMode = 0;
            for (int i = 0; i < cbList.size(); i++) {
                if (cbList.get(i).isChecked()) {
                    finalClickMode = i;
                    break;
                }
            }

            if (switchLongClickBurn.isChecked() && !MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                switchLongClickBurn.setChecked(false);
                return;
            }

            boolean finalLongClickBurn = switchLongClickBurn.isChecked();
            SPUtils.getInstance().put("setting_clickmkey", finalClickMode);
            SPUtils.getInstance().put("setting_longclickmkey", finalLongClickBurn);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showLockModeDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_lock_screen_mode, null);
        List<CheckBox> cbList = new ArrayList<>();
        cbList.add(view.findViewById(R.id.cbModeNone));
        cbList.add(view.findViewById(R.id.cbModeBackAppHome));
        cbList.add(view.findViewById(R.id.cbModeBackHome));
        cbList.add(view.findViewById(R.id.cbModeHomeAndBurn));
        cbList.add(view.findViewById(R.id.cbModeDesktopAndBurn));

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        int savedMode = SPUtils.getInstance().get("setting_suopingmode", 0);
        if (savedMode >= 0 && savedMode < cbList.size()) {
            cbList.get(savedMode).setChecked(true);
        }

        for (int i = 0; i < cbList.size(); i++) {
            final CheckBox currentCb = cbList.get(i);
            currentCb.setOnClickListener(v -> {
                if (currentCb.isChecked()) {
                    for (CheckBox cb : cbList) {
                        if (cb != currentCb) {
                            cb.setChecked(false);
                        }
                    }
                } else {
                    currentCb.setChecked(true);
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (!MemberUtils.GetVipStatic()) {
                ShowVipDialog(activity_setting.that);
                return;
            }
            int selectedMode = 0;
            for (int i = 0; i < cbList.size(); i++) {
                if (cbList.get(i).isChecked()) {
                    selectedMode = i;
                    break;
                }
            }
            SPUtils.getInstance().put("setting_suopingmode", selectedMode);
            dialog.dismiss();
        });
        dialog.show();
    }

    public static void showWeChatBindDialog(Context context, String bindCode) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_wechat_bind, null);
        TextView tvBindCode = view.findViewById(R.id.tvBindCode);
        Button btnBindClose = view.findViewById(R.id.btnBindClose);

        if (bindCode != null && !bindCode.isEmpty()) {
            tvBindCode.setText(bindCode);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnBindClose.setOnClickListener(v -> dialog.dismiss());
    }

    // 弹出“添加节点”专属风格对话框
    public static void showAddServiceDialog(activity_service that) {
        AlertDialog.Builder builder = new AlertDialog.Builder(that);
        View dialogView = LayoutInflater.from(that).inflate(R.layout.dialog_add_server, null);
        builder.setView(dialogView);

        EditText etInput = dialogView.findViewById(R.id.etServerId);

        // 将当前输入框赋给静态变量，供扫码返回后回填
        currentDialogEditText = etInput;

        // 绑定扫码图标点击事件
        ImageView ivScanQr = dialogView.findViewById(R.id.ivScanQr);
        if (ivScanQr != null) {
            ivScanQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    that.startQrCodeScan();
                }
            });
        }

        Button btnCancel = dialogView.findViewById(R.id.btnAddServerCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnAddServerConfirm);

        if (etInput != null) {
            etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            InputFilter alphaNumericFilter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    for (int i = start; i < end; i++) {
                        if (!Character.isLetterOrDigit(source.charAt(i))) {
                            return "";
                        }
                    }
                    return null;
                }
            };
            etInput.setFilters(new InputFilter[]{alphaNumericFilter});
        }

        final AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 弹窗关闭时释放静态引用，防止内存泄漏
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                currentDialogEditText = null;
            }
        });

        dialog.show();

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (etInput != null) {
                        String serverId = etInput.getText().toString().trim();
                        if (!serverId.isEmpty()) {
                            dialog.dismiss();

                            if (Data.MemberBillingBridge.get() == null || App.db == null) {
                                Toast.makeText(that, "核心服务未初始化", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 委托闭源 AAR 安全查询节点并写入本地数据库
                            Data.MemberBillingBridge.get().addServerByPassword(that, App.UID, App.db, serverId, new Data.IMemberBilling.AddServerCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(that, "共享节点添加成功", Toast.LENGTH_SHORT).show();
                                    // 刷新节点列表页面
                                    activity_service.Get_Server();
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    Toast.makeText(that, errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            });

                        } else {
                            Toast.makeText(that, "ID不能为空", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }
    }

    public static void showBackupRestoreDialog(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_backup_setting, null);
        View btnCloseDialog = view.findViewById(R.id.btnCloseDialog);
        Button btnRestore = view.findViewById(R.id.btnRestore);
        Button btnBackup = view.findViewById(R.id.btnBackup);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        }

        btnRestore.setOnClickListener(v -> {
            Toast.makeText(context, "正在从云端恢复设置...", Toast.LENGTH_SHORT).show();
            MemberUtils.GetSetting();
            dialog.dismiss();
        });

        btnBackup.setOnClickListener(v -> {
            Toast.makeText(context, "正在备份当前设置到云端...", Toast.LENGTH_SHORT).show();
            String settingJson = SPUtils.getInstance().getAllAsJson();
            MemberUtils.SaveSetting(settingJson);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showShareDialog(Context context, String serverName, String password) {
        Bitmap posterBitmap = createSharePoster(context, serverName, password);
        if (posterBitmap == null) {
            Toast.makeText(context, "分享海报生成失败", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(40, 50, 40, 40);

        ImageView posterImage = new ImageView(context);
        posterImage.setAdjustViewBounds(true);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imgParams.setMargins(0, 0, 0, 40);
        posterImage.setImageBitmap(posterBitmap);
        layout.addView(posterImage, imgParams);

        Button shareBtn = new Button(context);
        shareBtn.setText("保存图库并复制文案");
        shareBtn.setBackgroundResource(R.drawable.bg_capsule_button);
        shareBtn.setTextColor(Color.WHITE);
        shareBtn.setTextSize(15);
        shareBtn.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        layout.addView(shareBtn, btnParams);

        AlertDialog shareDialog = new AlertDialog.Builder(context)
                .setView(layout)
                .create();

        shareBtn.setOnClickListener(v -> {
            saveImageToGallery(context, posterBitmap);

            String shareText = "您好友邀请你用喵喵APP聊天了，这是一款端对端加密的聊天应用，可以自设节点，保证数据和隐私安全，您通过以下密钥增加节点，就可以享受独有的加密聊天乐趣，请复制如下密钥，打开喵喵应用-设置-服务器节点-添加共享节点添加，密钥为：\n" + password;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ShareNode", shareText);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(context, "文案已复制，分享图已保存到相册！", Toast.LENGTH_SHORT).show();
            shareDialog.dismiss();
        });

        if (shareDialog.getWindow() != null) {
            shareDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        shareDialog.show();

        if (shareDialog.getWindow() != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.WHITE);
            drawable.setCornerRadius(40f);
            shareDialog.getWindow().setBackgroundDrawable(drawable);
        }
    }

    private static Bitmap createSharePoster(Context context, String serverName, String content) {
        try {
            Bitmap bgBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.qrcodebg);
            if (bgBitmap == null) return null;

            Bitmap posterBitmap = bgBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(posterBitmap);

            int bgWidth = posterBitmap.getWidth();
            int bgHeight = posterBitmap.getHeight();

            int qrSize = (int) (bgWidth * 0.48);
            int qrX = (bgWidth - qrSize) / 2;
            int qrY = (int) (bgHeight * 0.57 - qrSize / 2);

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap qrBitmap = barcodeEncoder.createBitmap(bitMatrix);

            canvas.drawBitmap(qrBitmap, qrX, qrY, null);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#444444"));
            textPaint.setTextSize(144f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);

            float textY = qrY + qrSize + 150;

            if (serverName != null && !serverName.isEmpty()) {
                canvas.drawText(serverName, bgWidth / 2f, textY, textPaint);
            }

            return posterBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveImageToGallery(Context context, Bitmap bitmap) {
        try {
            String savedImageURL = MediaStore.Images.Media.insertImage(
                    context.getContentResolver(),
                    bitmap,
                    "MiaoMiaoNode_" + System.currentTimeMillis(),
                    "喵喵节点分享海报"
            );
            if (savedImageURL == null) {
                Toast.makeText(context, "海报保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "保存相册失败，请检查存储权限", Toast.LENGTH_SHORT).show();
        }
    }
}