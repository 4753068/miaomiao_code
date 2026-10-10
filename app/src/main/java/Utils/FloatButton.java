package Utils;

import static android.content.Context.VIBRATOR_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.qapp.midian.App;
import com.qapp.midian.R;
import com.qapp.midian.activity_friend;

import java.lang.ref.WeakReference;

import Data.CheckUserUtil;

public class FloatButton {

    private static WeakReference floatingBtnRef = null;
    private static WeakReference messageNumberRef = null;
    private static WeakReference rootContainerRef = null;
    private static WeakReference floatContainerRef = null;

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
                FrameLayout parentView = rootContainerRef != null ? (FrameLayout) rootContainerRef.get() : null;

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

        // ==============================================================
        // 检查并展示首次启动的悬浮窗引导
        // ==============================================================
        boolean hasShownGuide = SPUtils.getInstance().get("has_shown_chat_guide", false);
        if (!hasShownGuide) {
            showGuideOverlay(activity, rootContainer, floatContainer, size);
        }
    }

    /**
     * 动态生成引导遮罩层
     */
    private static void showGuideOverlay(Activity activity, FrameLayout rootContainer, FrameLayout floatContainer, int fbSize) {
        // 1. 创建全屏遮罩层
        FrameLayout guideOverlay = new FrameLayout(activity);
        guideOverlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        guideOverlay.setBackgroundColor(Color.parseColor("#B3000000"));
        guideOverlay.setClickable(true);

        // 2. 创建包裹内容的垂直线性布局
        LinearLayout guideContent = new LinearLayout(activity);
        guideContent.setOrientation(LinearLayout.VERTICAL);
        guideContent.setGravity(Gravity.CENTER_HORIZONTAL);

        // 3. 【X 关闭图标按钮】替换掉原来的文字胶囊按钮
        ImageView btnClose = new ImageView(activity);
        btnClose.setImageResource(R.drawable.btn_closed);
        btnClose.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // 设定可点击区域大小为 36dp，内部留有 6dp 内边距方便手指轻触
        int closeBtnSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, activity.getResources().getDisplayMetrics());
        int closeBtnPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, activity.getResources().getDisplayMetrics());
        btnClose.setPadding(closeBtnPadding, closeBtnPadding, closeBtnPadding, closeBtnPadding);

        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(closeBtnSize, closeBtnSize);
        closeParams.gravity = Gravity.CENTER_HORIZONTAL;
        closeParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, activity.getResources().getDisplayMetrics());
        btnClose.setLayoutParams(closeParams);

        // 点击 X 按钮：记录状态为 true 并销毁当前遮罩
        btnClose.setOnClickListener(v -> {
            SPUtils.getInstance().put("has_shown_chat_guide", true);
            rootContainer.removeView(guideOverlay);
        });

        // 4. "点击这里开始聊天" 提示文本
        TextView tvHint = new TextView(activity);
        tvHint.setText("点击这里开始聊天");
        tvHint.setTextColor(Color.WHITE);
        tvHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvHint.setTypeface(null, Typeface.BOLD);
        tvHint.setPadding(0, 0, 0, 10);

        int hintWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, activity.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(hintWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = Gravity.CENTER_HORIZONTAL;
        tvHint.setLayoutParams(hintParams);
        tvHint.setGravity(Gravity.CENTER);

        // 5. 虚线箭头图片
        ImageView ivArrow = new ImageView(activity);
        ivArrow.setImageResource(R.drawable.xuxianjiantou);
        ivArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int arrowWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, activity.getResources().getDisplayMetrics());
        int arrowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, activity.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(arrowWidth, arrowHeight);
        ivArrow.setLayoutParams(arrowParams);

        // 组装内容：X 关闭图标 -> 提示文字 -> 箭头
        guideContent.addView(btnClose);
        guideContent.addView(tvHint);
        guideContent.addView(ivArrow);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guideOverlay.addView(guideContent, contentParams);

        int floatIndex = rootContainer.indexOfChild(floatContainer);
        if (floatIndex >= 0) {
            rootContainer.addView(guideOverlay, floatIndex);
        } else {
            rootContainer.addView(guideOverlay);
        }

        // 6. 动态计算尺寸并将其悬浮对齐到 FloatButton 正上方
        guideContent.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                guideContent.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                FrameLayout.LayoutParams fbParams = (FrameLayout.LayoutParams) floatContainer.getLayoutParams();
                int fbX = fbParams.leftMargin;
                int fbY = fbParams.topMargin;

                int contentWidth = guideContent.getWidth();
                int contentHeight = guideContent.getHeight();

                int screenWidth = rootContainer.getWidth();
                if (screenWidth == 0) {
                    screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                }

                int targetX = fbX + (fbSize / 2) - (contentWidth / 2);

                if (targetX < 20) {
                    targetX = 20;
                } else if (targetX + contentWidth > screenWidth - 20) {
                    targetX = screenWidth - contentWidth - 20;
                }

                // 偏移计算
                int top60dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, activity.getResources().getDisplayMetrics());
                int targetY = fbY - contentHeight - 10 + top60dp;

                if (targetY < 20) {
                    targetY = fbY + fbSize + 10;
                    ivArrow.setRotation(180f);
                }

                guideContent.setTranslationX(targetX);
                guideContent.setTranslationY(targetY);
            }
        });
    }

    public static void updateAlpha() {
        ImageView btn = floatingBtnRef != null ? (ImageView) floatingBtnRef.get() : null;
        if (btn != null) {
            int savedAlpha = SPUtils.getInstance().get("Floatalpha", 5);
            btn.setAlpha(savedAlpha / 10f);
        }
    }

    public static void updateMessageCount(int count) {
        TextView tv = messageNumberRef != null ? (TextView) messageNumberRef.get() : null;
        if (tv == null) return;

        if (count > 0) {
            tv.setText(count > 99 ? "99+" : String.valueOf(count));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    public static void Remove() {
        FrameLayout root = rootContainerRef != null ? (FrameLayout) rootContainerRef.get() : null;
        FrameLayout floatView = floatContainerRef != null ? (FrameLayout) floatContainerRef.get() : null;

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