package com.qapp.midian;

import static Utils.WindowUtils.setupTransparentStatusBarWithBlackText;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import Utils.BiometricPrompt;

public class activity_biometric extends AppCompatActivity {

    public static activity_biometric that = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric);
        that = this;
        setupTransparentStatusBarWithBlackText(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(activity_biometric.this, activity_main.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        BiometricPrompt.showBiometricPrompt(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (that == this) {
            that = null;
        }
    }
}