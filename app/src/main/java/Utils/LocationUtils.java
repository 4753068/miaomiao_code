package Utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.qapp.midian.App;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import Data.MemberBillingBridge;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocationUtils {

    private static final String TAG = "LocationUtils";
    private static volatile LocationUtils instance;

    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;
    private WeakReference<OnLocationResultListener> resultListenerRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;
    private int currentUid = 0;
    private static boolean isPrivacyAgreed = false;

    public interface OnLocationResultListener {
        void onSuccess(AMapLocation location);
        void onFailure(int errorCode, String errorInfo);
        default void onServerUploadResult(boolean success, String responseOrError) {}
    }

    private LocationUtils() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static LocationUtils getInstance() {
        if (instance == null) {
            synchronized (LocationUtils.class) {
                if (instance == null) {
                    instance = new LocationUtils();
                }
            }
        }
        return instance;
    }

    public synchronized void initPrivacy(Context context) {
        if (isPrivacyAgreed || context == null) return;
        Context appContext = context.getApplicationContext();
        AMapLocationClient.updatePrivacyShow(appContext, true, true);
        AMapLocationClient.updatePrivacyAgree(appContext, true);
        isPrivacyAgreed = true;
    }

    private void initClient(Context context) {
        if (locationClient == null && context != null) {
            try {
                Context appContext = context.getApplicationContext();
                initPrivacy(appContext);

                locationClient = new AMapLocationClient(appContext);
                locationOption = getDefaultOption();
                locationClient.setLocationListener(internalLocationListener);
            } catch (Exception e) {
                Log.e(TAG, "初始化高德定位失败", e);
            }
        }
    }

    /**
     * 单次定位并上报
     */
    public void startSingleLocation(Context context, OnLocationResultListener listener) {
        this.currentUid = App.UID;
        this.resultListenerRef = listener != null ? new WeakReference<>(listener) : null;
        initClient(context);

        if (locationClient != null && locationOption != null) {
            locationClient.stopLocation();

            locationOption.setOnceLocation(true);
            locationOption.setOnceLocationLatest(true);

            locationClient.setLocationOption(locationOption);
            locationClient.startLocation();
        }
    }

    /**
     * 连续定位并上报
     */
    public void startContinuousLocation(Context context, int uid, long interval, OnLocationResultListener listener) {
        this.currentUid = uid;
        this.resultListenerRef = listener != null ? new WeakReference<>(listener) : null;
        initClient(context);

        if (locationClient != null && locationOption != null) {
            locationClient.stopLocation();

            locationOption.setOnceLocation(false);
            locationOption.setInterval(Math.max(interval, 1000));

            locationClient.setLocationOption(locationOption);
            locationClient.startLocation();
        }
    }

    public void stopLocation() {
        if (locationClient != null && locationClient.isStarted()) {
            locationClient.stopLocation();
        }
    }

    public void destroy() {
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
            locationClient = null;
            locationOption = null;
        }
        if (resultListenerRef != null) {
            resultListenerRef.clear();
            resultListenerRef = null;
        }
    }

    private final AMapLocationListener internalLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {
            OnLocationResultListener listener = resultListenerRef != null ? resultListenerRef.get() : null;

            if (aMapLocation == null) {
                if (listener != null) {
                    listener.onFailure(-1, "Location result is null");
                }
                return;
            }

            if (locationOption != null && locationOption.isOnceLocation()) {
                stopLocation();
            }

            if (aMapLocation.getErrorCode() == 0) {
                if (listener != null) {
                    listener.onSuccess(aMapLocation);
                }
                // 👉 将经纬度交由闭源 AAR 桥接上传
                MemberBillingBridge.get().updateLocation(
                        currentUid,
                        aMapLocation.getLatitude(),
                        aMapLocation.getLongitude(),
                        aMapLocation.getCity(),
                        (success, responseOrError) -> {
                            // 切回主线程反馈
                            mainHandler.post(() -> {
                                OnLocationResultListener l = resultListenerRef != null ? resultListenerRef.get() : null;
                                if (l != null) {
                                    l.onServerUploadResult(success, responseOrError);
                                }
                            });
                        }
                );

            } else {
                Log.e(TAG, "定位失败: ErrCode=" + aMapLocation.getErrorCode() + ", Info=" + aMapLocation.getErrorInfo());
                if (listener != null) {
                    listener.onFailure(aMapLocation.getErrorCode(), aMapLocation.getErrorInfo());
                }
            }
        }
    };

    /**
     * 动态获取接口地址并使用 OkHttp 表单上传
     */
    private void uploadLocationToServer(int uid, AMapLocation location) {
        if (uid <= 0) {
            Log.w(TAG, "UID 无效，取消位置上传");
            return;
        }

        String serverUrl = App.DataServiceUrl + "/?data=miaomiao&file=member&func=update_location";
        String city = location.getCity() != null ? location.getCity() : "";

        RequestBody formBody = new FormBody.Builder()
                .add("uid", String.valueOf(uid))
                .add("lat", String.valueOf(location.getLatitude()))
                .add("lng", String.valueOf(location.getLongitude()))
                .add("city", city)
                .build();

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(formBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "位置上传失败: " + e.getMessage());
                mainHandler.post(() -> {
                    OnLocationResultListener listener = resultListenerRef != null ? resultListenerRef.get() : null;
                    if (listener != null) {
                        listener.onServerUploadResult(false, e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        mainHandler.post(() -> {
                            OnLocationResultListener listener = resultListenerRef != null ? resultListenerRef.get() : null;
                            if (listener != null) {
                                listener.onServerUploadResult(true, responseData);
                            }
                        });
                    } else {
                        String errorMsg = "HTTP error code: " + response.code();
                        mainHandler.post(() -> {
                            OnLocationResultListener listener = resultListenerRef != null ? resultListenerRef.get() : null;
                            if (listener != null) {
                                listener.onServerUploadResult(false, errorMsg);
                            }
                        });
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private AMapLocationClientOption getDefaultOption() {
        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setNeedAddress(true);
        option.setHttpTimeOut(20000); // 调整为更合理的 20 秒超时
        option.setLocationCacheEnable(false);
        option.setMockEnable(false);
        return option;
    }
}