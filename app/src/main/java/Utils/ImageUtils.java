package Utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    /**
     * 保存 Bitmap 到系统公共相册 (兼容 Android 10+)
     */
    public static void saveImageToGallery(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) {
            if (context != null) {
                FBMessage.Show(context, "图片为空，保存失败");
            }
            return;
        }

        String fileName = "QR_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Midian");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.flush();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                }
                FBMessage.Show(context, "二维码名片已成功保存到相册");
            } catch (Exception e) {
                Log.e(TAG, "保存图片到相册失败", e);
                // 发生异常时删除未完成的媒体脏数据
                context.getContentResolver().delete(uri, null, null);
                FBMessage.Show(context, "保存失败：" + e.getMessage());
            }
        } else {
            FBMessage.Show(context, "创建相册文件失败");
        }
    }

    public static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] bitmapBytes = baos.toByteArray();
            return Base64.encodeToString(bitmapBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "bitmapToBase64 失败", e);
            return null;
        }
    }

    /**
     * Base64 转为 Bitmap（兼容带协议前缀与纯 Base64 串）
     */
    public static Bitmap base64ToBitmap(String base64Img) {
        if (TextUtils.isEmpty(base64Img)) return null;
        try {
            String pureBase64 = base64Img.contains(",") ? base64Img.split(",")[1] : base64Img;
            byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } catch (Exception e) {
            Log.e(TAG, "base64ToBitmap 失败", e);
            return null;
        }
    }

    public static void saveBitmap(Bitmap bm, String path) {
        if (bm == null || TextUtils.isEmpty(path)) {
            return;
        }
        File saveFile = new File(path);
        File parentDir = saveFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileOutputStream saveImgOut = new FileOutputStream(saveFile)) {
            bm.compress(Bitmap.CompressFormat.JPEG, 80, saveImgOut);
            saveImgOut.flush();
        } catch (IOException ex) {
            Log.e(TAG, "保存图片失败: " + path, ex);
        }
    }

    /**
     * 安全加载本地图片，自动释放流句柄
     */
    public static Bitmap getLoacalBitmap(String path) {
        if (TextUtils.isEmpty(path)) return null;
        File file = new File(path);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            return BitmapFactory.decodeStream(fis);
        } catch (Exception e) {
            Log.e(TAG, "读取本地图片失败: " + path, e);
            return null;
        }
    }

    public static Bitmap drawable2Bitmap(Drawable drawable) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(drawable.getIntrinsicWidth(), 1),
                Math.max(drawable.getIntrinsicHeight(), 1),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565
        );
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Drawable bitmap2Drawable(Resources resources, Bitmap bitmap) {
        return new BitmapDrawable(resources, bitmap);
    }

    /**
     * 合成名片背景与二维码前景图
     */
    public static Bitmap combineBitmap(Bitmap background, Bitmap foreground, int top, int left, String title) {
        if (background == null) {
            return null;
        }

        Paint paint = new Paint();
        int bgWidth = background.getWidth();
        int bgHeight = background.getHeight();
        boolean hasTitle = !TextUtils.isEmpty(title);

        if (hasTitle) {
            bgHeight += 500;
        }

        Bitmap newmap = Bitmap.createBitmap(bgWidth, bgHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(background, 0, 0, paint);

        if (foreground != null) {
            canvas.drawBitmap(foreground, bgWidth - 420, 550, paint);
        }

        if (hasTitle) {
            TextPaint titlePaint = new TextPaint();
            titlePaint.setAntiAlias(true);
            titlePaint.setTextSize(50f);
            titlePaint.setColor(Color.BLACK);

            StaticLayout titleStaticLayout = new StaticLayout(
                    title + "\n来自蜜电App，一个绝对保密的情侣聊天应用",
                    titlePaint,
                    (int) (background.getWidth() * 0.6),
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false
            );
            canvas.save();
            canvas.translate(40, background.getHeight() + 30);
            titleStaticLayout.draw(canvas);
            canvas.restore();
        }

        return newmap;
    }

    /**
     * 将 JPG 文件按比例缩小到指定的宽度或高度
     */
    public static Bitmap reset_image_size(String filePath, int targetWidth, int targetHeight) {
        if (TextUtils.isEmpty(filePath)) return null;
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        int srcWidth = options.outWidth;
        int srcHeight = options.outHeight;
        if (srcWidth <= 0 || srcHeight <= 0) return null;

        int reqWidth = targetWidth;
        int reqHeight = targetHeight;

        if (targetWidth > 0 && targetHeight <= 0) {
            double ratio = (double) targetWidth / srcWidth;
            reqHeight = (int) (srcHeight * ratio);
        } else if (targetHeight > 0 && targetWidth <= 0) {
            double ratio = (double) targetHeight / srcHeight;
            reqWidth = (int) (srcWidth * ratio);
        } else if (targetWidth <= 0 && targetHeight <= 0) {
            reqWidth = srcWidth;
            reqHeight = srcHeight;
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap sampledBitmap = BitmapFactory.decodeFile(filePath, options);
        if (sampledBitmap != null) {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(sampledBitmap, reqWidth, reqHeight, true);
            if (scaledBitmap != sampledBitmap) {
                sampledBitmap.recycle();
            }
            return scaledBitmap;
        }

        return null;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}