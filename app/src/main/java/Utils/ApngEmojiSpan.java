package Utils;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;
import android.widget.TextView;


import java.lang.ref.WeakReference;

/**
 * 支持 APNG 动图混排且防抖动的 EmojiSpan
 * 继承 ReplacementSpan 以获得对尺寸和绘制的完全控制权
 */
public class ApngEmojiSpan extends ReplacementSpan {

    private final Drawable mDrawable;
    private final WeakReference<TextView> mTextViewRef;

    public ApngEmojiSpan(Drawable drawable, TextView textView) {
        this.mDrawable = drawable;
        this.mTextViewRef = new WeakReference<>(textView);

        // 核心：绑定 Drawable 的回调，实现动画帧切换时刷新 TextView
        this.mDrawable.setCallback(new Drawable.Callback() {
            @Override
            public void invalidateDrawable(Drawable who) {
                TextView tv = mTextViewRef.get();
                if (tv != null) {
                    // 局部重绘或直接重绘，保证动画流畅
                    tv.invalidate();
                }
            }

            @Override
            public void scheduleDrawable(Drawable who, Runnable what, long when) {
                TextView tv = mTextViewRef.get();
                if (tv != null) {
                    tv.postOnAnimation(what);
                }
            }

            @Override
            public void unscheduleDrawable(Drawable who, Runnable what) {
                TextView tv = mTextViewRef.get();
                if (tv != null) {
                    tv.removeCallbacks(what);
                }
            }
        });
    }

    /**
     * 计算 Span 的宽度
     */
    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Rect rect = mDrawable.getBounds();
        if (fm != null) {
            Paint.FontMetricsInt paintFm = paint.getFontMetricsInt();
            int fontHeight = paintFm.descent - paintFm.ascent;
            int drHeight = rect.bottom - rect.top;

            // 让图片的居中线和文字的居中线对齐
            int top = paintFm.ascent + (fontHeight - drHeight) / 2;
            int bottom = top + drHeight;

            fm.ascent = top;
            fm.top = top;
            fm.bottom = bottom;
            fm.descent = bottom;
        }
        return rect.right;
    }

    /**
     * 精确绘制 Span 到 Canvas 上
     */
    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        canvas.save();

        // 计算居中对齐的 Y 轴偏移量
        Rect rect = mDrawable.getBounds();
        Paint.FontMetricsInt paintFm = paint.getFontMetricsInt();
        // y 是 baseline 的坐标
        int transY = y + paintFm.ascent + ((paintFm.descent - paintFm.ascent) - (rect.bottom - rect.top)) / 2;

        // 移动画布并绘制
        canvas.translate(x, transY);
        mDrawable.draw(canvas);

        canvas.restore();
    }

    /**
     * 获取内部的 Drawable 实例（方便在外部控制 start / stop）
     */
    public Drawable getDrawable() {
        return mDrawable;
    }
}