package Utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;

import com.blankj.utilcode.util.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

public class Permission {
    private static final String TAG = "Permission";

    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    private static boolean isActivityInvalid(Activity activity) {
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }

    /**
     * 统一的权限说明对话框（安全防泄漏版）
     */
    private static void showRationaleDialog(final Activity activity, final String message, final Runnable onConfirm) {
        if (isActivityInvalid(activity)) return;

        activity.runOnUiThread(() -> {
            if (isActivityInvalid(activity)) return;

            try {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("权限申请说明")
                        .setMessage(message)
                        .setPositiveButton("去授权", (dialogInterface, which) -> {
                            dialogInterface.dismiss();
                            if (onConfirm != null) {
                                onConfirm.run();
                            }
                        })
                        .setNegativeButton("暂不授予", (dialogInterface, which) -> dialogInterface.dismiss())
                        .setCancelable(false)
                        .create();

                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setColor(Color.WHITE);
                    float radius = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            16f,
                            activity.getResources().getDisplayMetrics()
                    );
                    drawable.setCornerRadius(radius);
                    window.setBackgroundDrawable(drawable);
                }
            } catch (Exception e) {
                Log.e(TAG, "显示权限说明弹窗异常", e);
            }
        });
    }

    /**
     * 麦克风录音权限（语音消息必需）
     */
    public static void checkAudioRecord(final Activity that, final PermissionCallback callback) {
        if (PermissionUtils.isGranted(Manifest.permission.RECORD_AUDIO)) {
            if (callback != null) callback.onGranted();
            return;
        }

        String msg = "发送语音消息需要访问麦克风进行录音，请在接下来的对话框中授予权限。";
        showRationaleDialog(that, msg, () -> {
            PermissionUtils.permission(Manifest.permission.RECORD_AUDIO)
                    .callback(new PermissionUtils.SimpleCallback() {
                        @Override
                        public void onGranted() {
                            if (callback != null) callback.onGranted();
                        }

                        @Override
                        public void onDenied() {
                            if (callback != null) callback.onDenied();
                            showGoToSettingsDialog(that, "麦克风权限被拒绝", "未获得录音权限，无法发送语音消息。请前往应用设置开启。");
                        }
                    }).request();
        });
    }

    /**
     * 媒体与相册访问权限（适配 Android 13/14 细粒度媒体权限）
     */
    public static void checkStorageMedia(final Activity that, final PermissionCallback callback) {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (!PermissionUtils.isGranted(Manifest.permission.READ_MEDIA_IMAGES)) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (!PermissionUtils.isGranted(Manifest.permission.READ_MEDIA_VIDEO)) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (!PermissionUtils.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (perms.isEmpty()) {
            if (callback != null) callback.onGranted();
            return;
        }

        String msg = "发送图片或视频需要读取本地多媒体文件，请在接下来的提示中授予读取权限。";
        showRationaleDialog(that, msg, () -> {
            PermissionUtils.permission(perms.toArray(new String[0]))
                    .callback(new PermissionUtils.SimpleCallback() {
                        @Override
                        public void onGranted() {
                            if (callback != null) callback.onGranted();
                        }

                        @Override
                        public void onDenied() {
                            if (callback != null) callback.onDenied();
                            showGoToSettingsDialog(that, "存储/相册权限被拒绝", "未获得读取相册权限，无法选择并发送图片。请在设置中开启。");
                        }
                    }).request();
        });
    }

    /**
     * 相机权限（扫码或拍摄）
     */
    public static void checkCamera(final Activity that, final PermissionCallback callback) {
        if (PermissionUtils.isGranted(Manifest.permission.CAMERA)) {
            if (callback != null) callback.onGranted();
            return;
        }

        String msg = "使用扫描二维码或拍照功能需要使用相机，请在弹窗中允许授权。";
        showRationaleDialog(that, msg, () -> {
            PermissionUtils.permission(Manifest.permission.CAMERA)
                    .callback(new PermissionUtils.SimpleCallback() {
                        @Override
                        public void onGranted() {
                            if (callback != null) callback.onGranted();
                        }

                        @Override
                        public void onDenied() {
                            if (callback != null) callback.onDenied();
                            showGoToSettingsDialog(that, "相机权限被拒绝", "应用需要相机权限以完成拍照和扫码。请点击去设置开启。");
                        }
                    }).request();
        });
    }

    /**
     * 位置权限
     */
    public static void checkLocation(final Activity that, final PermissionCallback callback) {
        if (PermissionUtils.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                && PermissionUtils.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (callback != null) callback.onGranted();
            return;
        }

        String msg = "使用附近的人或位置共享服务需要获取您的地理位置，请允许授予定位权限。";
        showRationaleDialog(that, msg, () -> {
            PermissionUtils.permissionGroup(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }).callback(new PermissionUtils.SimpleCallback() {
                @Override
                public void onGranted() {
                    if (callback != null) callback.onGranted();
                }

                @Override
                public void onDenied() {
                    if (callback != null) callback.onDenied();
                    showGoToSettingsDialog(that, "定位权限被拒绝", "未开启定位服务将无法获取位置信息，请前往系统设置授予定位权限。");
                }
            }).request();
        });
    }

    /**
     * 引导用户前往系统应用详情设置页
     */
    public static void showGoToSettingsDialog(final Activity activity, final String title, final String message) {
        if (isActivityInvalid(activity)) return;

        activity.runOnUiThread(() -> {
            if (isActivityInvalid(activity)) return;

            try {
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("去设置", (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                            intent.setData(uri);
                            try {
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "打开应用设置页失败", e);
                            }
                        })
                        .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "显示设置引导对话框异常", e);
            }
        });
    }
}