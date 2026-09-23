package com.qapp.midian;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class activity_qr extends AppCompatActivity {
    private CaptureManager capture;
    private ImageButton buttonLed;
    private DecoratedBarcodeView barcodeScannerView;
    private boolean bTorch = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_qr);

        barcodeScannerView = findViewById(R.id.dbv);
        buttonLed = findViewById(R.id.button_led);

        // ==================== ✨ 核心优化 1：相机配置连续对焦 ====================
        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.setContinuousFocusEnabled(true); // 开启连续自动对焦，防止虚焦
        cameraSettings.setMeteringEnabled(true);        // 开启测光，适应屏幕反光
        barcodeScannerView.getBarcodeView().setCameraSettings(cameraSettings);

        // ==================== ✨ 核心优化 2：开启深度硬解码模式 ====================
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        // 关键点：TRY_HARDER 会耗费更多算法深度解析散点与有遮挡的二维码
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        // 支持识别反转色与边缘畸变
       // hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));

        // 仅解码 QR 码，排除一维码干扰并注入高精度配置
        barcodeScannerView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE), hints, "UTF-8", 2)
        );
        // ========================================================================

        barcodeScannerView.setTorchListener(new DecoratedBarcodeView.TorchListener() {
            @Override
            public void onTorchOn() {
                buttonLed.setBackground(getResources().getDrawable(R.drawable.xbutton_light_open));
                bTorch = true;
            }

            @Override
            public void onTorchOff() {
                buttonLed.setBackground(getResources().getDrawable(R.drawable.xbutton_light));
                bTorch = false;
            }
        });

        buttonLed.setOnClickListener(v -> {
            if (bTorch) {
                barcodeScannerView.setTorchOff();
            } else {
                barcodeScannerView.setTorchOn();
            }
        });

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
        barcodeScannerView.setTorchOff();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}