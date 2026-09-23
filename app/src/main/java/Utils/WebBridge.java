package Utils;

import static service.foreground.context;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseIntArray;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.blankj.utilcode.util.FileUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_main;
import com.qapp.midian.activity_service;
import com.qapp.midian.activity_update;
import com.qapp.midian.activity_web;

import org.json.JSONException;

import Data.MemberUtils;
import socket.AutoReconnectWebSocket;

public class WebBridge {



    /**
     * 供网页端 JavaScript 调用的本地方法
     */
    @JavascriptInterface
    public void setBurnStatus(String name,String value) {
        // 调用之前写好的 SPUtils 工具类
        //Log.e("TBA","设置SP:"+value);
        if (!"delete_sp".equals(value)) {
            if (name.equals("uid") || name.equals("setting_appitem") || name.equals("setting_clickmkey")  ||
                    name.equals("setting_longclickmkey") ||
                    name.equals("setting_suopingmode") ||
                    name.equals("setting_yanzhengmode") ||
                    name.equals("switch_homelock") ||
                    name.equals("sound") ||
                    name.equals("ShortVibrate") ||
                    name.equals("OpenVibrate") ||
                    name.equals("OpenSound") ||
                    name.equals("switch_zidongfenhui") ||
                    name.equals("switch_duhoufenhui") ||
                    name.equals("switch_backdelete") ||
                    name.equals("switch_apphomelock") ||
                    name.equals("switch_hidechat") ||
                    name.equals("enterkey_sendmessage")
            )

            {
               int int_value=Integer.parseInt(value);
               SPUtils.getInstance().put(name, int_value);

                if (name.equals("switch_homelock"))
                {
                    App.FriendPass=false;
                }

            }
            else if (name.equals("vip"))
            {
                long int_value=Long.parseLong(value);
                SPUtils.getInstance().put(name, int_value);
            }
            else {
                SPUtils.getInstance().put(name, value);
            }
        }
        else {
            SPUtils.getInstance().remove(name);
        }
    }

    /**
     * 供网页端调用：获取 Android 本地所有的 SP 配置
     * @return JSON 字符串
     */
    @JavascriptInterface
    public String getConfig() {
        return SPUtils.getInstance().getAllAsJson();
    }

    @JavascriptInterface
    public void startSocket() {
        // 【核心修复 1】从磁盘(SP)中把前端刚刚存进去的 uid 重新读取到内存变量 App.UID 中
        App.UID = SPUtils.getInstance().get("uid", 0);

        // 【核心修复 2】确认 UID 大于 0 后，真正启动 WebSocket
        if (App.UID > 0) {
            AutoReconnectWebSocket.startSocket();
            Log.e("WebBridge", "前端触发启动 Socket 成功，当前 UID=" + App.UID);
        } else {
            Log.e("WebBridge", "前端触发启动 Socket 失败，因为内存中获取到的 UID 仍为 0");
        }
    }

    /**
     * 前端调用的页面跳转方法
     */
    @JavascriptInterface
    public void toAppPage() {
        // 注意：由于 JS 接口是在子线程(JavaBridge)中运行的
        // 如果你的 toAppPage 涉及 WebView 刷新等 UI 操作，务必包在 runOnUiThread 中！
        if (com.qapp.midian.activity_main.that != null) {
            com.qapp.midian.activity_main.that.runOnUiThread(() -> {
                FBWebView.toAppPage("site_item");
            });
        }
    }

    /**
     * 验证密码
     */
    @JavascriptInterface
    public void VirtualPassword(String password) {
        FBMessage.Show(App.AppContext,password);
    }

    @JavascriptInterface
    public void toUpdate() {
        Intent it = new Intent();
        it.setClass(App.AppContext, activity_update.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.AppContext.startActivity(it);
    }

    @JavascriptInterface
    public void toServer() {
        Intent it = new Intent();
        it.setClass(App.AppContext, activity_service.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.AppContext.startActivity(it);
    }

    @JavascriptInterface
    public void AppFinish() {
        activity_main.that.finish();
    }

    @JavascriptInterface
    public void setHomePage(int which)
    {
        Log.e("TBA","HOMEPAGE:"+which);
        if (which>1)
        {
            String name="com.qapp.midian.activity_main";
            if (which==2)  name="com.qapp.midian.Activity_Toutiao";
            if (which==3)  name="com.qapp.midian.Activity_Xigua";
            if (which==4)  name="com.qapp.midian.Activity_Douyin";
            if (which==3)  name="com.qapp.midian.Activity_Tengxun";
            if (which==6)  name="com.qapp.midian.Activity_Xiaohongshu";
            if (which==7)  name="com.qapp.midian.Activity_Wangyi";
            HomePageUtils.changeLauncher(name);
        }
        else
        {
            try {
                PackageManager packageManager = App.AppContext.getPackageManager();
                packageManager.setComponentEnabledSetting(new ComponentName(App.AppContext, "com.qapp.midian.activity_main"),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                Thread.sleep(1000);
                HomePageUtils.getCurrentLauncher();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


        }
        SPUtils.getInstance().put("setting_appitem",which);
    }

    @JavascriptInterface
    public void closePage() {

        if (context instanceof Activity) {
            ((Activity) context).finish(); // 销毁当前 Activity
        }
    }
    @JavascriptInterface
    public void Cleancache()
    {
        FileUtils.deleteAllInDir(App.Folder+"/Cache");
        FBMessage.Show(App.AppContext,"临时文件已删除");
    }

    @JavascriptInterface
    public void ExitApp()
    {
        MemberUtils.Exit();
    }

    @JavascriptInterface
    public void PlayRing(int which) {
        RingUtils.PlayRing(which);
    }

    @JavascriptInterface
    public void getUserToken(String callbackName, WebView mWebView) {
        // 1. 从 APP 本地获取 Token (假设已经登录)
        String userToken = SPUtils.getInstance().get("sign","");
        String nickname = "APP大侠";

        // 2. 拼接成 JSON 字符串
        final String jsonResult = String.format("{\"token\":\"%s\", \"nickname\":\"%s\"}", userToken, nickname);

        // 3. 切换到主线程，执行网页的 JS 回调函数
        mWebView.post(new Runnable() {
            @Override
            public void run() {
                mWebView.evaluateJavascript("javascript:" + callbackName + "('" + jsonResult + "')", null);
            }
        });
    }

}
