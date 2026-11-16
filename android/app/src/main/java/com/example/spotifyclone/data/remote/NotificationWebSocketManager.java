package com.example.spotifyclone.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.NotificationItem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class NotificationWebSocketManager {
    private static final String TAG = "NotificationWS";
    private static volatile NotificationWebSocketManager instance;

    public interface OnNotificationEventListener {
        default void onConnected(int unreadCount) {}
        default void onNewNotification(NotificationItem notification, int unreadCount) {}
        default void onNotificationRead(String notificationId, int unreadCount) {}
        default void onAllNotificationsRead(int unreadCount) {}
        default void onNotificationDeleted(String notificationId, int unreadCount) {}
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final CopyOnWriteArrayList<OnNotificationEventListener> listeners = new CopyOnWriteArrayList<>();

    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnecting = false;
    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private int lastKnownUnreadCount = 0;

    private NotificationWebSocketManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .build();
    }

    public static NotificationWebSocketManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NotificationWebSocketManager.class) {
                if (instance == null) {
                    instance = new NotificationWebSocketManager(context);
                }
            }
        }
        return instance;
    }

    public void registerListener(OnNotificationEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(OnNotificationEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public int getLastKnownUnreadCount() {
        return lastKnownUnreadCount;
    }

    public void setLastKnownUnreadCount(int count) {
        this.lastKnownUnreadCount = Math.max(0, count);
    }

    public synchronized void connect() {
        if (webSocket != null || isConnecting) {
            return;
        }

        String token = SessionManager.getInstance(appContext).getToken();
        if (token == null || token.trim().isEmpty()) {
            Log.d(TAG, "No auth token available, skipping WebSocket connection");
            return;
        }

        String baseUrl = ApiClient.getBaseUrl();
        String wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        if (wsBase.endsWith("/")) {
            wsBase = wsBase.substring(0, wsBase.length() - 1);
        }
        String wsUrl = wsBase + "/ws?token=" + token;

        isConnecting = true;
        shouldReconnect = true;

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        Log.d(TAG, "Connecting to WebSocket: " + wsBase + "/ws");

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnecting = false;
                reconnectAttempts = 0;
                Log.i(TAG, "WebSocket connected successfully");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "Received WS message: " + text);
                try {
                    JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                    String event = extractNonEmptyString(json, "event");
                    if (event == null) {
                        event = extractNonEmptyString(json, "type");
                    }
                    if (event == null) return;

                    final String resolvedEvent = event;
                    final JsonObject dataObj = (json.has("data") && !json.get("data").isJsonNull() && json.get("data").isJsonObject())
                            ? json.getAsJsonObject("data")
                            : json;
                    mainHandler.post(() -> handleEvent(resolvedEvent, dataObj, json));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing WS message", e);
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " / " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " / " + reason);
                webSocket = null;
                isConnecting = false;
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.w(TAG, "WebSocket failure: " + t.getMessage());
                webSocket = null;
                isConnecting = false;
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void handleEvent(String event, JsonObject dataObj, JsonObject rootJson) {
        Integer payloadUnread = extractOptionalInt(dataObj, "unreadCount");
        if (payloadUnread == null) {
            payloadUnread = extractOptionalInt(rootJson, "unreadCount");
        }
        if (payloadUnread != null) {
            lastKnownUnreadCount = Math.max(0, payloadUnread);
        } else if ("NEW_NOTIFICATION".equals(event)) {
            lastKnownUnreadCount = Math.max(1, lastKnownUnreadCount + 1);
        }

        switch (event) {
            case "CONNECTED":
                for (OnNotificationEventListener listener : listeners) {
                    listener.onConnected(lastKnownUnreadCount);
                }
                break;

            case "NEW_NOTIFICATION":
                NotificationItem item = null;
                if (dataObj != null && dataObj.has("notification") && dataObj.get("notification").isJsonObject()) {
                    item = gson.fromJson(dataObj.get("notification"), NotificationItem.class);
                } else if (rootJson != null && rootJson.has("notification") && rootJson.get("notification").isJsonObject()) {
                    item = gson.fromJson(rootJson.get("notification"), NotificationItem.class);
                } else if (dataObj != null) {
                    item = gson.fromJson(dataObj, NotificationItem.class);
                }
                for (OnNotificationEventListener listener : listeners) {
                    listener.onNewNotification(item, lastKnownUnreadCount);
                }
                break;

            case "NOTIFICATION_READ":
                String notifId = extractNonEmptyString(dataObj, "notificationId");
                if (notifId == null) {
                    notifId = extractNonEmptyString(rootJson, "notificationId");
                }
                for (OnNotificationEventListener listener : listeners) {
                    listener.onNotificationRead(notifId, lastKnownUnreadCount);
                }
                break;

            case "NOTIFICATION_READ_ALL":
            case "NOTIFICATIONS_READ_ALL":
                for (OnNotificationEventListener listener : listeners) {
                    listener.onAllNotificationsRead(lastKnownUnreadCount);
                }
                break;

            case "NOTIFICATION_DELETED":
                String deletedId = extractNonEmptyString(dataObj, "notificationId");
                if (deletedId == null) {
                    deletedId = extractNonEmptyString(rootJson, "notificationId");
                }
                for (OnNotificationEventListener listener : listeners) {
                    listener.onNotificationDeleted(deletedId, lastKnownUnreadCount);
                }
                break;
        }
    }

    private String extractNonEmptyString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        String val = obj.get(key).getAsString();
        return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
    }

    private Integer extractOptionalInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        reconnectAttempts++;
        long delaySeconds = Math.min(30, (long) Math.pow(2, Math.min(reconnectAttempts, 5)));
        Log.d(TAG, "Scheduling reconnect in " + delaySeconds + "s (attempt " + reconnectAttempts + ")");
        mainHandler.postDelayed(() -> {
            if (shouldReconnect && webSocket == null) {
                connect();
            }
        }, delaySeconds * 1000);
    }

    public synchronized void disconnect() {
        shouldReconnect = false;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "App closed WS");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        isConnecting = false;
        reconnectAttempts = 0;
    }
}
