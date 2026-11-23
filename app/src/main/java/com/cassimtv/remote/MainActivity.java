package com.cassimtv.remote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 15, 28);
    private static final int PANEL = Color.rgb(20, 29, 48);
    private static final int BLUE = Color.rgb(38, 112, 235);
    private static final int TEXT = Color.rgb(242, 246, 255);
    private static final int MUTED = Color.rgb(157, 174, 202);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AdbRemote remote;
    private SharedPreferences prefs;
    private EditText ipField;
    private EditText portField;
    private LinearLayout ipPanel;
    private TextView status;
    private TextView addressLabel;
    private Button connectButton;
    private boolean connected = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        prefs = getSharedPreferences("remote_settings", MODE_PRIVATE);
        remote = new AdbRemote(this);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(26), dp(22), dp(28));
        scroll.addView(page);

        TextView brand = text("CASSIM TV", 13, BLUE, true);
        brand.setLetterSpacing(0.16f);
        page.addView(brand);
        TextView heading = text("Remote", 32, TEXT, true);
        LinearLayout.LayoutParams h = params(-2, -2); h.topMargin = dp(3);
        page.addView(heading, h);
        TextView subtitle = text("Your TV. One tap away.", 14, MUTED, false);
        page.addView(subtitle, params(-2, -2));

        LinearLayout connectionCard = card();
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(24);
        page.addView(connectionCard, cp);
        LinearLayout topRow = row();
        connectionCard.addView(topRow);
        TextView connectionTitle = text("TV CONNECTION", 12, MUTED, true);
        connectionTitle.setLetterSpacing(0.08f);
        topRow.addView(connectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        status = text("Not connected", 12, MUTED, true);
        topRow.addView(status);

        addressLabel = text("No TV address saved", 14, TEXT, false);
        LinearLayout.LayoutParams al = params(-1, -2); al.topMargin = dp(12);
        connectionCard.addView(addressLabel, al);

        ipPanel = new LinearLayout(this);
        ipPanel.setOrientation(LinearLayout.VERTICAL);
        ipPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams ipp = params(-1, -2); ipp.topMargin = dp(12);
        connectionCard.addView(ipPanel, ipp);
        ipField = new EditText(this);
        ipField.setSingleLine(true);
        ipField.setHint("TV IP address (e.g. 192.168.0.184)");
        ipField.setTextColor(TEXT); ipField.setHintTextColor(MUTED);
        ipField.setText(prefs.getString("ip", ""));
        ipField.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        ipPanel.addView(ipField, params(-1, dp(52)));
        portField = new EditText(this);
        portField.setSingleLine(true);
        portField.setHint("ADB port (default 5555)");
        portField.setTextColor(TEXT); portField.setHintTextColor(MUTED);
        portField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portField.setText(String.valueOf(prefs.getInt("port", 5555)));
        LinearLayout.LayoutParams pp = params(-1, dp(48)); pp.topMargin = dp(7);
        ipPanel.addView(portField, pp);

        LinearLayout actions = row();
        LinearLayout.LayoutParams ap = params(-1, dp(48)); ap.topMargin = dp(13);
        connectionCard.addView(actions, ap);
        connectButton = button("Connect", BLUE);
        actions.addView(connectButton, new LinearLayout.LayoutParams(0, -1, 1));
        Button changeIp = button("Change TV IP", Color.rgb(47, 61, 86));
        LinearLayout.LayoutParams cip = new LinearLayout.LayoutParams(0, -1, 1);
        cip.leftMargin = dp(8); actions.addView(changeIp, cip);

        connectButton.setOnClickListener(v -> {
            if (connected && ipPanel.getVisibility() == View.VISIBLE) {
                disconnectUi();
                connectFromFields();
            } else if (connected) {
                disconnectUi();
            } else {
                connectFromFields();
            }
        });
        changeIp.setOnClickListener(v -> {
            boolean show = ipPanel.getVisibility() != View.VISIBLE;
            ipPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            connectButton.setText(connected ? (show ? "Reconnect" : "Disconnect") : "Connect");
            if (show) ipField.requestFocus();
        });

        TextView remoteLabel = text("REMOTE CONTROL", 12, MUTED, true);
        remoteLabel.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams rl = params(-2, -2); rl.topMargin = dp(27);
        page.addView(remoteLabel, rl);

        LinearLayout dpad = card();
        LinearLayout.LayoutParams dp = params(-1, -2); dp.topMargin = dp(12);
        page.addView(dpad, dp);
        addDpad(dpad);

        LinearLayout quick = row();
        LinearLayout.LayoutParams qp = params(-1, dp(56)); qp.topMargin = dp(13);
        page.addView(quick, qp);
        addQuick(quick, "BACK", 4);
        addQuick(quick, "HOME", 3);
        addQuick(quick, "POWER", 26);

        LinearLayout volume = card();
        LinearLayout.LayoutParams vp = params(-1, -2); vp.topMargin = dp(13);
        page.addView(volume, vp);
        TextView volumeTitle = text("VOLUME", 12, MUTED, true);
        volume.addView(volumeTitle);
        LinearLayout vr = row();
        LinearLayout.LayoutParams vrl = params(-1, dp(54)); vrl.topMargin = dp(10);
        volume.addView(vr, vrl);
        addVolume(vr, "−", 25);
        addVolume(vr, "MUTE", 164);
        addVolume(vr, "+", 24);

        TextView foot = text("Phone and TV must share the same network. TV Ethernet is supported.", 12, MUTED, false);
        LinearLayout.LayoutParams fp = params(-1, -2); fp.topMargin = dp(18);
        page.addView(foot, fp);
        setContentView(scroll);
        updateAddressLabel();
    }

    private void addDpad(LinearLayout parent) {
        LinearLayout.LayoutParams line = params(-1, dp(58));
        LinearLayout r1 = row(); parent.addView(r1, line);
        spacer(r1);
        addRemoteButton(r1, "▲", 19);
        spacer(r1);

        LinearLayout r2 = row(); LinearLayout.LayoutParams p2 = params(-1, dp(62)); p2.topMargin = dp(6); parent.addView(r2, p2);
        addRemoteButton(r2, "◀", 21);
        addRemoteButton(r2, "OK", 23);
        addRemoteButton(r2, "▶", 22);

        LinearLayout r3 = row(); LinearLayout.LayoutParams p3 = params(-1, dp(58)); p3.topMargin = dp(6); parent.addView(r3, p3);
        spacer(r3);
        addRemoteButton(r3, "▼", 20);
        spacer(r3);
    }

    private void addRemoteButton(LinearLayout row, String label, int key) {
        Button b = button(label, label.equals("OK") ? BLUE : Color.rgb(39, 52, 76));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        row.addView(b, p);
        b.setOnClickListener(v -> sendKey(key));
    }

    private void addQuick(LinearLayout row, String label, int key) {
        Button b = button(label, Color.rgb(39, 52, 76));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        row.addView(b, p);
        b.setOnClickListener(v -> sendKey(key));
    }

    private void addVolume(LinearLayout row, String label, int key) {
        Button b = button(label, Color.rgb(39, 52, 76));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        row.addView(b, p);
        b.setOnClickListener(v -> sendKey(key));
    }

    private void connectFromFields() {
        String host = ipField.getText().toString().trim();
        String portText = portField.getText().toString().trim();
        int port;
        try { port = Integer.parseInt(portText.isEmpty() ? "5555" : portText); }
        catch (Exception e) { setStatus("Invalid port", false); return; }
        if (!host.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$") || port < 1 || port > 65535) {
            setStatus("Enter a valid TV IP and port", false); return;
        }
        prefs.edit().putString("ip", host).putInt("port", port).apply();
        updateAddressLabel();
        setStatus("Connecting…", false);
        connectButton.setEnabled(false);
        worker.execute(() -> {
            try {
                remote.connect(host, port);
                runOnUiThread(() -> {
                    connected = true; connectButton.setEnabled(true);
                    connectButton.setText("Disconnect");
                    setStatus("Connected", true);
                    ipPanel.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Connection failed" : e.getMessage();
                runOnUiThread(() -> {
                    connected = false; connectButton.setEnabled(true);
                    connectButton.setText("Connect");
                    setStatus(message, false);
                });
            }
        });
    }

    private void sendKey(int keyCode) {
        if (!connected) { setStatus("Connect to your TV first", false); return; }
        worker.execute(() -> {
            try {
                remote.command("input keyevent " + keyCode);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connected = false; connectButton.setText("Connect");
                    setStatus("Connection lost: " + e.getMessage(), false);
                });
            }
        });
    }

    private void disconnectUi() {
        worker.execute(() -> remote.disconnect());
        connected = false;
        connectButton.setText("Connect");
        setStatus("Disconnected", false);
    }

    private void setStatus(String message, boolean good) {
        status.setText(message);
        status.setTextColor(good ? Color.rgb(93, 220, 154) : MUTED);
    }

    private void updateAddressLabel() {
        String ip = prefs.getString("ip", "");
        addressLabel.setText(ip.isEmpty() ? "No TV address saved" : ip + ":" + prefs.getInt("port", 5555));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL); bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        return box;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(14); b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setMinHeight(0); b.setMinimumHeight(0);
        return b;
    }

    private void spacer(LinearLayout row) {
        View v = new View(this);
        row.addView(v, new LinearLayout.LayoutParams(0, 1, 1));
    }

    private LinearLayout.LayoutParams params(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        worker.execute(() -> remote.disconnect());
        worker.shutdown();
        super.onDestroy();
    }
}
