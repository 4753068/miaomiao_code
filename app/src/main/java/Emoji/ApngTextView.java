package Emoji;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

public class ApngTextView extends AppCompatTextView {

    public ApngTextView(Context context) { super(context); }
    public ApngTextView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 当 TextView 滚进屏幕时，自动恢复播放动画
        ApngExpressionParser.startAnimators(getText());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 当 TextView 滚出屏幕、页面关闭时，自动停止动画释放 CPU 线程
        ApngExpressionParser.stopAnimators(getText());
    }

    public static void setText(Context context, TextView textView, String message, int size) {
        int emojiSize = size;
        CharSequence formattedText = ApngExpressionParser.parseExpression(
                context,     // Context
                textView,    // 接收标准的 TextView，EditText 也能传进来
                message,     // 原始文本
                emojiSize    // 表情像素大小
        );
        textView.setText(formattedText);
    }

    /**
     * 新增一个专为插入（Insert）服务的复用方法，返回生成的 CharSequence
     */
    public static CharSequence getApngCharSequence(Context context, TextView textView, String message, int size) {
        return ApngExpressionParser.parseExpression(context, textView, message, size);
    }
}
