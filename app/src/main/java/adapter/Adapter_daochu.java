package adapter;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qapp.midian.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Adapter_daochu extends RecyclerView.Adapter<Adapter_daochu.ViewHolder> {
    private List<Module_Daochu> mData;
    private OnBackupItemActionListener listener;

    // 🌟 回调接口：供宿主 Activity 响应具体的还原与删除逻辑
    public interface OnBackupItemActionListener {
        void onRestore(Module_Daochu item);
        void onDelete(Module_Daochu item, int position);
    }

    public Adapter_daochu(List<Module_Daochu> mData) {
        this.mData = mData;
    }

    public void setOnBackupItemActionListener(OnBackupItemActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.module_daochu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        Module_Daochu item = this.mData.get(position);
        holder.setData(item.getFilename());

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // 使用 View 的 Context 避免 BadTokenException
                View popupView = LayoutInflater.from(v.getContext()).inflate(R.layout.popup_backup, null);
                PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                // 还原备份文件
                popupView.findViewById(R.id.top_re).setOnClickListener(it -> {
                    popupWindow.dismiss();
                    if (listener != null) {
                        listener.onRestore(item);
                    }
                });

                // 删除备份文件
                popupView.findViewById(R.id.top_delete).setOnClickListener(it -> {
                    popupWindow.dismiss();
                    if (listener != null) {
                        listener.onDelete(item, holder.getBindingAdapterPosition());
                    }
                });

                popupWindow.showAsDropDown(v);
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.mData != null ? this.mData.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView _filename;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            _filename = itemView.findViewById(R.id.filename);
        }

        public void setData(String filename) {
            _filename.setText(filename);
        }
    }
}