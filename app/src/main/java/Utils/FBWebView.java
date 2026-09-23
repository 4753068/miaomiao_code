package Utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.qapp.midian.activity_main;

import org.json.JSONArray;
import org.json.JSONObject;

public class FBWebView extends WebView {
    private static final String TAG = "FBWebView";

    private ProgressBar progressBar;
    private FileChooserListener fileChooserListener;

    // 文件选择回调接口
    public interface FileChooserListener {
        void onShowFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams);
    }

    public FBWebView(Context context) {
        super(context);
        init();
    }

    public FBWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FBWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 🌟 安全加固：关闭跨域与本地敏感文件任意访问
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSavePassword(false);

        settings.setGeolocationEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 允许 HTTPS 页面加载 HTTP 混合内容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        initDefaultClients();
    }

    public void attachProgressBar(ProgressBar bar) {
        this.progressBar = bar;
    }

    public void setFileChooserListener(FileChooserListener listener) {
        this.fileChooserListener = listener;
    }

    private void initDefaultClients() {
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // 新版网络错误处理
            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // 旧版网络错误处理
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // 拦截页面跳转（Android 7.0+）
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleUrlLoading(view, request.getUrl().toString());
                }
                return false;
            }

            // 拦截页面跳转（旧版兼容）
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }
        });

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            // 处理 Android 5.0+ 文件选择上传
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileChooserListener != null) {
                    fileChooserListener.onShowFileChooser(filePathCallback, fileChooserParams);
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * 解决链接跳转并加固 Schema 调用
     */
    private boolean handleUrlLoading(WebView view, String url) {
        if (TextUtils.isEmpty(url) || url.equals("about:blank")) {
            return false;
        }

        // 常规网络请求由当前 WebView 内部直接承载
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }

        // 🌟 安全防护：禁止直接解析 file:// 和 content:// 链接以防越权
        if (url.startsWith("file://") || url.startsWith("content://")) {
            return true;
        }

        // 处理第三方 App 唤醒协议（微信、支付宝等合法 URI）
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            view.getContext().startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "无法调起外部应用打开协议: " + url, e);
            return true;
        }
    }

    public static void toAppPage(String url) {
        if (TextUtils.isEmpty(url)) return;

        if (url.contains("http")) {
            if (activity_main.webView != null) {
                activity_main.webView.post(() -> {
                    if (activity_main.webView != null) {
                        activity_main.webView.loadUrl(url);
                    }
                });
            }
        } else {
            int siteItem = SPUtils.getInstance().get("setting_appitem", 0);
            try {
                JSONArray jsonArray = new JSONArray(HomePageUtils.Site);
                if (siteItem >= 0 && siteItem < jsonArray.length()) {
                    JSONObject jsonObject = jsonArray.getJSONObject(siteItem);
                    String siteUrl = jsonObject.optString("url");
                    if (activity_main.webView != null && !TextUtils.isEmpty(siteUrl)) {
                        activity_main.webView.post(() -> {
                            if (activity_main.webView != null) {
                                activity_main.webView.loadUrl(siteUrl);
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "指定的 item 索引越界: " + siteItem);
                }
            } catch (Exception e) {
                Log.e(TAG, "解析配置站点异常", e);
            }
        }
    }
}