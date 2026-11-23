package com.cassimtv.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
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
    private final android.os.Handler heartbeatHandler = new android.os.Handler();
    private Runnable heartbeatTask;
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (socket != null && "android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                sendTvState(socket);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(volumeReceiver, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            deviceId = intent.getStringExtra("device_id");
            receiverToken = intent.getStringExtra("receiver_token");
        }
        if (deviceId == null || receiverToken == null) {
            android.content.SharedPreferences prefs = getSharedPreferences("tv_settings", MODE_PRIVATE);
            deviceId = prefs.getString("device_id", null);
            receiverToken = prefs.getString("receiver_token", null);
        }
        if (deviceId == null || receiverToken == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        getSharedPreferences("tv_settings", MODE_PRIVATE).edit()
                .putString("device_id", deviceId)
                .putString("receiver_token", receiverToken)
                .apply();
        startForeground(NOTIFICATION_ID, notification("Waiting for remote connection"));
        stopped = false;
        if (socket == null) connectSocket();
        return START_STICKY;
    }

    private void connectSocket() {
        if (stopped || socket != null || deviceId == null || receiverToken == null) return;
        String url = RemoteApi.BASE_URL.replace("https://", "wss://")
                + "/tv/receiver/" + deviceId;
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + receiverToken)
                .build();
        socket = RemoteApi.client().newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                if (stopped || socket != webSocket) {
                    webSocket.close(1000, "Receiver no longer active");
                    return;
                }
                updateNotification("Connected — ready for remote commands");
                startHeartbeat(webSocket);
                sendTvState(webSocket);
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject message = new JSONObject(text);
                    if ("get_state".equals(message.optString("type"))) {
                        sendTvState(webSocket);
                        return;
                    }
                    if (!"command".equals(message.optString("type"))) return;
                    String id = message.optString("id");
                    String action = message.optString("action");
                    boolean ok = executeAction(action);
                    JSONObject ack = new JSONObject();
                    ack.put("type", "ack");
                    ack.put("id", id);
                    ack.put("ok", ok);
                    ack.put("detail", ok ? "Executed" : "This action is unavailable. Enable the Cassim accessibility service or check TV support.");
                    appendVolumeState(ack);
                    webSocket.send(ack.toString());
                } catch (Exception ignored) {
                }
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (socket != webSocket) return;
                socket = null;
                stopHeartbeat();
                scheduleReconnect();
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (socket != webSocket) return;
                socket = null;
                stopHeartbeat();
                updateNotification("Connection interrupted — reconnecting");
                scheduleReconnect();
            }
        });
    }

    private void startHeartbeat(WebSocket webSocket) {
        stopHeartbeat();
        heartbeatTask = new Runnable() {
            @Override public void run() {
                if (stopped || socket != webSocket) return;
                webSocket.send("{\"type\":\"ping\"}");
                heartbeatHandler.postDelayed(this, 25000);
            }
        };
        heartbeatHandler.postDelayed(heartbeatTask, 25000);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) heartbeatHandler.removeCallbacks(heartbeatTask);
        heartbeatTask = null;
    }

    private void scheduleReconnect() {
        if (!stopped) {
            new android.os.Handler(getMainLooper()).postDelayed(this::connectSocket, 3000);
        }
    }

    private void appendVolumeState(JSONObject target) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            target.put("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            target.put("max_volume", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            target.put("muted", audio.isStreamMute(AudioManager.STREAM_MUSIC));
        } catch (Exception ignored) { }
    }

    private void sendTvState(WebSocket webSocket) {
        try {
            JSONObject state = new JSONObject();
            state.put("type", "tv_state");
            appendVolumeState(state);
            webSocket.send(state.toString());
        } catch (Exception ignored) { }
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
        stopHeartbeat();
        try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) { }
        if (socket != null) socket.close(1000, "Receiver stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
