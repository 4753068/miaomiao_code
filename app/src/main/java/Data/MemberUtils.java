package Data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.qapp.midian.App;
import com.qapp.midian.activity_login;
import com.qapp.midian.activity_setting;

import Utils.HomePageUtils;
import Utils.SPUtils;
import socket.AutoReconnectWebSocket;

public class MemberUtils {

    /**
     * 安全退出登录
     */
    public static void Exit() {
        if (MemberBillingBridge.get() == null) return;

        // 委托闭源 AAR 向服务端发起带签名的登出请求
        MemberBillingBridge.get().logout(activity_setting.that, App.UID, new IMemberBilling.LogoutCallback() {
            @Override
            public void onCleanHostResources() {
                // 1. 彻底擦除宿主端本地持久化缓存
                SPUtils.getInstance().clear();
                App.UID = 0;

                // 2. 异步释放网络与常驻系统资源
                new Thread(() -> {
                    try {
                        if (AutoReconnectWebSocket.socket != null) {
                            AutoReconnectWebSocket.socket.disconnect();
                        }
                    } catch (Exception ignored) {}

                    try {
                        Intent serviceIntent = new Intent(App.AppContext, service.foreground.class);
                        App.AppContext.stopService(serviceIntent);
                    } catch (Exception ignored) {}
                }).start();

                // 3. 回到主线程执行 UI 页面路由转换
                new Handler(Looper.getMainLooper()).post(() -> {
                    Context context = (activity_setting.that != null && !activity_setting.that.isFinishing() && !activity_setting.that.isDestroyed())
                            ? activity_setting.that
                            : App.AppContext;

                    Intent intent = new Intent(context, activity_login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(intent);

                    if (activity_setting.that != null && !activity_setting.that.isFinishing()) {
                        activity_setting.that.finish();
                    }
                });
            }
        });
    }

    /**
     * 实时鉴权用户 VIP 状态
     */
    public static boolean GetVipStatic() {
        if (MemberBillingBridge.get() == null) return false;
        return MemberBillingBridge.get().isVip(HomePageUtils::getCurrentLauncher);
    }

    /**
     * 统一调起开通会员弹窗
     */
    public static void ShowVipDialog(Activity activity) {
        if (MemberBillingBridge.get() != null) {
            MemberBillingBridge.get().showSubscriptionDialog(activity);
        }
    }

    /**
     * 上报个人设置（防篡改）
     */
    public static void SaveSetting(String json) {
        if (MemberBillingBridge.get() != null && App.UID > 0) {
            MemberBillingBridge.get().saveMemberSetting(App.UID, json);
        }
    }

    /**
     * 获取个人设置
     */
    public static void GetSetting() {
        if (MemberBillingBridge.get() != null && App.UID > 0) {
            MemberBillingBridge.get().syncMemberSetting(App.UID, null);
        }
    }

    /**
     * 更新用户地理位置
     */
    public static void UpdateLocation(double lat, double lng, String city, String address) {
        // 过滤异常无效坐标
        if (App.UID <= 0 || (lat == 0 && lng == 0)) return;

        // 本地缓存更新
        SPUtils.getInstance().put("lat", String.valueOf(lat));
        SPUtils.getInstance().put("lng", String.valueOf(lng));
        SPUtils.getInstance().put("city", city != null ? city : "");

        // 委托闭源 AAR 发送带签名的加密请求
        if (MemberBillingBridge.get() != null) {
            MemberBillingBridge.get().updateLocation(App.UID, lat, lng, city, new IMemberBilling.LocationUploadCallback() {
                @Override
                public void onResult(boolean success, String responseOrError) {
                    if (success) {
                        // 位置上报成功逻辑
                    } else {
                        // 上报失败逻辑
                    }
                }
            });
        }
    }
}