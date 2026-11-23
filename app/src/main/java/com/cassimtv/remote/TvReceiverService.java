package com.cassimtv.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TvReceiverService extends Service {
    private static final String CHANNEL_ID = "cassim_tv_receiver";
    private static final int NOTIFICATION_ID = 4107;
    private String deviceId;
    private String receiverToken;
    private WebSocket socket;
    private boolean stopped = false;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        deviceId = intent.getStringExtra("device_id");
        receiverToken = intent.getStringExtra("receiver_token");
        if (deviceId == null || receiverToken == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification("Waiting for remote connection"));
        stopped = false;
        connectSocket();
        return START_STICKY;
    }

    private void connectSocket() {
        if (stopped || deviceId == null || receiverToken == null) return;
        String url = RemoteApi.BASE_URL.replace("https://", "wss://")
                + "/tv/receiver/" + deviceId + "?token=" + receiverToken;
        Request request = new Request.Builder().url(url).build();
        socket = RemoteApi.client().newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                updateNotification("Connected — ready for remote commands");
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject message = new JSONObject(text);
                    if (!"command".equals(message.optString("type"))) return;
                    String id = message.optString("id");
                    String action = message.optString("action");
                    boolean ok = executeAction(action);
                    JSONObject ack = new JSONObject();
                    ack.put("type", "ack");
                    ack.put("id", id);
                    ack.put("ok", ok);
                    ack.put("detail", ok ? "Executed" : "This action is unavailable. Enable the Cassim accessibility service or check TV support.");
                    webSocket.send(ack.toString());
                } catch (Exception ignored) {
                }
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                scheduleReconnect();
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                updateNotification("Connection interrupted — reconnecting");
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!stopped) {
            new android.os.Handler(getMainLooper()).postDelayed(this::connectSocket, 3000);
        }
    }

    private boolean executeAction(String action) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (action) {
            case "VOLUME_UP":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return true;
            case "VOLUME_DOWN":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return true;
            case "MUTE":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                return true;
            default:
                return TvAccessibilityService.performRemoteAction(action);
        }
    }

    private Notification notification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Cassim TV Receiver")
                .setContentText(message)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(message));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cassim TV Receiver", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        stopped = true;
        if (socket != null) socket.close(1000, "Receiver stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
