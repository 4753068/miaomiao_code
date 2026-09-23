package Utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class RecycleViewDivider extends RecyclerView.ItemDecoration {

    private Paint mPaint;
    private Drawable mDivider;
    private int mDividerHeight = 2; // 分割线高度/厚度，默认 2px
    private final int mOrientation; // 列表方向
    private boolean mDrawLastItemDivider = true; // 是否绘制最后一项的分割线

    private static final int[] ATTRS = new int[]{android.R.attr.listDivider};

    /**
     * 默认系统分割线
     */
    public RecycleViewDivider(Context context, int orientation, int i, int color) {
        if (orientation != LinearLayoutManager.VERTICAL && orientation != LinearLayoutManager.HORIZONTAL) {
            throw new IllegalArgumentException("请输入正确的方向参数 (LinearLayoutManager.VERTICAL 或 HORIZONTAL)");
        }
        this.mOrientation = orientation;
        final TypedArray a = context.obtainStyledAttributes(ATTRS);
        this.mDivider = a.getDrawable(0);
        a.recycle();

        if (mDivider != null) {
            int intrinsicHeight = mDivider.getIntrinsicHeight();
            this.mDividerHeight = intrinsicHeight > 0 ? intrinsicHeight : 2;
        }
    }

    /**
     * 自定义图片/资源分割线
     */
    public RecycleViewDivider(Context context, int orientation, @DrawableRes int drawableId) {
        if (orientation != LinearLayoutManager.VERTICAL && orientation != LinearLayoutManager.HORIZONTAL) {
            throw new IllegalArgumentException("请输入正确的方向参数");
        }
        this.mOrientation = orientation;
        this.mDivider = ContextCompat.getDrawable(context, drawableId);
        if (mDivider != null) {
            int intrinsicHeight = (orientation == LinearLayoutManager.VERTICAL)
                    ? mDivider.getIntrinsicHeight()
                    : mDivider.getIntrinsicWidth();
            this.mDividerHeight = intrinsicHeight > 0 ? intrinsicHeight : 2;
        }
    }

    /**
     * 自定义纯色分割线
     */
    public RecycleViewDivider(int orientation, int dividerHeight, @ColorInt int dividerColor) {
        if (orientation != LinearLayoutManager.VERTICAL && orientation != LinearLayoutManager.HORIZONTAL) {
            throw new IllegalArgumentException("请输入正确的方向参数");
        }
        this.mOrientation = orientation;
        this.mDividerHeight = Math.max(1, dividerHeight);
        this.mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.mPaint.setColor(dividerColor);
        this.mPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置是否绘制列表最后一项的分割线
     */
    public RecycleViewDivider setDrawLastItemDivider(boolean drawLast) {
        this.mDrawLastItemDivider = drawLast;
        return this;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);

        int itemPosition = parent.getChildAdapterPosition(view);
        int totalCount = state.getItemCount();

        // 如果不绘制最后一项，且当前条目是末项，则不预留空间
        if (!mDrawLastItemDivider && itemPosition == totalCount - 1) {
            outRect.set(0, 0, 0, 0);
            return;
        }

        if (mOrientation == LinearLayoutManager.VERTICAL) {
            outRect.set(0, 0, 0, mDividerHeight);
        } else {
            outRect.set(0, 0, mDividerHeight, 0);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);
        if (mOrientation == LinearLayoutManager.VERTICAL) {
            drawVertical(c, parent);
        } else {
            drawHorizontal(c, parent);
        }
    }

    private void drawVertical(Canvas canvas, RecyclerView parent) {
        final int left = parent.getPaddingLeft();
        final int right = parent.getMeasuredWidth() - parent.getPaddingRight();
        final int childSize = parent.getChildCount();
        final int totalItemCount = parent.getAdapter() != null ? parent.getAdapter().getItemCount() : 0;

        for (int i = 0; i < childSize; i++) {
            final View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (!mDrawLastItemDivider && position == totalItemCount - 1) {
                continue;
            }

            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int top = child.getBottom() + layoutParams.bottomMargin;
            final int bottom = top + mDividerHeight;

            if (mPaint != null) {
                canvas.drawRect(left, top, right, bottom, mPaint);
            } else if (mDivider != null) {
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
            }
        }
    }

    private void drawHorizontal(Canvas canvas, RecyclerView parent) {
        final int top = parent.getPaddingTop();
        final int bottom = parent.getMeasuredHeight() - parent.getPaddingBottom();
        final int childSize = parent.getChildCount();
        final int totalItemCount = parent.getAdapter() != null ? parent.getAdapter().getItemCount() : 0;

        for (int i = 0; i < childSize; i++) {
            final View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (!mDrawLastItemDivider && position == totalItemCount - 1) {
                continue;
            }

            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int left = child.getRight() + layoutParams.rightMargin;
            final int right = left + mDividerHeight;

            if (mPaint != null) {
                canvas.drawRect(left, top, right, bottom, mPaint);
            } else if (mDivider != null) {
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
            }
        }
    }
}