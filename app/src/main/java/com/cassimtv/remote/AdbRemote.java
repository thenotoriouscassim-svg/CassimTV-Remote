package com.cassimtv.remote;

import android.content.Context;
import android.util.Base64;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Small ADB-over-TCP client. All public methods are called from a worker thread. */
public final class AdbRemote {
    private final Context context;
    private AdbConnection connection;
    private Socket socket;

    public AdbRemote(Context context) {
        this.context = context.getApplicationContext();
    }

    private AdbCrypto loadOrCreateKey() throws Exception {
        File privateKey = new File(context.getFilesDir(), "adbkey");
        File publicKey = new File(context.getFilesDir(), "adbkey.pub");
        AdbBase64 encoder = data -> Base64.encodeToString(data, Base64.NO_WRAP);

        if (privateKey.exists() && publicKey.exists()) {
            return AdbCrypto.loadAdbKeyPair(encoder, privateKey, publicKey);
        }

        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(encoder);
        crypto.saveAdbKeyPair(privateKey, publicKey);
        return crypto;
    }

    public synchronized void connect(String host, int port) throws Exception {
        disconnect();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);

        connection = AdbConnection.create(socket, loadOrCreateKey());
        boolean connected = connection.connect(12, TimeUnit.SECONDS, false);
        if (!connected) {
            disconnect();
            throw new IOException("No ADB authorization yet. Check the TV for an RSA permission prompt, accept it, then tap Connect again.");
        }
    }

    public synchronized String command(String command) throws Exception {
        if (connection == null) throw new IOException("Not connected to the TV.");
        AdbStream stream = connection.open("shell:" + command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while (true) {
                byte[] chunk = stream.read();
                if (chunk == null) break;
                output.write(chunk);
            }
        } catch (IOException endOfStream) {
            // ADB closes the shell stream when the command finishes.
        } finally {
            try { stream.close(); } catch (Exception ignored) { }
        }
        return output.toString(StandardCharsets.UTF_8.name()).trim();
    }

    public synchronized void disconnect() {
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) { }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) { }
        connection = null;
        socket = null;
    }

    public synchronized boolean isConnected() {
        return connection != null;
    }
}
