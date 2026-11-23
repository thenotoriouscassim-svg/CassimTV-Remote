package com.cassimtv.remote;

import android.content.Context;
import android.util.Base64;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ADB-over-TCP client using one persistent interactive shell.
 * Reusing the shell avoids starting a new remote process for every key press.
 */
public final class AdbRemote {
    private final Context context;
    private AdbConnection connection;
    private Socket socket;
    private AdbStream shell;
    private volatile boolean shellAlive;
    private Thread shellReader;

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
        socket.setKeepAlive(true);

        connection = AdbConnection.create(socket, loadOrCreateKey());
        boolean connected = connection.connect(12, TimeUnit.SECONDS, false);
        if (!connected) {
            disconnect();
            throw new IOException("ADB authorization was not completed. Accept the RSA prompt on the TV, then connect again.");
        }

        // Open one interactive shell and keep it alive for all remote button presses.
        shell = connection.open("shell:");
        shellAlive = true;
        shellReader = new Thread(() -> {
            try {
                while (shellAlive) {
                    byte[] data = shell.read();
                    if (data == null) break;
                    // Drain shell output continuously so its receive queue never backs up.
                }
            } catch (Exception ignored) {
                // A closed shell is reported by sendKey() on the next press.
            } finally {
                shellAlive = false;
            }
        }, "CassimTV-ADB-ShellReader");
        shellReader.setDaemon(true);
        shellReader.start();
    }

    /** Sends a key event over the already-open shell without waiting for command startup/exit. */
    public synchronized void sendKey(int keyCode) throws Exception {
        if (connection == null || shell == null || !shellAlive) {
            throw new IOException("ADB shell is no longer connected.");
        }
        String command = "input keyevent " + keyCode + "\n";
        shell.write(command.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized void disconnect() {
        shellAlive = false;
        try {
            if (shell != null) shell.close();
        } catch (Exception ignored) { }
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) { }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) { }
        shell = null;
        connection = null;
        socket = null;
        shellReader = null;
    }

    public synchronized boolean isConnected() {
        return connection != null && shell != null && shellAlive;
    }
}
