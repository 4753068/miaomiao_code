package Emoji;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;
import android.widget.TextView;

public class ApngImageSpan extends DynamicDrawableSpan {

    private final Drawable mDrawable;
    private final TextView mTextView;

    public ApngImageSpan(Drawable drawable, TextView textView) {
        super(ALIGN_BASELINE);
        this.mDrawable = drawable;
        this.mTextView = textView;

        mDrawable.setCallback(new Drawable.Callback() {
            @Override
            public void invalidateDrawable(Drawable who) {
                mTextView.invalidate();
            }

            @Override
            public void scheduleDrawable(Drawable who, Runnable what, long when) {
                // 将系统要求的绝对刷新时间，转换为相对当前的延迟时间
                long delay = when - android.os.SystemClock.uptimeMillis();
                // 确保延迟时间不小于0，然后使用 View 原生的 postDelayed
                mTextView.postDelayed(what, Math.max(0, delay));
            }

            @Override
            public void unscheduleDrawable(Drawable who, Runnable what) {
                mTextView.removeCallbacks(what);
            }
        });
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
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
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        canvas.save();
        int transY = top + (bottom - top - mDrawable.getBounds().bottom) / 2;
        canvas.translate(x, transY);
        mDrawable.draw(canvas);
        canvas.restore();
    }
}