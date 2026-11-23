package com.cassimtv.remote;

import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import android.os.Looper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RemoteApi {
    public static final String BASE_URL = "https://cassim-tv-relay.ramancassim-764.workers.dev";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    private static final Handler SOCKET_HANDLER = new Handler(Looper.getMainLooper());
    private static final ConcurrentLinkedQueue<String> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile WebSocket remoteSocket;
    private static volatile String remoteDeviceId;
    private static volatile String remoteToken;
    private static volatile boolean connecting;

    private RemoteApi() {}

    public static JSONObject post(String path, JSONObject body, String bearer) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .post(RequestBody.create(JSON, body.toString()))
                .header("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) builder.header("Authorization", "Bearer " + bearer);
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            String text = response.body() == null ? "{}" : response.body().string();
            JSONObject result = new JSONObject(text);
            if (!response.isSuccessful()) throw new IOException(result.optString("error",
                    result.optString("detail", "Server request failed (" + response.code() + ").")));
            return result;
        }
    }

    public static synchronized void connectRemote(String deviceId, String token) {
        boolean changed = !deviceId.equals(remoteDeviceId) || !token.equals(remoteToken);
        remoteDeviceId = deviceId;
        remoteToken = token;
        if (changed && remoteSocket != null) {
            remoteSocket.close(1000, "Switching paired TV");
            remoteSocket = null;
        }
        ensureRemoteConnected();
    }

    public static void sendRemoteCommand(String action) {
        try {
            JSONObject command = new JSONObject();
            command.put("action", action);
            if (PENDING.size() < 32) PENDING.add(command.toString());
            flushCommands();
            ensureRemoteConnected();
        } catch (Exception ignored) { }
    }

    private static synchronized void ensureRemoteConnected() {
        if (remoteDeviceId == null || remoteToken == null || connecting
                || (remoteSocket != null && remoteSocket.send("{\"type\":\"ping\"}"))) return;
        connecting = true;
        String url = BASE_URL.replace("https://", "wss://") + "/tv/remote/" + remoteDeviceId;
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + remoteToken).build();
        CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                remoteSocket = webSocket;
                connecting = false;
                flushCommands();
            }
            @Override public void onMessage(WebSocket webSocket, String text) { }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (remoteSocket == webSocket) remoteSocket = null;
                connecting = false;
                scheduleReconnect();
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (remoteSocket == webSocket) remoteSocket = null;
                connecting = false;
                scheduleReconnect();
            }
        });
    }

    private static void flushCommands() {
        WebSocket socket = remoteSocket;
        if (socket == null) return;
        String message;
        while ((message = PENDING.peek()) != null) {
            if (!socket.send(message)) break;
            PENDING.poll();
        }
    }

    private static void scheduleReconnect() {
        SOCKET_HANDLER.postDelayed(RemoteApi::ensureRemoteConnected, 1200);
    }

    public static OkHttpClient client() { return CLIENT; }
}
