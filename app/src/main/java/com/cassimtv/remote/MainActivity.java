package com.cassimtv.remote;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 25);
    private static final int PANEL = Color.rgb(19, 29, 49);
    private static final int BLUE = Color.rgb(49, 119, 245);
    private static final int TEXT = Color.rgb(244, 247, 255);
    private static final int MUTED = Color.rgb(157, 174, 202);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private TextView status;
    private String deviceId;
    private String remoteToken;
    private String pairingCode;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }

    private void basePage(String eyebrow, String heading, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(30), dp(24), dp(30));
        scroll.addView(page);
        TextView brand = text("CASSIM TV", 13, BLUE, true);
        brand.setLetterSpacing(0.18f);
        page.addView(brand);
        TextView title = text(heading, 30, TEXT, true);
        LinearLayout.LayoutParams tp = params(-2, -2);
        tp.topMargin = dp(7);
        page.addView(title, tp);
        TextView desc = text(subtitle, 14, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp = params(-1, -2);
        dp.topMargin = dp(7);
        page.addView(desc, dp);
        setContentView(scroll);
    }

    private void showHome() {
        basePage("HOME", "One app. Two roles.", "Choose how you want to use this device.");
        View gap = new View(this);
        page.addView(gap, params(1, dp(30)));
        addRoleCard("THIS IS TV", "Display a secure pairing PIN and receive commands.", () -> createPairing());
        addRoleCard("THIS IS REMOTE", "Enter the PIN shown on your television.", () -> showRemotePair());
        TextView note = text("Both devices need an internet connection to reach the relay.", 12, MUTED, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = params(-1, -2);
        np.topMargin = dp(26);
        page.addView(note, np);
    }

    private void addRoleCard(String title, String description, Runnable click) {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = params(-1, -2);
        cp.topMargin = dp(14);
        page.addView(card, cp);
        TextView iconView = text(title.equals("THIS IS TV") ? "▣" : "◉", 34, BLUE, true);
        card.addView(iconView);
        TextView heading = text(title, 18, TEXT, true);
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(8);
        heading.setGravity(Gravity.CENTER);
        card.addView(heading, hp);
        TextView desc = text(description, 13, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp = params(-1, -2);
        dp.topMargin = dp(6);
        card.addView(desc, dp);
        card.setOnClickListener(v -> click.run());
        card.setFocusable(true);
        card.setClickable(true);
    }

    private void createPairing() {
        basePage("TV RECEIVER", "Pair this TV", "Keep this screen open while you connect your phone.");
        status = text("Contacting Cassim relay…", 14, MUTED, false);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(24);
        status.setGravity(Gravity.CENTER);
        page.addView(status, sp);
        worker.execute(() -> {
            try {
                JSONObject result = RemoteApi.post("/tv/pair/start", new JSONObject(), null);
                deviceId = result.getString("device_id");
                pairingCode = result.getString("pairing_code");
                String receiverToken = result.getString("receiver_token");
                runOnUiThread(() -> {
                    status.setText("ENTER THIS CODE ON YOUR PHONE");
                    status.setTextColor(MUTED);
                    TextView pin = text(pairingCode, 42, Color.rgb(91, 220, 158), true);
                    pin.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams pp = params(-1, dp(100));
                    pp.topMargin = dp(18);
                    page.addView(pin, pp);
                    TextView expiry = text("Code expires in 5 minutes", 13, MUTED, false);
                    expiry.setGravity(Gravity.CENTER);
                    page.addView(expiry);
                    Button access = button("ENABLE TV NAVIGATION", Color.rgb(42, 57, 82));
                    LinearLayout.LayoutParams ap = params(-1, dp(52));
                    ap.topMargin = dp(25);
                    page.addView(access, ap);
                    access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
                    TextView help = text("Enable “Cassim TV Navigation” in Accessibility settings for Home, Back and directional navigation. Volume works without it.", 12, MUTED, false);
                    help.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams hp = params(-1, -2);
                    hp.topMargin = dp(12);
                    page.addView(help, hp);
                    Button home = button("BACK", Color.rgb(42, 57, 82));
                    LinearLayout.LayoutParams bp = params(-1, dp(48));
                    bp.topMargin = dp(22);
                    page.addView(home, bp);
                    home.setOnClickListener(v -> showHome());
                    Intent service = new Intent(this, TvReceiverService.class);
                    service.putExtra("device_id", deviceId);
                    service.putExtra("receiver_token", receiverToken);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                    else startService(service);
                    status.setText("Waiting for your phone to pair…");
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not contact the server." : e.getMessage();
                runOnUiThread(() -> status.setText("Pairing failed: " + message));
            }
        });
    }

    private void showRemotePair() {
        basePage("REMOTE", "Connect to your TV", "Enter the six-digit PIN displayed on your television.");
        EditText pin = new EditText(this);
        pin.setSingleLine(true);
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(TEXT);
        pin.setHintTextColor(MUTED);
        pin.setHint("000000");
        pin.setTextSize(28);
        pin.setLetterSpacing(0.2f);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(PANEL);
        inputBg.setCornerRadius(dp(16));
        pin.setBackground(inputBg);
        LinearLayout.LayoutParams ip = params(-1, dp(76));
        ip.topMargin = dp(30);
        page.addView(pin, ip);
        status = text("", 13, MUTED, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(14);
        page.addView(status, sp);
        Button connect = button("CONNECT TO TV", BLUE);
        LinearLayout.LayoutParams cp = params(-1, dp(56));
        cp.topMargin = dp(20);
        page.addView(connect, cp);
        connect.setOnClickListener(v -> {
            String code = pin.getText().toString().trim();
            if (!code.matches("\\d{6}")) {
                status.setText("Enter all six digits.");
                return;
            }
            connect.setEnabled(false);
            status.setText("Pairing…");
            worker.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("code", code);
                    JSONObject result = RemoteApi.post("/tv/pair/claim", body, null);
                    deviceId = result.getString("device_id");
                    remoteToken = result.getString("remote_token");
                    getSharedPreferences("remote_settings", MODE_PRIVATE).edit()
                            .putString("device_id", deviceId)
                            .putString("remote_token", remoteToken)
                            .apply();
                    runOnUiThread(this::showRemote);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Pairing failed." : e.getMessage();
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        status.setText(message);
                    });
                }
            });
        });
    }

    private void showRemote() {
        basePage("CONNECTED REMOTE", "TV Remote", "Commands are relayed securely to your paired TV.");
        LinearLayout badge = card();
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = params(-1, dp(58));
        bp.topMargin = dp(20);
        page.addView(badge, bp);
        TextView connected = text("●  PAIRED WITH TV", 13, Color.rgb(91, 220, 158), true);
        badge.addView(connected);
        TextView navTitle = text("NAVIGATION", 12, MUTED, true);
        LinearLayout.LayoutParams nt = params(-1, -2);
        nt.topMargin = dp(25);
        page.addView(navTitle, nt);
        LinearLayout pad = card();
        LinearLayout.LayoutParams pp = params(-1, -2);
        pp.topMargin = dp(10);
        page.addView(pad, pp);
        LinearLayout r1 = row();
        pad.addView(r1, params(-1, dp(58)));
        spacer(r1);
        addRemoteButton(r1, "▲", "DPAD_UP");
        spacer(r1);
        LinearLayout r2 = row();
        LinearLayout.LayoutParams r2p = params(-1, dp(62));
        r2p.topMargin = dp(6);
        pad.addView(r2, r2p);
        addRemoteButton(r2, "◀", "DPAD_LEFT");
        addRemoteButton(r2, "OK", "OK", true);
        addRemoteButton(r2, "▶", "DPAD_RIGHT");
        LinearLayout r3 = row();
        LinearLayout.LayoutParams r3p = params(-1, dp(58));
        r3p.topMargin = dp(6);
        pad.addView(r3, r3p);
        spacer(r3);
        addRemoteButton(r3, "▼", "DPAD_DOWN");
        spacer(r3);

        LinearLayout quick = row();
        LinearLayout.LayoutParams qp = params(-1, dp(54));
        qp.topMargin = dp(12);
        page.addView(quick, qp);
        addRemoteButton(quick, "BACK", "BACK");
        addRemoteButton(quick, "HOME", "HOME");
        addRemoteButton(quick, "POWER", "POWER");

        TextView volumeTitle = text("VOLUME", 12, MUTED, true);
        LinearLayout.LayoutParams vt = params(-1, -2);
        vt.topMargin = dp(24);
        page.addView(volumeTitle, vt);
        LinearLayout volume = row();
        LinearLayout.LayoutParams vp = params(-1, dp(58));
        vp.topMargin = dp(10);
        page.addView(volume, vp);
        addRemoteButton(volume, "−", "VOLUME_DOWN");
        addRemoteButton(volume, "MUTE", "MUTE");
        addRemoteButton(volume, "+", "VOLUME_UP");

        status = text("Ready", 12, MUTED, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = params(-1, -2);
        st.topMargin = dp(15);
        page.addView(status, st);
        Button unpair = button("DISCONNECT / CHANGE TV", Color.rgb(42, 57, 82));
        LinearLayout.LayoutParams up = params(-1, dp(48));
        up.topMargin = dp(15);
        page.addView(unpair, up);
        unpair.setOnClickListener(v -> {
            getSharedPreferences("remote_settings", MODE_PRIVATE).edit().clear().apply();
            showHome();
        });
    }

    private void addRemoteButton(LinearLayout row, String label, String action) {
        addRemoteButton(row, label, action, false);
    }

    private void addRemoteButton(LinearLayout row, String label, String action, boolean primary) {
        Button b = button(label, primary ? BLUE : Color.rgb(39, 53, 78));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        row.addView(b, p);
        b.setOnClickListener(v -> sendCommand(action));
    }

    private void sendCommand(String action) {
        if (deviceId == null || remoteToken == null) {
            status.setText("Pair with a TV first.");
            return;
        }
        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", deviceId);
                body.put("action", action);
                RemoteApi.post("/tv/command", body, remoteToken);
                runOnUiThread(() -> {
                    if (status != null) status.setText("Sent " + action.replace('_', ' '));
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Command failed." : e.getMessage();
                runOnUiThread(() -> {
                    if (status != null) status.setText(message);
                });
            }
        });
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(17), dp(17), dp(17), dp(17));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(19));
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
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(15));
        b.setBackground(bg);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
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
        worker.shutdown();
        super.onDestroy();
    }
}
