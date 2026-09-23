package Utils;

import static android.content.Context.VIBRATOR_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_friend;

import java.lang.ref.WeakReference;

import Data.CheckUserUtil;

public class FloatButton {
    private static WeakReference<ImageView> floatingBtnRef = null;
    private static WeakReference<TextView> messageNumberRef = null;
    private static WeakReference<FrameLayout> rootContainerRef = null;
    private static WeakReference<FrameLayout> floatContainerRef = null;

    private static boolean isDragging = false;
    public static TextView messageNumber;
    @SuppressLint("ClickableViewAccessibility")
    public static void floatButton(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        int switchHidemkey = SPUtils.getInstance().get("switch_hidemkey", 0);
        FrameLayout rootContainer = activity.findViewById(R.id.rootContainer);
        if (rootContainer == null) {
            return;
        }
        rootContainerRef = new WeakReference<>(rootContainer);

        // 防止重复附着：先清理旧的悬浮组件
        Remove();

        if (switchHidemkey != 0) {
            return;
        }

        // 1. 创建组合容器
        FrameLayout floatContainer = new FrameLayout(activity);
        floatContainerRef = new WeakReference<>(floatContainer);

        int size = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48, activity.getResources().getDisplayMetrics()
        );

        // 2. 创建主按钮图标
        ImageView imageView = new ImageView(activity);
        imageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.btnhome));
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(size, size);
        imageView.setLayoutParams(imgParams);

        int savedAlpha = SPUtils.getInstance().get("Floatalpha", 5);
        imageView.setAlpha(savedAlpha / 10f);
        floatingBtnRef = new WeakReference<>(imageView);

        // 3. 创建角标红点
        messageNumber = new TextView(activity);
        messageNumber.setText("99+");
        messageNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        messageNumber.setTextColor(ContextCompat.getColor(activity, R.color.white));
        messageNumber.setGravity(Gravity.CENTER);
        messageNumber.setMinWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics()));
        messageNumber.setHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics()));

        int padding2dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, activity.getResources().getDisplayMetrics());
        messageNumber.setPadding(padding2dp, padding2dp, padding2dp, padding2dp);
        messageNumber.setTypeface(null, Typeface.BOLD);
        messageNumber.setBackground(ContextCompat.getDrawable(activity, R.drawable.newnum));
        messageNumber.setVisibility(View.GONE);

        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics())
        );
        textParams.gravity = Gravity.CENTER;
        messageNumber.setLayoutParams(textParams);
        messageNumberRef = new WeakReference<>(messageNumber);

        floatContainer.addView(imageView);
        floatContainer.addView(messageNumber);

        // 4. 定位与边距计算
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(size, size);
        if (!SPUtils.getInstance().contains("FloatButtonX")) {
            containerParams.leftMargin = screenWidth - size - 50;
            containerParams.topMargin = screenHeight - size - 50;
        } else {
            containerParams.leftMargin = SPUtils.getInstance().get("FloatButtonX", 0);
            containerParams.topMargin = SPUtils.getInstance().get("FloatButtonY", 0);
        }
        floatContainer.setLayoutParams(containerParams);

        // 5. 点击与长按事件
        floatContainer.setOnClickListener(v -> {
            if (!isDragging) {
                Intent it = new Intent(activity, activity_friend.class);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(it);
            }
        });

        floatContainer.setOnLongClickListener(v -> {
            if (!isDragging) {
                CheckUserUtil.LongClickMkey();
            }
            return true;
        });

        // 6. 拖动与边界检测
        floatContainer.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private boolean hasMoved = false;
            private static final int TOUCH_SLOP = 20;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
                FrameLayout parentView = rootContainerRef != null ? rootContainerRef.get() : null;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = event.getRawX() - layoutParams.leftMargin;
                        dY = event.getRawY() - layoutParams.topMargin;
                        hasMoved = false;
                        isDragging = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!hasMoved) {
                            float deltaX = Math.abs(event.getRawX() - (layoutParams.leftMargin + dX));
                            float deltaY = Math.abs(event.getRawY() - (layoutParams.topMargin + dY));
                            if (deltaX > TOUCH_SLOP || deltaY > TOUCH_SLOP) {
                                hasMoved = true;
                                isDragging = true;

                                Vibrator vibrator = (Vibrator) activity.getSystemService(VIBRATOR_SERVICE);
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                                    } else {
                                        vibrator.vibrate(50);
                                    }
                                }
                            }
                        }

                        if (hasMoved && parentView != null) {
                            int newX = (int) (event.getRawX() - dX);
                            int newY = (int) (event.getRawY() - dY);
                            newX = Math.max(0, Math.min(newX, parentView.getWidth() - view.getWidth()));
                            newY = Math.max(0, Math.min(newY, parentView.getHeight() - view.getHeight()));

                            layoutParams.leftMargin = newX;
                            layoutParams.topMargin = newY;

                            SPUtils.getInstance().put("FloatButtonX", newX);
                            SPUtils.getInstance().put("FloatButtonY", newY);
                            view.setLayoutParams(layoutParams);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (hasMoved) {
                            isDragging = false;
                            hasMoved = false;
                            return true;
                        }
                        isDragging = false;
                        hasMoved = false;
                        break;
                }
                return false;
            }
        });

        rootContainer.addView(floatContainer);
    }

    public static void updateAlpha() {
        ImageView btn = floatingBtnRef != null ? floatingBtnRef.get() : null;
        if (btn != null) {
            int savedAlpha = SPUtils.getInstance().get("Floatalpha", 5);
            btn.setAlpha(savedAlpha / 10f);
        }
    }

    public static void updateMessageCount(int count) {
        TextView tv = messageNumberRef != null ? messageNumberRef.get() : null;
        if (tv == null) return;

        if (count > 0) {
            tv.setText(count > 99 ? "99+" : String.valueOf(count));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    public static void Remove() {
        FrameLayout root = rootContainerRef != null ? rootContainerRef.get() : null;
        FrameLayout floatView = floatContainerRef != null ? floatContainerRef.get() : null;

        if (root != null && floatView != null) {
            root.removeView(floatView);
        }

        if (floatContainerRef != null) {
            floatContainerRef.clear();
        }
        if (floatingBtnRef != null) {
            floatingBtnRef.clear();
        }
        if (messageNumberRef != null) {
            messageNumberRef.clear();
        }
    }
}