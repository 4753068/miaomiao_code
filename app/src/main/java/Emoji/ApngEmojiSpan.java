package Emoji;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;
import android.widget.TextView;
import androidx.annotation.NonNull;

public class ApngEmojiSpan extends DynamicDrawableSpan {

    private final Drawable mDrawable;
    private final TextView mTextView;

    public ApngEmojiSpan(Drawable drawable, TextView textView) {
        super(ALIGN_BASELINE);
        this.mDrawable = drawable;
        this.mTextView = textView;

        // 绑定刷新回调给对应的 TextView (包括 EditText)
        mDrawable.setCallback(new Drawable.Callback() {
            @Override
            public void invalidateDrawable(@NonNull Drawable who) {
                // 【修复点】使用 postInvalidate 确保在 UI 线程安全触发重绘
                if (mTextView != null) {
                    mTextView.postInvalidate();
                }
            }

            @Override
            public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                if (mTextView != null) {
                    long delay = when - android.os.SystemClock.uptimeMillis();
                    mTextView.postDelayed(what, Math.max(0, delay));
                }
            }

            @Override
            public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                if (mTextView != null) {
                    mTextView.removeCallbacks(what);
                }
            }
        });
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Rect rect = mDrawable.getBounds();
        if (fm != null) {
            Paint.FontMetricsInt paintFm = paint.getFontMetricsInt();
            int fontHeight = paintFm.bottom - paintFm.top;
            int drHeight = rect.bottom - rect.top;
            int top = paintFm.top + (fontHeight - drHeight) / 2;
            int bottom = top + drHeight;
            fm.ascent = top;
            fm.top = top;
            fm.bottom = bottom;
            fm.descent = bottom;
        }
        return rect.right;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.save();
        int transY = top + (bottom - top - mDrawable.getBounds().bottom) / 2;
        canvas.translate(x, transY);
        mDrawable.draw(canvas);
        canvas.restore();
    }
}