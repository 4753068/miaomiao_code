package Utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.qapp.midian.App;
import com.qapp.midian.R;

public class QRCodeUtils {
    private static final String TAG = "QRCodeUtils";

    public static void showQrCodeDialog(Activity activity, String qrContent, String name) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || TextUtils.isEmpty(qrContent)) {
            return;
        }

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_group_qrcode);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDialogGroupName = dialog.findViewById(R.id.tvDialogGroupName);
        ImageView ivDialogQrCode = dialog.findViewById(R.id.ivDialogQrCode);
        TextView tvSaveQrCode = dialog.findViewById(R.id.tvSaveQrCode);
        Button btnDialogClose = dialog.findViewById(R.id.btnDialogClose);

        if (tvDialogGroupName != null) {
            tvDialogGroupName.setText(name);
        }

        final Bitmap[] currentMergedBitmap = new Bitmap[1];

        // 高容错率 (ecc=H) 800x800 二维码
        String qrUrl = "https://api.qrcode-monkey.com/qr/custom?size=800&ecc=H&data=" + Uri.encode(qrContent);

        Glide.with(activity)
                .asBitmap()
                .load(qrUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;

                        Bitmap merged = combineQrWithLogo(resource);
                        currentMergedBitmap[0] = merged;
                        if (ivDialogQrCode != null && merged != null) {
                            ivDialogQrCode.setImageBitmap(merged);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });

        if (tvSaveQrCode != null) {
            tvSaveQrCode.setOnClickListener(v -> {
                if (currentMergedBitmap[0] != null) {
                    ImageUtils.saveImageToGallery(activity, currentMergedBitmap[0]);
                } else {
                    FBMessage.Show(activity, "二维码尚未加载完成");
                }
            });
        }

        if (btnDialogClose != null) {
            btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        }

        // 关闭对话框时主动回收临时位图，防止内存泄漏
        dialog.setOnDismissListener(d -> {
            if (currentMergedBitmap[0] != null && !currentMergedBitmap[0].isRecycled()) {
                currentMergedBitmap[0].recycle();
                currentMergedBitmap[0] = null;
            }
        });

        dialog.show();
    }

    /**
     * 将网络二维码与本地 Logo 合并为一张 Bitmap
     */
    public static Bitmap combineQrWithLogo(Bitmap qrBitmap) {
        if (qrBitmap == null) return null;

        Bitmap logoBitmap = BitmapFactory.decodeResource(App.AppContext.getResources(), R.drawable.logo);
        if (logoBitmap == null) {
            return qrBitmap;
        }

        int qrWidth = qrBitmap.getWidth();
        int qrHeight = qrBitmap.getHeight();

        // 控制 Logo 大小约为二维码的 1/5.5，落在 H 级容错安全区内
        int logoSize = (int) (qrWidth / 5.5f);
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true);
        if (scaledLogo != logoBitmap) {
            logoBitmap.recycle(); // 释放原图
        }

        Bitmap finalBitmap = Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);

        // 1. 画底层二维码
        canvas.drawBitmap(qrBitmap, 0, 0, null);

        // 2. 绘制圆角白色底衬，防止 Logo 与黑块粘连
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        float left = (qrWidth - logoSize) / 2f;
        float top = (qrHeight - logoSize) / 2f;
        float padding = 12f;

        RectF rectF = new RectF(left - padding, top - padding, left + logoSize + padding, top + logoSize + padding);
        canvas.drawRoundRect(rectF, 16f, 16f, paint);

        // 3. 绘制居中 Logo
        canvas.drawBitmap(scaledLogo, left, top, null);
        scaledLogo.recycle();

        return finalBitmap;
    }
}