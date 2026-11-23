package com.cassimtv.remote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final int BG        = Color.rgb(5,   8,  18);
    private static final int CARD      = Color.rgb(16,  25,  48);
    private static final int CARD2     = Color.rgb(22,  34,  64);
    private static final int BORDER    = Color.rgb(40,  65, 120);
    private static final int BLUE      = Color.rgb(55, 120, 255);
    private static final int BLUE_GLOW = Color.argb(60, 55, 120, 255);
    private static final int BLUE_DIM  = Color.rgb(28,  58, 130);
    private static final int TEAL      = Color.rgb(60, 220, 170);
    private static final int TEXT      = Color.rgb(235, 242, 255);
    private static final int MUTED     = Color.rgb(110, 138, 185);
    private static final int GHOST     = Color.rgb(50,  72, 115);
    private static final int RED       = Color.rgb(255,  70,  70);

    // ── State ─────────────────────────────────────────────────────────────────
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private TextView     status;
    private TextView     volumeReadout;
    private TextView     muteReadout;
    private ProgressBar  volumeProgress;
    private String       deviceId;
    private String       remoteToken;
    private String       pairingCode;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        android.content.SharedPreferences tvPrefs =
                getSharedPreferences("tv_settings", MODE_PRIVATE);
        String savedTvId    = tvPrefs.getString("device_id", null);
        String savedRecvTok = tvPrefs.getString("receiver_token", null);

        if (savedTvId != null && savedRecvTok != null) {
            Intent svc = new Intent(this, TvReceiverService.class);
            svc.putExtra("device_id", savedTvId);
            svc.putExtra("receiver_token", savedRecvTok);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        }

        android.content.SharedPreferences remotePrefs =
                getSharedPreferences("remote_settings", MODE_PRIVATE);
        deviceId    = remotePrefs.getString("device_id", null);
        remoteToken = remotePrefs.getString("remote_token", null);

        if (deviceId != null && remoteToken != null) showRemote();
        else showHome();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCREENS
    // ══════════════════════════════════════════════════════════════════════════

    private void showHome() {
        basePage();

        // ── Branding ─────────────────────────────────────────────────────────
        LinearLayout brandRow = hbox(Gravity.CENTER);
        LinearLayout.LayoutParams brp = params(-2, -2);
        brp.topMargin = dp(8);
        page.addView(brandRow, brp);

        View dot = ovalView(dp(7), dp(7), TEAL);
        brandRow.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView brand = tv("  CASSIM TV", 11, BLUE);
        brand.setLetterSpacing(0.20f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brandRow.addView(brand);

        // ── Hero ──────────────────────────────────────────────────────────────
        TextView hero = new TextView(this);
        hero.setText("One app.\nTwo roles.");
        hero.setTextSize(40);
        hero.setTextColor(TEXT);
        hero.setTypeface(Typeface.DEFAULT_BOLD);
        hero.setGravity(Gravity.CENTER);
        hero.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(20);
        page.addView(hero, hp);

        TextView sub = tv("Set this device as a TV receiver\nor control your TV as a remote.", 14, MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(10);
        page.addView(sub, sp);

        page.addView(spacerV(dp(28)));

        // ── Role cards ────────────────────────────────────────────────────────
        roleCard("Television",
                "Show a pairing code and accept commands from your phone.",
                "▣",
                () -> {
                    android.content.SharedPreferences p =
                            getSharedPreferences("tv_settings", MODE_PRIVATE);
                    String id  = p.getString("device_id", null);
                    String tok = p.getString("receiver_token", null);
                    if (id != null && tok != null) showExistingTv(id, tok);
                    else createPairing();
                });

        roleCard("Remote",
                "Enter the pairing code shown on your television.",
                "◉",
                this::showRemotePair);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void roleCard(String title, String desc, String icon, Runnable action) {
        LinearLayout card = glassCard(dp(20));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout.LayoutParams cp = params(-1, -2);
        cp.topMargin = dp(14);
        page.addView(card, cp);

        // Icon circle
        FrameLayout iconCircle = new FrameLayout(this);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(BLUE_DIM);
        circleBg.setStroke(dp(1), BORDER);
        iconCircle.setBackground(circleBg);
        int circleSize = dp(54);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(circleSize, circleSize);
        icp.gravity = Gravity.CENTER_VERTICAL;

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(22);
        iconTv.setTextColor(BLUE);
        iconTv.setGravity(Gravity.CENTER);
        iconCircle.addView(iconTv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        card.addView(iconCircle, icp);

        // Text
        LinearLayout textCol = vbox();
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, -2, 1);
        tcp.leftMargin = dp(16);

        TextView titleTv = tv(title, 17, TEXT);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(titleTv);

        TextView descTv = tv(desc, 13, MUTED);
        descTv.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dvp = params(-1, -2);
        dvp.topMargin = dp(4);
        textCol.addView(descTv, dvp);
        card.addView(textCol, tcp);

        // Chevron
        TextView chev = tv("›", 28, GHOST);
        chev.setPadding(dp(8), 0, 0, 0);
        card.addView(chev);

        touchFeedback(card, action);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void createPairing() {
        basePage();
        screenHeader("Television", "Waiting for remote…",
                "Show this code to your phone app to pair.");

        status = tv("Reaching Cassim relay…", 13, MUTED);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(24);
        page.addView(status, sp);

        worker.execute(() -> {
            try {
                JSONObject result = RemoteApi.post("/tv/pair/start", new JSONObject(), null);
                deviceId    = result.getString("device_id");
                pairingCode = result.getString("pairing_code");
                String receiverToken = result.getString("receiver_token");

                getSharedPreferences("tv_settings", MODE_PRIVATE).edit()
                        .putString("device_id", deviceId)
                        .putString("receiver_token", receiverToken)
                        .apply();

                runOnUiThread(() -> {
                    status.setText("Enter this code on your phone.");

                    LinearLayout pinCard = glassCard(dp(20));
                    pinCard.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams pcp = params(-1, dp(130));
                    pcp.topMargin = dp(20);
                    page.addView(pinCard, pcp);

                    TextView pinLabel = tv("PAIRING CODE", 10, MUTED);
                    pinLabel.setLetterSpacing(0.14f);
                    pinCard.addView(pinLabel);

                    TextView pinView = new TextView(this);
                    pinView.setText(pairingCode);
                    pinView.setTextSize(54);
                    pinView.setTextColor(TEAL);
                    pinView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    pinView.setGravity(Gravity.CENTER);
                    pinView.setLetterSpacing(0.12f);
                    LinearLayout.LayoutParams pvp = params(-1, -2);
                    pvp.topMargin = dp(6);
                    pinCard.addView(pinView, pvp);

                    Button access = pillButton("Enable TV Navigation", false);
                    LinearLayout.LayoutParams ap = params(-1, dp(54));
                    ap.topMargin = dp(24);
                    page.addView(access, ap);
                    access.setOnClickListener(v ->
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

                    Intent service = new Intent(this, TvReceiverService.class);
                    service.putExtra("device_id", deviceId);
                    service.putExtra("receiver_token", receiverToken);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                    else startService(service);
                });

            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Pairing failed — " + e.getMessage()));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────


    // Keep the existing receiver identity and allow another phone to pair.
    private void showExistingTv(String savedId, String savedToken) {
        Intent service = new Intent(this, TvReceiverService.class);
        service.putExtra("device_id", savedId);
        service.putExtra("receiver_token", savedToken);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        basePage();
        screenHeader("Television", "Your TV is ready",
                "This receiver keeps the same identity. Pair additional phones whenever you like.");

        LinearLayout card = glassCard(dp(20));
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = params(-1, dp(100));
        cp.topMargin = dp(25);
        page.addView(card, cp);

        TextView ready = tv("●  RECEIVER REGISTERED", 15, TEAL);
        ready.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(ready);

        Button add = pillButton("Pair another phone", true);
        LinearLayout.LayoutParams ap = params(-1, dp(54));
        ap.topMargin = dp(18);
        page.addView(add, ap);
        add.setOnClickListener(v -> createAdditionalPairing(savedId, savedToken));

        Button back = ghostBtn("Back");
        LinearLayout.LayoutParams bp = params(-1, dp(48));
        bp.topMargin = dp(10);
        page.addView(back, bp);
        back.setOnClickListener(v -> showHome());
    }

    private void createAdditionalPairing(String savedId, String savedToken) {
        basePage();
        screenHeader("Add a remote", "Pair another phone",
                "Enter this one-time code on the next phone. Existing remotes stay paired.");

        status = tv("Generating secure pairing code…", 14, MUTED);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(25);
        page.addView(status, sp);

        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", savedId);
                body.put("receiver_token", savedToken);
                JSONObject result = RemoteApi.post("/tv/pair/add", body, null);
                String code = result.getString("pairing_code");

                runOnUiThread(() -> {
                    status.setText("Enter this code on another phone");
                    LinearLayout pinCard = glassCard(dp(20));
                    pinCard.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams pcp = params(-1, dp(130));
                    pcp.topMargin = dp(18);
                    page.addView(pinCard, pcp);

                    TextView pin = tv(code, 48, TEAL);
                    pin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    pin.setGravity(Gravity.CENTER);
                    pinCard.addView(pin);

                    TextView expiry = tv("Code expires in 5 minutes · Existing phones remain paired", 12, MUTED);
                    expiry.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams ep = params(-1, -2);
                    ep.topMargin = dp(12);
                    page.addView(expiry, ep);

                    Button done = ghostBtn("Done");
                    LinearLayout.LayoutParams dpParams = params(-1, dp(50));
                    dpParams.topMargin = dp(24);
                    page.addView(done, dpParams);
                    done.setOnClickListener(v -> showExistingTv(savedId, savedToken));
                });
            } catch (Exception e) {
                String message = e.getMessage() == null
                        ? "Could not generate pairing code." : e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private void showRemotePair() {
        basePage();
        screenHeader("Connect", "Enter your PIN",
                "Type the six-digit code shown on your television.");

        LinearLayout inputCard = glassCard(dp(20));
        inputCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams icp = params(-1, -2);
        icp.topMargin = dp(28);
        page.addView(inputCard, icp);

        EditText pin = new EditText(this);
        pin.setBackgroundColor(Color.TRANSPARENT);
        pin.setSingleLine(true);
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(TEXT);
        pin.setHintTextColor(GHOST);
        pin.setHint("0 0 0 0 0 0");
        pin.setTextSize(36);
        pin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);
        pin.setLetterSpacing(0.20f);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        inputCard.addView(pin, params(-1, dp(80)));

        status = tv("", 13, RED);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = params(-1, -2);
        stp.topMargin = dp(12);
        page.addView(status, stp);

        Button connect = pillButton("Connect to TV", true);
        LinearLayout.LayoutParams bp = params(-1, dp(56));
        bp.topMargin = dp(16);
        page.addView(connect, bp);

        connect.setOnClickListener(v -> {
            String code = pin.getText().toString().trim();
            if (!code.matches("\\d{6}")) { status.setText("Need all six digits."); return; }
            connect.setEnabled(false);
            connect.setText("Pairing…");
            status.setText("");
            worker.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("code", code);
                    JSONObject result = RemoteApi.post("/tv/pair/claim", body, null);
                    deviceId    = result.getString("device_id");
                    remoteToken = result.getString("remote_token");
                    getSharedPreferences("remote_settings", MODE_PRIVATE).edit()
                            .putString("device_id", deviceId)
                            .putString("remote_token", remoteToken)
                            .apply();
                    runOnUiThread(this::showRemote);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        connect.setText("Connect to TV");
                        status.setText(e.getMessage());
                    });
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void showRemote() {
        RemoteApi.setStatusListener(new RemoteApi.StatusListener() {
            @Override public void onTvState(int volume, int maxVolume, boolean muted) {
                runOnUiThread(() -> {
                    if (volumeReadout != null)
                        volumeReadout.setText(volume + " / " + maxVolume);
                    if (volumeProgress != null) {
                        volumeProgress.setMax(maxVolume);
                        volumeProgress.setProgress(volume);
                    }
                    if (muteReadout != null) {
                        muteReadout.setText(muted ? "Muted" : "Live");
                        muteReadout.setTextColor(muted ? RED : TEAL);
                    }
                });
            }
            @Override public void onConnection(String message) {
                runOnUiThread(() -> { if (status != null) status.setText(message); });
            }
        });

        RemoteApi.connectRemote(deviceId, remoteToken);

        // ── Full-screen non-scrolling layout ──────────────────────────────────
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(16));

        root.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // ── Top bar: branding + status ────────────────────────────────────────
        LinearLayout topBar = hbox(Gravity.CENTER_VERTICAL);
        layout.addView(topBar, params(-1, dp(40)));

        // Branding pill
        LinearLayout brandPill = hbox(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(CARD);
        pillBg.setCornerRadius(dp(20));
        pillBg.setStroke(dp(1), BORDER);
        brandPill.setBackground(pillBg);
        brandPill.setPadding(dp(10), dp(5), dp(14), dp(5));

        View led = ovalView(dp(7), dp(7), TEAL);
        brandPill.addView(led, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView brandTv = tv("  CASSIM TV · REMOTE", 11, TEXT);
        brandTv.setLetterSpacing(0.14f);
        brandTv.setTypeface(Typeface.DEFAULT_BOLD);
        brandPill.addView(brandTv);
        topBar.addView(brandPill);

        topBar.addView(spacerH());

        status = tv("Connecting…", 11, MUTED);
        topBar.addView(status);

        // ── Volume card ───────────────────────────────────────────────────────
        LinearLayout volCard = glassCard(dp(16));
        volCard.setOrientation(LinearLayout.HORIZONTAL);
        volCard.setGravity(Gravity.CENTER_VERTICAL);
        volCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams vcp = params(-1, -2);
        vcp.topMargin = dp(10);
        layout.addView(volCard, vcp);

        LinearLayout volLeft = vbox();
        TextView volLabel = tv("VOLUME", 9, MUTED);
        volLabel.setLetterSpacing(0.14f);
        volLeft.addView(volLabel);
        volumeReadout = tv("— / —", 22, TEXT);
        volumeReadout.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams vrp = params(-2, -2);
        vrp.topMargin = dp(2);
        volLeft.addView(volumeReadout, vrp);
        volCard.addView(volLeft, new LinearLayout.LayoutParams(0, -2, 1));

        muteReadout = tv("Live", 13, TEAL);
        muteReadout.setTypeface(Typeface.DEFAULT_BOLD);
        volCard.addView(muteReadout);

        // Volume track — inline, inside a container below the card
        LinearLayout volTrackWrap = new LinearLayout(this);
        volTrackWrap.setOrientation(LinearLayout.VERTICAL);
        volTrackWrap.setPadding(dp(16), dp(6), dp(16), dp(2));

        volumeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volumeProgress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(BLUE));
        volumeProgress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(GHOST));
        LinearLayout.LayoutParams prp = params(-1, dp(4));
        prp.topMargin = dp(0);
        volTrackWrap.addView(volumeProgress, prp);
        LinearLayout.LayoutParams twp = params(-1, -2);
        twp.topMargin = dp(0);
        layout.addView(volTrackWrap, twp);

        // ── D-pad card ────────────────────────────────────────────────────────
        LinearLayout dpadCard = glassCard(dp(22));
        dpadCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dcp = params(-1, 0);
        dcp.topMargin = dp(10);
        dcp.weight = 1.5f;
        layout.addView(dpadCard, dcp);

        // Up row
        LinearLayout upRow = hbox(Gravity.CENTER);
        dpadCard.addView(upRow, params(-1, dp(62)));
        upRow.addView(dpadBtn("▲", "DPAD_UP", false));

        // Middle row
        LinearLayout midRow = hbox(Gravity.CENTER);
        dpadCard.addView(midRow, params(-1, dp(72)));
        midRow.addView(dpadBtn("◀", "DPAD_LEFT", false));
        midRow.addView(spacerH2(dp(10)));
        midRow.addView(dpadBtn("OK", "OK", true));
        midRow.addView(spacerH2(dp(10)));
        midRow.addView(dpadBtn("▶", "DPAD_RIGHT", false));

        // Down row
        LinearLayout dnRow = hbox(Gravity.CENTER);
        dpadCard.addView(dnRow, params(-1, dp(62)));
        dnRow.addView(dpadBtn("▼", "DPAD_DOWN", false));

        // ── Quick actions row ─────────────────────────────────────────────────
        LinearLayout quickCard = glassCard(dp(18));
        quickCard.setOrientation(LinearLayout.HORIZONTAL);
        quickCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams qcp = params(-1, 0);
        qcp.topMargin = dp(10);
        qcp.weight = 0.6f;
        layout.addView(quickCard, qcp);

        quickCard.addView(actionBtn("Back",  "BACK",  false), new LinearLayout.LayoutParams(0, -1, 1));
        quickCard.addView(spacerH2(dp(8)));
        quickCard.addView(actionBtn("Home",  "HOME",  false), new LinearLayout.LayoutParams(0, -1, 1));
        quickCard.addView(spacerH2(dp(8)));
        quickCard.addView(actionBtn("Power", "POWER", false), new LinearLayout.LayoutParams(0, -1, 1));

        // ── Volume row ────────────────────────────────────────────────────────
        LinearLayout volRow = glassCard(dp(18));
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams vrp2 = params(-1, 0);
        vrp2.topMargin = dp(10);
        vrp2.weight = 0.6f;
        layout.addView(volRow, vrp2);

        volRow.addView(actionBtn("−",    "VOLUME_DOWN", false), new LinearLayout.LayoutParams(0, -1, 1));
        volRow.addView(spacerH2(dp(8)));
        volRow.addView(actionBtn("Mute", "MUTE",        false), new LinearLayout.LayoutParams(0, -1, 1));
        volRow.addView(spacerH2(dp(8)));
        volRow.addView(actionBtn("+",    "VOLUME_UP",   false), new LinearLayout.LayoutParams(0, -1, 1));

        // ── Disconnect ────────────────────────────────────────────────────────
        Button unpair = ghostBtn("Disconnect this phone");
        LinearLayout.LayoutParams up2 = params(-1, dp(44));
        up2.topMargin = dp(10);
        layout.addView(unpair, up2);

        unpair.setOnClickListener(v -> {
            RemoteApi.setStatusListener(null);
            getSharedPreferences("remote_settings", MODE_PRIVATE).edit().clear().apply();
            deviceId    = null;
            remoteToken = null;
            showHome();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BUTTON FACTORIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Large circular D-pad button. */
    private View dpadBtn(String label, String action, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(primary ? 16 : 22);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);

        int size = primary ? dp(72) : dp(58);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(dp(10), 0, dp(10), 0);
        p.gravity = Gravity.CENTER_VERTICAL;

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (primary) {
            bg.setColor(BLUE);
        } else {
            bg.setColor(CARD2);
            bg.setStroke(dp(1), BORDER);
        }
        b.setBackground(bg);
        b.setLayoutParams(p);

        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v -> sendCommand(action));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)       b.setAlpha(0.55f);
            else if (e.getAction() == MotionEvent.ACTION_UP
                  || e.getAction() == MotionEvent.ACTION_CANCEL) b.setAlpha(1f);
            return false;
        });
        return b;
    }

    /** Rounded pill button for quick-action rows. */
    private View actionBtn(String label, String action, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? BLUE : CARD2);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), primary ? BLUE : BORDER);
        b.setBackground(bg);

        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v -> sendCommand(action));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)        b.setAlpha(0.5f);
            else if (e.getAction() == MotionEvent.ACTION_UP
                   || e.getAction() == MotionEvent.ACTION_CANCEL) b.setAlpha(1f);
            return false;
        });
        return b;
    }

    /** Filled or outlined CTA pill. */
    private Button pillButton(String text, boolean filled) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(filled ? Color.WHITE : BLUE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(filled ? BLUE : Color.TRANSPARENT);
        bg.setCornerRadius(dp(16));
        if (!filled) bg.setStroke(dp(1), BLUE);
        b.setBackground(bg);
        return b;
    }

    /** Ghost / destructive secondary button. */
    private Button ghostBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(MUTED);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), GHOST);
        b.setBackground(bg);
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LAYOUT HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void basePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(18), dp(24), dp(18), dp(28));
        scroll.addView(page);
        setContentView(scroll);
    }

    private void screenHeader(String eyebrow, String title, String subtitle) {
        TextView ew = tv(eyebrow.toUpperCase(), 10, MUTED);
        ew.setLetterSpacing(0.16f);
        page.addView(ew);

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(32);
        h.setTextColor(TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(6);
        page.addView(h, hp);

        TextView sub = tv(subtitle, 14, MUTED);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(5);
        page.addView(sub, sp);
    }

    private LinearLayout glassCard(int radius) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), BORDER);
        box.setBackground(bg);
        return box;
    }

    private View ovalView(int w, int h, int color) {
        View v = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    private void touchFeedback(View v, Runnable action) {
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)        view.setAlpha(0.72f);
            else if (e.getAction() == MotionEvent.ACTION_UP) {
                view.setAlpha(1f);
                action.run();
            } else if (e.getAction() == MotionEvent.ACTION_CANCEL) view.setAlpha(1f);
            return true;
        });
    }

    private LinearLayout hbox(int gravity) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView tv(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private View spacerV(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, h));
        return v;
    }

    private View spacerH() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        return v;
    }

    private View spacerH2(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private LinearLayout.LayoutParams params(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void sendCommand(String action) {
        if (deviceId == null || remoteToken == null) {
            if (status != null) status.setText("Pair with a TV first.");
            return;
        }
        RemoteApi.sendRemoteCommand(action);
        if (status != null) status.setText("sent · " + action.replace('_', ' ').toLowerCase());
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}