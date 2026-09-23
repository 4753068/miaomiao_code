package Utils;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;
import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_media;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import socket.AutoReconnectWebSocket;

public class UploadUtils {
    public static JSONObject FromJson = new JSONObject();
    public static int MessageId;
/*
    private static Handler handler = new Handler() {
        @SuppressLint("HandlerLeak")
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                if (ChatUtils.ReadCount==0)
                {
                    FloatingX.install(MainActivity.helper).hide();
                    FloatingX.install(MainActivity.helper_message).hide();
                    FloatingX.install(MainActivity.helper_nonet).show();
                }
            }

        }
    };

 */
    public UploadUtils(int messageId)
    {
        MessageId=messageId;
    }

    public static void UploadFile(String filename,int Friend_Uid,String messageId)
    {
        Log.e("TBA","上传="+filename);
        // 原始文件RequestBody
        String extension = MimeTypeMap.getFileExtensionFromUrl(filename);
        //String IMG_TYPE=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());

        File uploadFile=new File(filename);
        String IMG_TYPE= FileUtils.getFileExtension(uploadFile);
        //String fileMd5= EncryptUtils.encryptMD5File2String(uploadFile).toLowerCase();

        RequestBody fileBody = RequestBody.create(uploadFile, MediaType.parse("*/*; charset=utf-8"));
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        // 包装为带进度的RequestBody
        ProgressRequestBody progressBody = new ProgressRequestBody(fileBody, (bytesWritten, contentLength) -> {
            // 计算进度百分比，注意处理contentLength为-1的情况
            if (contentLength != -1) {
                int progress = (int) ((100 * bytesWritten) / contentLength);
                //runOnUiThread(() -> progressBar.setProgress(progress));
                new Handler(Looper.getMainLooper()).post(() -> {
                    activity_chat.updateUploadProgress(messageId, progress,true);
                });
                Log.e("TBA","上传-->"+progress);

            }
        });

        builder.addFormDataPart("file",filename, progressBody).build();
        MultipartBody build = builder.addFormDataPart("uid", App.UID+"").addFormDataPart("touid",Friend_Uid+"").addFormDataPart("filemd5",uploadFile.getName()).addFormDataPart("messageid",messageId).addFormDataPart("filetype",IMG_TYPE).build();
        String url=App.DataServiceUrl+"?data=midian&file=message&func=uploads_for_android";
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)   // 连接超时
                .readTimeout(60, TimeUnit.SECONDS)      // 读取超时
                .writeTimeout(60, TimeUnit.SECONDS)     // 写入超时
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(build)
                .build();


        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("MSG", "上传媒体错误: " + e.getMessage());
            }

            @SuppressLint("Range")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String jsonVal=response.body().string();
                Log.e("UPLOAD",jsonVal);

                try {
                    JSONObject json = new JSONObject(jsonVal);
                    int code=json.getInt("code");
                    if (code==1)
                    {
                        String Mediaurl=json.getString("Mediaurl");
                        JSONObject jo_media = new JSONObject();
                        try {

                            byte[] enc = readEncryptedFile(filename);
                            byte[] dec = AESUtils.decrypt_byte(App.AppContext,enc);

                            String file_type=getMimeType(dec);
                            String finalFileType="IMAGE";
                            //Log.e("TBA",file_type);
                            if (file_type.equals("image/jpeg") || file_type.equals("image/png") || file_type.equals("image/bmp")){
                                finalFileType="IMAGE";
                            }
                            else if (file_type.equals("video/mp4")){
                                finalFileType="VIDEO";
                            }
                            else if (file_type.equals("application/octet-stream")){
                                finalFileType="AUDIO";
                            }
                            else if (file_type.equals("image/gif")){
                                finalFileType="GIF";
                            }

                            jo_media.put("type", finalFileType);
                            jo_media.put("path",  filename);
                            jo_media.put("url",  Mediaurl);
                            jo_media.put("duration", activity_chat.RecorderDuration);

                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        int time= (int) (System.currentTimeMillis()/1000);
                        String SendMessageId=App.UID+""+time;
                        String Message=AESUtils.encrypt(App.AppContext,"[/MEDIA]");
                        //保存到本地
                        String sql_new = "select id,MessageId from chat where MessageId='"+SendMessageId+"' order by id desc limit 1";
                        Cursor cursor_new = App.db.rawQuery(sql_new, null);
                        if (cursor_new.getCount()==0) {
                            App.db.execSQL("insert into chat(MessageId,ToUid,FromUID,Message,Inputtime,IsRead,Yingyong,Mediaurl) values ('" + SendMessageId + "'," + activity_chat.Friend_UID + "," + App.UID + ",'" + Message + "'," + time + ",0,'','"+jo_media.toString()+"')");
                        }
                        cursor_new.close();

                        JSONObject jo = new JSONObject();
                        try {

                            jo.put("type", "ChatMessage");
                            jo.put("send_uid", App.UID);
                            jo.put("send_time", System.currentTimeMillis()/1000);
                            JSONObject jo_data = new JSONObject();
                            jo_data.put("_MessageId", SendMessageId);
                            jo_data.put("_Message", Message);
                            jo_data.put("_Yingyong", "");
                            jo_data.put("_Mediaurl",AESUtils.encrypt(App.AppContext,jo_media.toString()));
                            jo_data.put("_ToUid",activity_chat.Friend_UID);
                            jo_data.put("_FromUID",App.UID);
                            jo_data.put("_Inputtime", time);
                            jo_data.put("_ID", 0);
                            jo_data.put("_IsRead", 0);
                            jo_data.put("_Progress", 0);
                            jo.put("data", jo_data);
                            AutoReconnectWebSocket.socket.sendMessage(jo.toString());
                        }
                        catch (JSONException e)
                        {
                            throw new RuntimeException(e);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });


    }

    public static void Download_IMG(String Url,String _MessagId)
    {
        String[] parts = Url.split("/");

        File file=new File(App.Folder+"/Cache/"+parts[parts.length-1]);
        if (!file.exists())
        {
            //Log.e("TBA","开始下载文件"+Url);
            // 进度回调实现
            ProgressResponseBody_Upload.ProgressListener progressListener = new ProgressResponseBody_Upload.ProgressListener() {
                @Override
                public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    //Log.e("TBA","已下载"+percent);

                        //percent=(int) ((100 * downloadedBytes) / totalBytes);



                    //Log.e("TBA","下载图片>>>"+percent+"/"+downloadedBytes+"/"+totalBytes);
                    /*
                    for (int i=0;i<ChatActivity.Datas_Chat.size();i++)
                    {
                        int _mid=ChatActivity.Datas_Chat.get(i).getID();
                        //Log.e("TBA",messageid+"/"+_messageId);
                        if (_mid==MessageId){
                            int _id=ChatActivity.Datas_Chat.get(i).getID();
                            int _ToUid=ChatActivity.Datas_Chat.get(i).getUID();
                            int _FromUid=ChatActivity.Datas_Chat.get(i).getFromUID();
                            String _Inputtime=ChatActivity.Datas_Chat.get(i).getTime();
                            String _Yingyong=ChatActivity.Datas_Chat.get(i).getYingyong();
                            String _message=ChatActivity.Datas_Chat.get(i).getMessage();
                            int _IsRead=ChatActivity.Datas_Chat.get(i).get_IsRead();
                            String _messageId=ChatActivity.Datas_Chat.get(i).getMessageId();
                            ChatActivity.Datas_Chat.set(i,new Datas_Chat(_id,"正在下载 "+percent+"%",_messageId,_ToUid,_FromUid,"",_Inputtime+"",_Yingyong,"",_IsRead,percent));

                            int finalI = i;
                            int finalPercent = percent;
                            ChatActivity.that.runOnUiThread(new Runnable(){
                                @Override
                                public void run() {
                                    Bundle payload = new Bundle();
                                    payload.putInt("_Progresssa", finalPercent);
                                    ChatActivity.mAdapter_Chat.notifyItemChanged(finalI,payload);
                                }
                            });
                        }
                    }

                     */
                }
                private String formatSize(long bytes) {
                    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
                }
            };

            UploadUtils.downloadImage(Url, App.Folder+"/Cache/"+parts[parts.length-1],progressListener, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        UploadUtils.saveToFile(response.body().byteStream(),parts[parts.length-1]);

                        if (!parts[parts.length-1].contains(".mp3"))
                        {
                            if (activity_media.that!=null)
                            {
                                //activity_media.Mediaurl=App.Folder+"/Cache/"+parts[parts.length-1];
                                //activity_media.getMedia();
                            }
                            /*
                            Intent it = new Intent();
                            it.setClass(that, ShowMediaActivity.class);
                            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Bundle bundle_chat = new Bundle();
                            bundle_chat.putString("Mediaurl",App.Folder+"/Cache/"+parts[parts.length-1]);
                            it.putExtras(bundle_chat);
                            App.AppContext.startActivity(it);

                             */
                        }

                    }
                }
            });

        }
    }
    // 异步下载（推荐）
    public static void downloadImage(String url, String fileName,
                                     ProgressResponseBody_Upload.ProgressListener listener, Callback callback) {

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept-Encoding","identity")
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody_Upload(originalResponse.body(), listener))
                            .build();
                })
                .build();
        client.newCall(request).enqueue(callback);
    }

    // 同步下载（需在子线程执行）
    public static void downloadImageSync(String url, String fileName) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                saveToFile(response.body().byteStream(), fileName);
            }
        }
    }

    public static void saveToFile(InputStream inputStream, String fileName) throws IOException {
        // 获取下载目录（可根据需求修改路径）

        File file = new File(App.Folder+"/Cache", fileName);
        try (OutputStream outputStream = new FileOutputStream(file);
             BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
            }
        }
    }

    public static String getMimeType(byte[] data) {
        if (data == null || data.length < 4) return "application/octet-stream";

        // 检查 JPEG: FF D8 FF
        if (data.length >= 3 &&
                (data[0] & 0xFF) == 0xFF &&
                (data[1] & 0xFF) == 0xD8 &&
                (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // 检查 PNG: 89 50 4E 47
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x89 &&
                (data[1] & 0xFF) == 0x50 &&
                (data[2] & 0xFF) == 0x4E &&
                (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // 检查 GIF: 47 49 46 38 -> "GIF8"
        if (data.length >= 4 &&
                (data[0] & 0xFF) == 0x47 &&
                (data[1] & 0xFF) == 0x49 &&
                (data[2] & 0xFF) == 0x46 &&
                (data[3] & 0xFF) == 0x38) {
            return "image/gif";
        }

        // 检查 WEBP: RIFF + WEBP
        if (data.length >= 12 &&
                (data[0] & 0xFF) == 0x52 && // 'R'
                (data[1] & 0xFF) == 0x49 && // 'I'
                (data[2] & 0xFF) == 0x46 && // 'F'
                (data[3] & 0xFF) == 0x46 && // 'F'
                (data[8] & 0xFF) == 0x57 && // 'W'
                (data[9] & 0xFF) == 0x45 && // 'E'
                (data[10] & 0xFF) == 0x42 && // 'B'
                (data[11] & 0xFF) == 0x50) { // 'P'
            return "image/webp";
        }

        // 检查 MP4: 通常以 ftyp 开头（位置可能在偏移 4～8）
        // 格式: [size][ftype][...]
        if (data.length >= 12) {
            for (int i = 0; i <= 8; i++) {
                if (i + 11 < data.length &&
                        (data[i + 4] & 0xFF) == 0x66 && // 'f'
                        (data[i + 5] & 0xFF) == 0x74 && // 't'
                        (data[i + 6] & 0xFF) == 0x79 && // 'y'
                        (data[i + 7] & 0xFF) == 0x70) { // 'p'
                    return "video/mp4";
                }
            }
        }

        // 可继续添加其他类型：AVI, MKV, PDF 等

        return "application/octet-stream"; // 未知类型
    }

    public static byte[] readEncryptedFile(String filePath) throws IOException {
        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }
        fis.close();
        return bos.toByteArray();
    }

}
