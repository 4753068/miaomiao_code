package socket;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.qapp.commercial_auth.AESUtils;
import com.qapp.midian.App;
import com.qapp.midian.activity_chat;
import com.qapp.midian.activity_friend;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import Data.ChatUtils;
import Data.FriendUtils;
import Data.MemberBillingBridge;
import Data.MessageUtils;
import Utils.FBMessage;
import Utils.SPUtils;
import adapter.Module_Chat;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class AutoReconnectWebSocket {

    public static volatile AutoReconnectWebSocket instance;
    public static final int MAX_RETRIES = 5;
    private static final int CHUNK_SIZE = 60 * 1024;
    private static final int HEADER_SIZE = 1024;

    private OkHttpClient client;
    private Request request;
    private WebSocket webSocket;
    public static boolean isManualClose = false;
    private int retryCount = 0;
    private long lastActiveTime = System.currentTimeMillis() - 60_000;

    private String originalUrl;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final ConcurrentHashMap<String, Long> pendingMessages = new ConcurrentHashMap<>();

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final ExecutorService uploadExecutor = Executors.newCachedThreadPool();

    private static final Map<String, ScheduledFuture<?>> serverUploadTasks = new ConcurrentHashMap<>();
    private static final Map<String, Integer> messageRetryCounts = new ConcurrentHashMap<>();
    private static final Map<String, ScheduledFuture<?>> offlineFallbackTasks = new ConcurrentHashMap<>();

    public static final Map<String, String> uploadMessageIdMap = new ConcurrentHashMap<>();

    public static final AtomicBoolean socketConnected = new AtomicBoolean(false);
    public static String SocketUrl = null;
    private static String TAG = "Socket";
    public static AutoReconnectWebSocket socket = null;

    private ScheduledFuture<?> heartbeatFuture = null;

    public static boolean isGroupChat = false;
    public static String currentGroupId = "";

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - lastActiveTime > 35_000) {
                Log.e(TAG, "心跳超时，触发重连");
                socketConnected.set(false);
                if (!isManualClose && !socketConnected.get()) connect();
            } else {
                try {
                    if (webSocket != null) {
                        LocalDateTime nowTime = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String formatted = nowTime.format(formatter);
                        JSONObject json = new JSONObject();
                        json.put("type", "pong");
                        json.put("uid", App.UID);
                        json.put("time", formatted);
                        sendRawMessage(json.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "发送心跳失败", e);
                }
            }
            heartbeatFuture = scheduler.schedule(this, 30_000, TimeUnit.MILLISECONDS);
        }
    };

    private AutoReconnectWebSocket(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("WebSocket URL cannot be null or empty");
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            throw new IllegalArgumentException("WebSocket URL must start with ws:// or wss://");
        }
        this.originalUrl = url;
        initClient();
    }

    private void initClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build();
        request = new Request.Builder().url(originalUrl).build();
    }

    public static void startSocket() {
        App.UID = SPUtils.getInstance().get("uid", 0);
        if (App.UID > 0) {
            AutoReconnectWebSocket.isManualClose = false;
            if (AutoReconnectWebSocket.instance == null) {
                String rawUrl = SPUtils.getInstance().get("SocketUrl", "socket.am930.cn");
                String rawPort = SPUtils.getInstance().get("SocketPort", "8083");

                rawUrl = rawUrl.replace("wss://", "").replace("ws://", "");
                if (rawUrl.contains(":")) {
                    rawUrl = rawUrl.substring(0, rawUrl.indexOf(":"));
                }

                SocketUrl = "wss://" + rawUrl + ":" + rawPort;
                AutoReconnectWebSocket.switchUrl(SocketUrl);
            }
            if (!socketConnected.get()) {
                AutoReconnectWebSocket.instance.connect();
            }
        }
    }

    public static void switchUrl(String newUrl) {
        if (newUrl == null || newUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("New WebSocket URL cannot be null or empty");
        }
        if (!newUrl.startsWith("ws://") && !newUrl.startsWith("wss://")) {
            throw new IllegalArgumentException("New WebSocket URL must start with ws:// or wss://");
        }

        // 彻底清空主线程排队的旧重连任务，防止旧地址死灰复燃
        mainHandler.removeCallbacksAndMessages(null);

        if (instance != null) {
            instance.disconnect();
            instance.destroy();
            instance = null;
        }

        isManualClose = false;
        instance = new AutoReconnectWebSocket(newUrl);
        instance.connecting.set(false);
        socket = instance;
        Log.e("SOCKET", "已切换并准备连接新节点：" + newUrl);
    }

    public void connect() {
        if (isManualClose) {
            Log.w("WebSocket", "手动关闭中，跳过连接");
            return;
        }
        lastActiveTime = System.currentTimeMillis() - 60_000;
        if (connecting.get()) {
            Log.w("WebSocket", "已有连接正在进行，跳过本次 connect");
            return;
        }
        if (webSocket != null) {
            webSocket.close(1000, "Replacing connection for reconnect");
            webSocket = null;
        }
        cancelPendingReconnect();
        connecting.set(true);

        mainHandler.post(() -> {
            try {
                Log.e("WebSocket", "正在创建 WebSocket 连接: " + originalUrl);
                webSocket = client.newWebSocket(request, new AutoReconnectListener());
            } catch (Exception e) {
                Log.e(TAG, "创建 WebSocket 实例失败", e);
                connecting.set(false);
                scheduleReconnect();
            }
        });
    }

    public void disconnect() {
        isManualClose = true;
        cancelPendingReconnect();
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "User closed");
            webSocket = null;
        }
        socketConnected.set(false);
        connecting.set(false);
    }

    public void destroy() {
        disconnect();
        if (client != null) {
            try {
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            } catch (Exception ignored) {}
        }
        // 全局静态线程池 scheduler 和 uploadExecutor 保持常驻，不执行关闭
    }

    public void sendRawMessage(String data) {
        if (webSocket != null && socketConnected.get()) {
            try { webSocket.send(data); } catch (Exception e) { Log.e(TAG, "sendRawMessage 发生异常", e); }
        }
    }

    public void sendMessage(String data) {
        try {
            JSONObject sendJson = new JSONObject(data);
            String type = sendJson.optString("type");

            if ("SendSuccess".equals(type) || "pong".equals(type)) {
                sendRawMessage(data);
                return;
            }

            if ("Update_Friend_Profile".equals(type) || "Group_Disbanded".equals(type)) {
                if (webSocket != null && socketConnected.get()) {
                    webSocket.send(data);
                }
                return;
            }

            if ("Chehui".equals(type) && isGroupChat && !TextUtils.isEmpty(currentGroupId)) {
                JSONObject joData = sendJson.optJSONObject("data");
                if (joData != null) {
                    joData.put("_GroupId", currentGroupId);
                    joData.put("_ToUid", 0);
                    data = sendJson.toString();
                }
            }

            if (!"ChatMessage".equals(type) && !"GroupMessage".equals(type)) {
                if (webSocket != null && socketConnected.get()) webSocket.send(data);
                return;
            }

            if (!sendJson.has("data")) {
                if (webSocket != null) webSocket.send(data);
                return;
            }

            JSONObject json = new JSONObject(sendJson.getString("data"));
            String messageId = json.getString("_MessageId");

            if (webSocket != null && socketConnected.get()) {
                webSocket.send(data);
            }

            if (!pendingMessages.containsKey(messageId)) {
                pendingMessages.put(messageId, System.currentTimeMillis());
                messageRetryCounts.put(messageId, 1);
            }
            executeMessageRetryChain(data, messageId, sendJson, json);

        } catch (JSONException e) {
            Log.e(TAG, "sendMessage 解析 JSON 失败", e);
        }
    }

    private void executeMessageRetryChain(final String originalData, final String messageId, final JSONObject sendJson, final JSONObject json) {
        if (uploadMessageIdMap.containsValue(messageId)) {
            return;
        }

        ScheduledFuture<?> retryTask = scheduler.schedule(() -> {
            if (!pendingMessages.containsKey(messageId)) return;

            Integer currentRetries = messageRetryCounts.get(messageId);
            if (currentRetries == null) currentRetries = 1;

            if (currentRetries < 10) {
                int nextRetryCount = currentRetries + 1;
                messageRetryCounts.put(messageId, nextRetryCount);

                if (webSocket == null || !socketConnected.get()) {
                    Log.w(TAG, "⏳ 消息 [" + messageId + "] 检测到当前无网络，等待网络恢复重连... (" + nextRetryCount + "/10)");
                } else {
                    webSocket.send(originalData);
                }
                executeMessageRetryChain(originalData, messageId, sendJson, json);
            } else {
                pendingMessages.remove(messageId);
                messageRetryCounts.remove(messageId);
                serverUploadTasks.remove(messageId);

                startOfflineFallbackProcess(messageId, sendJson);
            }
        }, 6, TimeUnit.SECONDS);

        serverUploadTasks.put(messageId, retryTask);
    }

    private void startOfflineFallbackProcess(final String messageId, final JSONObject sendJson) {
        final AtomicBoolean isHandled = new AtomicBoolean(false);

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (isHandled.compareAndSet(false, true)) {
                offlineFallbackTasks.remove(messageId);
                handleSendMessageTimeout(sendJson, 7);
            }
        }, 30, TimeUnit.SECONDS);

        offlineFallbackTasks.put(messageId, timeoutTask);

        MessageUtils.SendOffLineMessage(sendJson, new MessageUtils.OnOfflineBackupCallback() {
            @Override
            public void onSuccess() {
                if (isHandled.compareAndSet(false, true)) {
                    ScheduledFuture<?> task = offlineFallbackTasks.remove(messageId);
                    if (task != null) task.cancel(false);
                    handleSendMessageTimeout(sendJson, 6);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (isHandled.compareAndSet(false, true)) {
                    ScheduledFuture<?> task = offlineFallbackTasks.remove(messageId);
                    if (task != null) task.cancel(false);
                    handleSendMessageTimeout(sendJson, 7);
                }
            }
        });
    }

    private void scheduleReconnect() {
        socketConnected.set(false);

        if (retryCount >= MAX_RETRIES) {
            retryCount = 0;
            findAvailableNodeAndSwitch();
            return;
        }

        long delay = (retryCount < 10) ? 3_000 : (retryCount < 20) ? 6_000 : 10_000;
        retryCount++;
        mainHandler.postDelayed(() -> {
            if (!socketConnected.get() && !isManualClose) {
                connect();
            }
        }, delay);
    }

    private void findAvailableNodeAndSwitch() {
        java.util.List<ServerNode> serverNodes = new java.util.ArrayList<>();
        Cursor cursor = null;
        try {
            String currentPure = originalUrl.replace("wss://", "").replace("ws://", "");
            if (currentPure.contains(":")) {
                currentPure = currentPure.substring(0, currentPure.indexOf(":"));
            }

            cursor = App.db.rawQuery("SELECT socket_url, server_port, server_name FROM server", null);
            while (cursor != null && cursor.moveToNext()) {
                String rawUrl = cursor.getString(0);
                int port = cursor.getInt(1);
                String serverName = cursor.getString(2);

                if (rawUrl != null && !rawUrl.isEmpty()) {
                    String pureUrl = rawUrl.replace("wss://", "").replace("ws://", "");
                    if (pureUrl.contains(":")) {
                        pureUrl = pureUrl.substring(0, pureUrl.indexOf(":"));
                    }

                    if (!pureUrl.equals(currentPure)) {
                        String wssUrl = "wss://" + pureUrl + ":" + port;
                        serverNodes.add(new ServerNode(pureUrl, port, wssUrl, serverName));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取备用节点失败", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        if (serverNodes.isEmpty()) {
            scheduleReconnect();
            return;
        }

        OkHttpClient pingClient = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();

        final AtomicBoolean hasSwitched = new AtomicBoolean(false);

        for (final ServerNode node : serverNodes) {
            Request request = new Request.Builder().url(node.wssUrl).build();
            pingClient.newWebSocket(request, new WebSocketListener() {
                private boolean handled = false;

                @Override
                public void onOpen(WebSocket webSocket, Response response) {}

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    if (!handled && text.contains("\"welcome\"")) {
                        handled = true;
                        webSocket.close(1000, "Ping Success");

                        if (hasSwitched.compareAndSet(false, true)) {
                            SPUtils.getInstance().put("SocketName", node.serverName);
                            SPUtils.getInstance().put("SocketUrl", "wss://" + node.pureUrl);
                            SPUtils.getInstance().put("SocketPort", node.port + "");

                            if (App.UID > 0) {
                                new Thread(() -> {
                                    try {
                                        AutoReconnectWebSocket.SocketUrl = SPUtils.getInstance().get("SocketUrl", "wss://socket.am930.cn") + ":" + SPUtils.getInstance().get("SocketPort", "8083");
                                        AutoReconnectWebSocket.switchUrl(AutoReconnectWebSocket.SocketUrl);
                                        if (AutoReconnectWebSocket.instance != null) {
                                            AutoReconnectWebSocket.instance.connect();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "后台切换 WebSocket 异常", e);
                                    }
                                }).start();
                            }
                        }
                    } else if (!handled && text.contains("\"error\"")) {
                        handled = true;
                        webSocket.close(1000, "Node also full");
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    handled = true;
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    handled = true;
                }
            });
        }

        mainHandler.postDelayed(() -> {
            try {
                pingClient.dispatcher().executorService().shutdown();
            } catch (Exception ignored) {}

            if (!hasSwitched.get() && !socketConnected.get()) {
                scheduleReconnect();
            }
        }, 5000);
    }

    private static class ServerNode {
        String pureUrl;
        int port;
        String wssUrl;
        String serverName;

        ServerNode(String pureUrl, int port, String wssUrl, String serverName) {
            this.pureUrl = pureUrl;
            this.port = port;
            this.wssUrl = wssUrl;
            this.serverName = serverName;
        }
    }

    private void cancelPendingReconnect() {
        mainHandler.removeCallbacksAndMessages(null);
    }

    public void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = scheduler.schedule(heartbeatRunnable, 30_000, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private static void handleSendMessageTimeout(JSONObject json, final int newStatus) {
        mainHandler.post(() -> {
            try {
                JSONObject json_data = new JSONObject(json.getString("data"));
                String messageId = json_data.getString("_MessageId");

                App.db.execSQL("UPDATE chat SET IsRead = ? WHERE MessageId = ?", new Object[]{newStatus, messageId});

                if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed()) {
                    for (int i = 0; i < activity_chat.Datas_Chat.size(); i++) {
                        Module_Chat item = activity_chat.Datas_Chat.get(i);
                        if (messageId.equals(item.getMessageId())) {
                            activity_chat.Datas_Chat.set(i, new Module_Chat(
                                    item.getID(), item.getMessage(), item.getMessageId(),
                                    item.getUID(), item.getFromUID(), item.getThumb(), item.getTime(),
                                    item.getYingyong(), item.getMediaurl(), newStatus, 0
                            ));

                            activity_chat.mAdapter_Chat.notifyItemChanged(i);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "处理消息超时本地更新失败", e);
            }
        });
    }

    private class AutoReconnectListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            AutoReconnectWebSocket.this.webSocket = webSocket;
            retryCount = 0;
            lastActiveTime = System.currentTimeMillis();
            socketConnected.set(true);
            connecting.set(false);

            mainHandler.post(() -> {
                String servername = SPUtils.getInstance().get("SocketName", "UN");
                Log.e(TAG, "✅ 成功连接到 " + servername);
                socket = instance;
            });

            try {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formatted = now.format(formatter);
                JSONObject json = new JSONObject();
                json.put("type", "login");
                json.put("uid", App.UID);
                json.put("time", formatted);
                sendRawMessage(json.toString());
            } catch (JSONException e) {
                Log.e(TAG, "发送登录包失败", e);
            }
            startHeartbeat();
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (code == 1013) {
                retryCount = MAX_RETRIES;
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            AutoReconnectWebSocket.this.webSocket = null;
            stopHeartbeat();
            socketConnected.set(false);
            connecting.set(false);

            // 保护拦截：如果当前触发 closed 的已经不是最新的 instance，绝不重连
            if (AutoReconnectWebSocket.this != instance) {
                return;
            }

            if (!isManualClose) {
                if (code == 1013) retryCount = MAX_RETRIES;
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            AutoReconnectWebSocket.this.webSocket = null;
            socketConnected.set(false);
            connecting.set(false);

            // 保护拦截：旧实例抛错不准给新实例安排重连
            if (AutoReconnectWebSocket.this != instance) {
                return;
            }

            if (!socketConnected.get() && !isManualClose) {
                scheduleReconnect();
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            lastActiveTime = System.currentTimeMillis();
            try {
                JSONObject json = new JSONObject(text);
                String type = json.getString("type");
                Log.e("SOCKET", text);

                if ("welcome".equals(type) || "ping".equals(type)) {
                    MessageUtils.DeleteForTime();
                    LocalDateTime nowTime = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formatted = nowTime.format(formatter);

                    JSONObject json_pong = new JSONObject();
                    json_pong.put("type", "pong");
                    json_pong.put("uid", App.UID);
                    json_pong.put("time", formatted);
                    sendRawMessage(json_pong.toString());
                    socketConnected.set(true);
                }
                else if ("error".equals(type)) {
                    String errorMsg = json.optString("msg", "未知错误");
                    if (errorMsg.contains("满") || errorMsg.contains("full")) {
                        retryCount = MAX_RETRIES;
                    }
                }
                else if ("UPLOAD_COMPLETE".equals(type)) {
                    JSONObject jo = new JSONObject();
                    String Mediaurl = json.getString("url");
                    String[] parts = Mediaurl.split("/");
                    String path = parts[parts.length - 1];

                    File cacheFile = new File(App.Folder + "/Cache/", path);
                    long fileSizeInBytes = 0;
                    if (cacheFile.exists()) {
                        fileSizeInBytes = cacheFile.length();
                    }

                    String originMessageId = uploadMessageIdMap.remove(path);

                    if (originMessageId != null && originMessageId.startsWith("THUMB_")) {
                        String realMessageId = originMessageId.replace("THUMB_", "");
                        try (Cursor cursor = App.db.rawQuery("SELECT Mediaurl FROM chat WHERE MessageId=? ORDER BY id DESC LIMIT 1", new String[]{realMessageId})) {
                            if (cursor.getCount() > 0) {
                                cursor.moveToFirst();
                                String existingMediaUrl = cursor.getString(0);
                                if (existingMediaUrl != null && !existingMediaUrl.isEmpty()) {
                                    JSONObject dbMediaJson = new JSONObject(existingMediaUrl);
                                    dbMediaJson.put("thumb", Mediaurl);

                                    App.db.execSQL("UPDATE chat SET Mediaurl = ? WHERE MessageId = ?", new Object[]{dbMediaJson.toString(), realMessageId});
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "更新缩略图URL失败", e);
                        }
                        return;
                    }

                    String SendMessageId = (originMessageId != null) ? originMessageId : (App.UID + "" + System.currentTimeMillis());

                    String updatedMediaUrl = "";
                    try (Cursor cursor = App.db.rawQuery("SELECT Mediaurl FROM chat WHERE MessageId=? ORDER BY id DESC LIMIT 1", new String[]{SendMessageId})) {
                        if (cursor.getCount() > 0) {
                            cursor.moveToFirst();
                            String existingMediaUrl = cursor.getString(0);
                            if (existingMediaUrl != null && !existingMediaUrl.isEmpty()) {
                                JSONObject dbMediaJson = new JSONObject(existingMediaUrl);
                                dbMediaJson.put("url", Mediaurl);
                                updatedMediaUrl = dbMediaJson.toString();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "查询媒体地址失败", e);
                    }

                    if (updatedMediaUrl.isEmpty()) {
                        JSONObject fallback = new JSONObject();
                        fallback.put("type", "IMAGE");
                        fallback.put("path", "Cache/" + path);
                        fallback.put("url", Mediaurl);
                        fallback.put("duration", activity_chat.RecorderDuration);
                        fallback.put("filesize", fileSizeInBytes);
                        updatedMediaUrl = fallback.toString();
                    }

                    try {
                        String Message = AESUtils.encrypt(App.AppContext, "[/MEDIA]");

                        if (AutoReconnectWebSocket.isGroupChat) {
                            jo.put("type", "GroupMessage");
                            jo.put("send_uid", App.UID);
                            jo.put("send_time", System.currentTimeMillis() / 1000);

                            JSONObject jo_data = new JSONObject();
                            jo_data.put("_MessageId", SendMessageId);
                            jo_data.put("_Message", Message);
                            jo_data.put("_Yingyong", "");
                            jo_data.put("_Mediaurl", AESUtils.encrypt(App.AppContext, updatedMediaUrl));
                            jo_data.put("_GroupId", AutoReconnectWebSocket.currentGroupId);
                            jo_data.put("_ToUid", 0);
                            jo_data.put("_FromUID", App.UID);
                            jo_data.put("_Inputtime", System.currentTimeMillis());
                            jo_data.put("_ID", 0);
                            jo_data.put("_IsRead", 0);
                            jo_data.put("_Progress", 0);
                            jo.put("data", jo_data);
                        } else {
                            jo.put("type", "ChatMessage");
                            jo.put("send_uid", App.UID);
                            jo.put("send_time", System.currentTimeMillis() / 1000);

                            JSONObject jo_data = new JSONObject();
                            jo_data.put("_MessageId", SendMessageId);
                            jo_data.put("_Message", Message);
                            jo_data.put("_Yingyong", "");
                            jo_data.put("_Mediaurl", AESUtils.encrypt(App.AppContext, updatedMediaUrl));
                            jo_data.put("_ToUid", activity_chat.Friend_UID);
                            jo_data.put("_FromUID", App.UID);
                            jo_data.put("_Inputtime", System.currentTimeMillis());
                            jo_data.put("_ID", 0);
                            jo_data.put("_IsRead", 0);
                            jo_data.put("_Progress", 0);
                            jo.put("data", jo_data);
                        }

                        sendMessage(jo.toString());

                        App.db.execSQL("UPDATE chat SET Mediaurl = ? WHERE MessageId = ?", new Object[]{updatedMediaUrl, SendMessageId});

                        if (originMessageId != null) {
                            activity_chat.updateUploadProgress(originMessageId, 100, true);
                            mainHandler.postDelayed(() -> activity_chat.updateUploadProgress(originMessageId, 100, false), 350);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "UPLOAD_COMPLETE 内部组包异常", e);
                    }
                }
                else {
                    if (!json.has("data")) return;
                    JSONObject data = json.getJSONObject("data");

                    if ("ChatMessage".equals(type) || "SelfMessage".equals(type)) {
                        MessageUtils.ChatMessage(data);
                        JSONObject jo = new JSONObject();
                        jo.put("type", "SendSuccess");
                        jo.put("send_uid", App.UID);
                        jo.put("send_time", System.currentTimeMillis() / 1000);
                        JSONObject jo_data = new JSONObject();
                        jo_data.put("_MessageId", data.getString("_MessageId"));
                        jo_data.put("_ToUid", data.getString("_FromUID"));
                        jo_data.put("_FromUID", App.UID);
                        jo.put("data", jo_data);
                        sendRawMessage(jo.toString());
                    }
                    else if ("GroupMessage".equals(type)) {
                        int fromUid = data.optInt("_FromUID");
                        if (fromUid == App.UID) return;
                        MessageUtils.GroupMessage(data);
                    }
                    else if ("SyncMessage".equals(type)) {
                        JSONObject data_Sync = json.getJSONObject("data");
                        MessageUtils.ChatMessage(data_Sync);
                    }
                    else if ("SendSuccess".equals(type) || "GroupMsgAck".equals(type)) {
                        String messageId = data.getString("_MessageId");

                        try {
                            App.db.execSQL("UPDATE chat SET IsRead = 3 WHERE MessageId = ?", new Object[]{messageId});
                        } catch (Exception e) {
                            Log.e(TAG, "更新本地数据库消息状态失败", e);
                        }

                        if ("SendSuccess".equals(type)) {
                            ChatUtils.SendSuccess(data);
                        }

                        pendingMessages.remove(messageId);
                        messageRetryCounts.remove(messageId);

                        ScheduledFuture<?> uploadTask = serverUploadTasks.remove(messageId);
                        if (uploadTask != null && !uploadTask.isDone()) {
                            uploadTask.cancel(false);
                        }

                        ScheduledFuture<?> fallbackTask = offlineFallbackTasks.remove(messageId);
                        if (fallbackTask != null && !fallbackTask.isDone()) {
                            fallbackTask.cancel(false);
                        }

                        if (activity_chat.that != null && !activity_chat.that.isFinishing()) {
                            activity_chat.that.runOnUiThread(() -> {
                                if (activity_chat.Datas_Chat != null && activity_chat.mAdapter_Chat != null) {
                                    for (int i = 0; i < activity_chat.Datas_Chat.size(); i++) {
                                        Module_Chat item = activity_chat.Datas_Chat.get(i);
                                        if (messageId.equals(item.getMessageId())) {
                                            final int finalIndex = i;
                                            activity_chat.Datas_Chat.set(finalIndex, new Module_Chat(
                                                    item.getID(), item.getMessage(), messageId, item.getUID(), item.getFromUID(),
                                                    item.getThumb(), item.getTime(), item.getYingyong(), item.getMediaurl(), 3, 0
                                            ));

                                            Bundle payload = new Bundle();
                                            payload.putInt("_IsRead", 3);
                                            activity_chat.mAdapter_Chat.notifyItemChanged(finalIndex, payload);
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    }
                    else if ("Chehui".equals(type)) {
                        String MessageId = data.getString("_MessageId");
                        try (Cursor cursor = App.db.rawQuery("SELECT * FROM chat WHERE MessageId=? ORDER BY id DESC LIMIT 1", new String[]{MessageId})) {
                            if (cursor.getCount() > 0) {
                                App.db.execSQL("UPDATE chat SET IsRead=5, Message='消息已被撤回' WHERE MessageId=?", new Object[]{MessageId});
                                if (activity_chat.that != null) {
                                    for (int i = 0; i < activity_chat.Datas_Chat.size(); i++) {
                                        if (MessageId.equals(activity_chat.Datas_Chat.get(i).getMessageId())) {
                                            Module_Chat old = activity_chat.Datas_Chat.get(i);
                                            activity_chat.Datas_Chat.set(i, new Module_Chat(
                                                    old.getID(), "消息已被撤回", MessageId, old.getUID(), old.getFromUID(), old.getThumb(), old.getTime(), old.getYingyong(), old.getMediaurl(), 5, 0
                                            ));
                                            activity_chat.that.runOnUiThread(activity_chat.mAdapter_Chat::notifyDataSetChanged);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if ("NewFriend".equals(type)) {
                        if (data.has("action")) {
                            String action = data.optString("action");
                            String targetGroupId = data.optString("group_id");

                            if ("remove_group".equals(action) && !TextUtils.isEmpty(targetGroupId)) {
                                App.db.execSQL("DELETE FROM friend WHERE UserID = ? AND Type = 30", new Object[]{targetGroupId});

                                if (activity_chat.that != null && !activity_chat.that.isFinishing() && targetGroupId.equals(activity_chat.Friend_UserID)) {
                                    activity_chat.that.runOnUiThread(() -> {
                                        FBMessage.Show(activity_chat.that, "您已被移出该群");
                                        activity_chat.that.finish();
                                    });
                                }

                                activity_friend.forceRefreshList();
                                return;
                            }
                            else if ("invite_group".equals(action)) {
                                if (Data.MemberBillingBridge.get() != null) {
                                    Data.MemberBillingBridge.get().syncFriends(activity_friend.that, App.UID, App.db, App.Folder, () -> {
                                        activity_friend.forceRefreshList();
                                    });
                                }
                                return;
                            }
                        }

                        FriendUtils.GET_FRIEND();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> activity_friend.forceRefreshList(), 1000);
                    }
                    else if ("Update_Friend_Profile".equals(type)) {
                        try {
                            int friendUid = data.has("uid") ? data.optInt("uid") : data.optInt("_FromUID");
                            String newNickname = data.optString("nickname");
                            String newAvatar = data.optString("avatar");

                            if (friendUid > 0) {
                                try {
                                    Cursor c = App.db.rawQuery("SELECT Image FROM friend WHERE Friend_UID = ?", new String[]{String.valueOf(friendUid)});
                                    if (c != null && c.moveToFirst()) {
                                        String oldImage = c.getString(0);
                                        if (oldImage != null && !oldImage.isEmpty() && !oldImage.equals("null")) {
                                            File oldFile = new File(oldImage.startsWith("/") ? oldImage : App.Folder + "/Image/" + oldImage);
                                            if (oldFile.exists()) {
                                                oldFile.delete();
                                            }
                                        }
                                    }
                                    if (c != null) c.close();
                                } catch (Exception ignored) {}

                                String sql = "UPDATE friend SET Nickname = ?, _Nickname = CASE WHEN Nickname = _Nickname THEN ? ELSE _Nickname END, Image = ?, ImageUrl = ? WHERE Friend_UID = ?";
                                App.db.execSQL(sql, new Object[]{newNickname, newNickname, newAvatar, newAvatar, friendUid});

                                if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                    FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                                }

                                if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed() && activity_chat.Friend_UID == friendUid) {
                                    activity_chat.that.runOnUiThread(() -> {
                                        if (activity_chat.mAdapter_Chat != null) {
                                            activity_chat.mAdapter_Chat.notifyDataSetChanged();
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "静默更新好友资料失败", e);
                        }
                    }
                    else if ("Update_Group_Profile".equals(type)) {
                        try {
                            String groupId = data.optString("group_id");
                            String groupName = data.optString("group_name");
                            String groupAvatar = data.optString("group_avatar");

                            if (!TextUtils.isEmpty(groupId)) {
                                if (groupName != null && !groupName.isEmpty() && groupAvatar != null && !groupAvatar.isEmpty()) {
                                    App.db.execSQL("UPDATE friend SET Nickname = ?, _Nickname = CASE WHEN Nickname = _Nickname THEN ? ELSE _Nickname END, Image = ? WHERE UserID = ? AND Type = 30",
                                            new Object[]{groupName, groupName, groupAvatar, groupId});
                                } else if (groupName != null && !groupName.isEmpty()) {
                                    App.db.execSQL("UPDATE friend SET Nickname = ?, _Nickname = CASE WHEN Nickname = _Nickname THEN ? ELSE _Nickname END WHERE UserID = ? AND Type = 30",
                                            new Object[]{groupName, groupName, groupId});
                                } else if (groupAvatar != null && !groupAvatar.isEmpty()) {
                                    App.db.execSQL("UPDATE friend SET Image = ? WHERE UserID = ? AND Type = 30",
                                            new Object[]{groupAvatar, groupId});
                                }

                                if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                    FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "更新群资料失败", e);
                        }
                    }
                    else if ("Group_Disbanded".equals(type)) {
                        try {
                            String targetGroupId = data.optString("group_id");
                            if (!TextUtils.isEmpty(targetGroupId)) {
                                App.db.execSQL("DELETE FROM friend WHERE UserID = ? AND Type = 30", new Object[]{targetGroupId});

                                if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                    FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                                }

                                if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed()
                                        && targetGroupId.equals(activity_chat.Friend_UserID)) {
                                    activity_chat.that.runOnUiThread(() -> {
                                        FBMessage.Show(activity_chat.that, "该群聊已被群主解散");
                                        activity_chat.that.finish();
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理群解散通知异常", e);
                        }
                    }
                    else if ("Update_Friend_Type".equals(type)) {
                        int fromuid = data.getInt("_FromUID");
                        int type2 = data.getInt("_Type");
                        if (type2 == 9) {
                            App.db.execSQL("DELETE FROM friend WHERE Friend_UID = ? AND UID = ?", new Object[]{fromuid, App.UID});
                        } else {
                            int myLocalType = (type2 == 3) ? 4 : type2;
                            App.db.execSQL("UPDATE friend SET type = ? WHERE Friend_UID = ? AND UID = ?", new Object[]{myLocalType, fromuid, App.UID});
                            if (activity_friend.that != null) FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                            if (activity_chat.that != null && type2 == 3) activity_chat.that.finish();
                        }
                    }
                    else if ("YC_DELETE_CHAT".equals(type)) {
                        int uid = json.getInt("uid");

                        // 1. 删除该用户的单聊记录，以及所有群聊记录（ToUid 对应群组表流水号）
                        App.db.execSQL("DELETE FROM chat WHERE FromUid = ? OR ToUid = ? OR ToUid IN (SELECT Friend_UID FROM friend WHERE Type = 30)",
                                new Object[]{uid, uid});

                        // 2. 清空单聊和所有群组在列表上的最后一条消息摘要
                        App.db.execSQL("UPDATE friend SET Content = '' WHERE Friend_UID = ? OR Type = 30",
                                new Object[]{uid});

                        // 3. 刷新聊天界面与好友列表
                        if (activity_chat.that != null && !activity_chat.that.isFinishing()) {
                            activity_chat.that.runOnUiThread(() -> {
                                if (activity_chat.Datas_Chat != null) activity_chat.Datas_Chat.clear();
                                if (activity_chat.mAdapter_Chat != null) activity_chat.mAdapter_Chat.notifyDataSetChanged();
                            });
                        }
                        if (activity_friend.that != null && !activity_friend.that.isFinishing()) {
                            activity_friend.forceRefreshList();
                        }
                    }
                    else if ("DELETE_PASSWORD".equals(type)) {
                        SPUtils.getInstance().remove("password");
                        SPUtils.getInstance().remove("virtualpassword");
                    }
                    else if ("GET_USER".equals(type)) {
                        if (App.UID > 0 && MemberBillingBridge.get() != null) {
                            MemberBillingBridge.get().handleUserAuth("startup", App.UID);
                        }
                    }
                    else if ("Delete_Both_Sides_Chat".equals(type)) {
                        int senderUid = data.optInt("_FromUID");
                        int targetUid = data.optInt("_ToUid");

                        if (targetUid == App.UID) {
                            App.db.execSQL("DELETE FROM chat WHERE (ToUid = ? AND FromUid = ?) OR (ToUid = ? AND FromUid = ?)", new Object[]{senderUid, App.UID, App.UID, senderUid});
                            App.db.execSQL("UPDATE friend SET Content = '' WHERE Friend_UID = ?", new Object[]{senderUid});

                            if (activity_chat.that != null && !activity_chat.that.isFinishing() && !activity_chat.that.isDestroyed() && activity_chat.Friend_UID == senderUid) {
                                activity_chat.that.runOnUiThread(() -> {
                                    if (activity_chat.Datas_Chat != null) {
                                        activity_chat.Datas_Chat.clear();
                                    }
                                    if (activity_chat.mAdapter_Chat != null) {
                                        activity_chat.mAdapter_Chat.notifyDataSetChanged();
                                    }
                                    FBMessage.Show(activity_chat.that, "对方清空了双方的聊天记录");
                                });
                            }

                            if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                FriendUtils.GET_LOCATION_FRIEND(activity_friend.that);
                            }
                        }
                    }
                    else if ("ServerPush".equals(type) || "WebPush".equals(type)) {
                        try {
                            String pushTitle = data.optString("title", "来自喵喵杂志");
                            String pushContent = data.optString("content", "");
                            String pushThumb = data.has("thumb") ? data.optString("thumb") : data.optString("thumbUrl");
                            String pushUrl = data.optString("url", "");

                            Utils.PushNotificationUtils.showNotification(
                                    App.AppContext,
                                    pushTitle,
                                    pushContent,
                                    pushThumb,
                                    pushUrl
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "处理推送通知异常", e);
                        }
                    }
                    else if ("OnlineStatusResult".equals(type)) {
                        try {
                            if (data != null) {
                                java.util.Map<Integer, Boolean> onlineStatuses = new java.util.HashMap<>();
                                java.util.Iterator<String> keys = data.keys();
                                while (keys.hasNext()) {
                                    String uidStr = keys.next();
                                    boolean isOnline = data.getBoolean(uidStr);
                                    onlineStatuses.put(Integer.parseInt(uidStr), isOnline);
                                }

                                mainHandler.post(() -> {
                                    if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                        activity_friend.updateOnlineStatuses(onlineStatuses);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "解析批量在线状态结果失败", e);
                        }
                    }
                    else if ("OnlineStatusResult".equals(type)) {
                        try {
                            if (data != null) {
                                java.util.Map<Integer, Boolean> onlineStatuses = new java.util.HashMap<>();
                                java.util.Iterator<String> keys = data.keys();
                                while (keys.hasNext()) {
                                    String uidStr = keys.next();
                                    // 🌟 同样加上纯数字检查
                                    if (!TextUtils.isEmpty(uidStr) && TextUtils.isDigitsOnly(uidStr)) {
                                        boolean isOnline = data.getBoolean(uidStr);
                                        onlineStatuses.put(Integer.parseInt(uidStr), isOnline);
                                    }
                                }

                                mainHandler.post(() -> {
                                    if (activity_friend.that != null && !activity_friend.that.isFinishing() && !activity_friend.that.isDestroyed()) {
                                        activity_friend.updateOnlineStatuses(onlineStatuses);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "解析批量在线状态结果失败", e);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析 WebSocket 消息失败", e);
            }
        }
    }

    public void uploadFile(File file, String messageId) {
        String fileId = file.getName();
        if (messageId != null) {
            uploadMessageIdMap.put(fileId, messageId);
        }

        uploadExecutor.execute(() -> {
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[CHUNK_SIZE];
                int totalChunks = (int) Math.ceil((double) file.length() / CHUNK_SIZE);
                int index = 0, read;

                while ((read = fis.read(buffer)) != -1) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    sendChunk(fileId, index, totalChunks, chunk);

                    if (messageId != null) {
                        int progress = (int) (((index + 1) * 100f) / totalChunks);
                        if (progress >= 100) progress = 99;
                        activity_chat.updateUploadProgress(messageId, progress, true);
                    }
                    index++;

                    if (webSocket != null) {
                        while (webSocket.queueSize() > 2 * 1024 * 1024) {
                            Thread.sleep(200);
                        }
                    }
                }
                fis.close();
            } catch (Exception e) {
                Log.e(TAG, "Upload 循环流读取分配异常崩塌", e);
                if (messageId != null) {
                    activity_chat.updateUploadProgress(messageId, 0, false);
                }
            }
        });
    }

    private void sendChunk(String fileId, int index, int total, byte[] chunk) {
        try {
            String headerJson = "{\"fileId\":\"" + fileId + "\",\"index\":" + index + ",\"total\":" + total + "}";
            byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);

            byte[] headerFixed = new byte[HEADER_SIZE];

            System.arraycopy(headerBytes, 0, headerFixed, 0, headerBytes.length);

            ByteBuffer packet = ByteBuffer.allocate(HEADER_SIZE + chunk.length);
            packet.put(headerFixed);
            packet.put(chunk);

            if (webSocket != null && socketConnected.get()) {
                webSocket.send(ByteString.of(packet.array()));
            }
        } catch (Exception e) {
            Log.e(TAG, "sendChunk 出现致命写入错误", e);
        }
    }
}