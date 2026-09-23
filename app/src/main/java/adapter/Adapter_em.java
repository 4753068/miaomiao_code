package adapter;

import static Emoji.ApngTextView.getApngCharSequence;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qapp.midian.App;
import com.qapp.midian.R;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import Emoji.ApngTextView;
import Emoji.ApngEmojiSpan;

public class Adapter_em extends RecyclerView.Adapter<Adapter_em.ViewHolder> {
    private List<Module_em> mData;
    private Activity activity;
    private EditText mEditText;

    public Adapter_em(Activity activity, EditText mEditText, List<Module_em> mData) {
        this.activity = activity;
        this.mData = mData;
        this.mEditText = mEditText;
    }

    @NonNull
    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.module_emjo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        final String key = this.mData.get(position).getKey();
        holder.setData(key);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("Range")
            @Override
            public void onClick(View v) {
                insertText(mEditText, key);

                /* 加入到最近表情 */
                if (App.recentEmojis.contains(key)) App.recentEmojis.remove(key);
                App.recentEmojis.addFirst(key);
                if (App.recentEmojis.size() > 6) {
                    App.recentEmojis.removeLast();
                }

                long time = System.currentTimeMillis() / 1000;
                // 🌟 使用标准参数化查询，防止潜在符号转义与注入风险
                String sql = "select id from em where em=? order by id desc limit 1";
                try (Cursor cursor = App.db.rawQuery(sql, new String[]{key})) {
                    if (cursor == null || cursor.getCount() == 0) {
                        App.db.execSQL("insert into em(Em,Updatetime) values (?,?)", new Object[]{key, time});
                    } else {
                        cursor.moveToFirst();
                        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                        App.db.execSQL("update em set Updatetime=? where id=?", new Object[]{time, id});
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.icon != null && holder.icon.getText() != null) {
            Emoji.ApngExpressionParser.stopAnimators(holder.icon.getText());
            holder.icon.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return this.mData != null ? this.mData.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView icon = null;
        public Runnable stopTask;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);

            stopTask = new Runnable() {
                @Override
                public void run() {
                    if (icon != null && icon.getText() != null) {
                        Emoji.ApngExpressionParser.stopAnimators(icon.getText());
                    }
                }
            };
        }

        public void setData(String key) {
            icon.removeCallbacks(stopTask);

            if (icon.getText() != null) {
                Emoji.ApngExpressionParser.stopAnimators(icon.getText());
            }

            ApngTextView.setText(icon.getContext(), icon, key, 100);

            CharSequence text = icon.getText();
            if (text instanceof android.text.Spanned) {
                android.text.Spanned spanned = (android.text.Spanned) text;
                ApngEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), ApngEmojiSpan.class);

                for (ApngEmojiSpan span : spans) {
                    Drawable drawable = span.getDrawable();
                    if (drawable != null) {
                        drawable.setCallback(null);
                        setupSpanCallback(drawable, icon);
                    }
                }
            }

            if (icon.getText() != null) {
                Emoji.ApngExpressionParser.startAnimators(icon.getText());
                icon.postDelayed(stopTask, 250);
            }
        }

        private void setupSpanCallback(Drawable drawable, final TextView textView) {
            drawable.setCallback(new Drawable.Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    textView.postInvalidate();
                }

                @Override
                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                    textView.post(what);
                }

                @Override
                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                    textView.removeCallbacks(what);
                }
            });
        }
    }

    private int getEditTextCursorIndex(EditText mEditText) {
        return mEditText.getSelectionStart();
    }

    private void insertText(EditText mEditText, String mText) {
        if (mEditText == null || mText == null || mText.isEmpty()) { return; }
        int emojiSize = 100;

        CharSequence spannableText = getApngCharSequence(mEditText.getContext(), mEditText, mText, emojiSize);
        int cursorIndex = getEditTextCursorIndex(mEditText);

        mEditText.getText().insert(cursorIndex, spannableText);

        Emoji.ApngExpressionParser.startAnimators(mEditText.getText());
        mEditText.postInvalidate();
    }
}