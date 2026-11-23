package com.cassimtv.remote;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
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

    // ── Palette ──────────────────────────────────────────────────────────
    private static final int BG        = Color.rgb(6,  10,  20);    // near-black base
    private static final int SURFACE   = Color.rgb(14, 22,  40);    // slightly lifted
    private static final int CARD      = Color.rgb(22, 35,  65);    // glass card
    private static final int CARD_EDGE = Color.rgb(38, 60, 110);    // card border highlight
    private static final int BLUE      = Color.rgb(61, 123, 255);   // primary accent
    private static final int BLUE_DIM  = Color.rgb(29,  60, 130);   // pressed / secondary
    private static final int TEAL      = Color.rgb(77, 255, 195);   // positive state
    private static final int TEXT      = Color.rgb(240, 245, 255);  // primary text
    private static final int MUTED     = Color.rgb(120, 145, 190);  // secondary text
    private static final int GHOST     = Color.rgb(55,  80, 125);   // disabled / ghost

    // ── State ─────────────────────────────────────────────────────────────
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout  page;
    private TextView      status;
    private TextView      volumeReadout;
    private TextView      muteReadout;
    private ProgressBar   volumeProgress;
    private String        deviceId;
    private String        remoteToken;
    private String        pairingCode;

    // ─────────────────────────────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════════
    //  SCREENS
    // ══════════════════════════════════════════════════════════════════════

    private void showHome() {
        basePage();

        // ── Logo / wordmark ──────────────────────────────────────────────
        LinearLayout logoRow = hbox(Gravity.CENTER);
        LinearLayout.LayoutParams lp = params(-2, -2);
        lp.topMargin = dp(16);
        page.addView(logoRow, lp);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(BLUE);
        dot.setBackground(dotBg);
        logoRow.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView wordmark = label("CASSIM TV", 11, BLUE);
        wordmark.setLetterSpacing(0.22f);
        LinearLayout.LayoutParams wp = params(-2, -2);
        wp.leftMargin = dp(8);
        logoRow.addView(wordmark, wp);

        // ── Hero text ────────────────────────────────────────────────────
        TextView hero = new TextView(this);
        hero.setText("One app.\nTwo roles.");
        hero.setTextSize(38);
        hero.setTextColor(TEXT);
        hero.setTypeface(Typeface.DEFAULT_BOLD);
        hero.setGravity(Gravity.CENTER);
        hero.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(22);
        page.addView(hero, hp);

        TextView sub = label("Set this device as a TV receiver\nor use it as a wireless remote.", 14, MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(10);
        page.addView(sub, sp);

        // ── Role cards ───────────────────────────────────────────────────
        View spacer = new View(this);
        page.addView(spacer, params(1, dp(32)));

        addRoleCard(
            "Television",
            "Display a pairing code and accept remote commands from your phone.",
            "▣",
            () -> {
                android.content.SharedPreferences p =
                        getSharedPreferences("tv_settings", MODE_PRIVATE);
                String id  = p.getString("device_id", null);
                String tok = p.getString("receiver_token", null);
                if (id != null && tok != null) showExistingTv(id, tok);
                else createPairing();
            }
        );

        addRoleCard(
            "Remote",
            "Enter the pairing code shown on your television.",
            "◉",
            this::showRemotePair
        );
    }

    // ─────────────────────────────────────────────────────────────────────

    private void addRoleCard(String title, String desc, String icon, Runnable click) {
        LinearLayout card = glassCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout.LayoutParams cp = params(-1, -2);
        cp.topMargin = dp(14);
        page.addView(card, cp);

        // Icon badge
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(BLUE_DIM);
        badge.setBackground(badgeBg);
        badge.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(22);
        iconView.setTextColor(BLUE);
        iconView.setGravity(Gravity.CENTER);
        badge.addView(iconView, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        card.addView(badge, new LinearLayout.LayoutParams(dp(60), dp(60)));

        // Text block
        LinearLayout textBlock = vbox();
        LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(0, -2, 1);
        tbp.leftMargin = dp(16);

        TextView titleView = label(title, 17, TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        textBlock.addView(titleView);

        TextView descView = label(desc, 13, MUTED);
        descView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dvp = params(-1, -2);
        dvp.topMargin = dp(4);
        textBlock.addView(descView, dvp);

        card.addView(textBlock, tbp);

        // Chevron
        TextView chev = label("›", 26, GHOST);
        chev.setPadding(dp(10), 0, 0, 0);
        card.addView(chev);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> click.run());

        // Pressed state
        card.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN)
                card.setAlpha(0.75f);
            else if (e.getAction() == android.view.MotionEvent.ACTION_UP
                  || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                card.setAlpha(1f);
                if (e.getAction() == android.view.MotionEvent.ACTION_UP)
                    click.run();
            }
            return true;
        });
        card.setClickable(false); // handled by touch
    }

    // ─────────────────────────────────────────────────────────────────────

    private void createPairing() {
        basePage();
        screenHeader("Television", "Waiting for remote…",
                "Show this code to your phone app to pair.");

        status = label("Reaching Cassim relay…", 13, MUTED);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(24);
        page.addView(status, sp);

        worker.execute(() -> {
            try {
                JSONObject result = RemoteApi.post(
                        "/tv/pair/start", new JSONObject(), null);

                deviceId    = result.getString("device_id");
                pairingCode = result.getString("pairing_code");
                String receiverToken = result.getString("receiver_token");

                getSharedPreferences("tv_settings", MODE_PRIVATE).edit()
                        .putString("device_id", deviceId)
                        .putString("receiver_token", receiverToken)
                        .apply();

                runOnUiThread(() -> {
                    status.setText("Enter this code on your phone.");

                    // ── PIN card ─────────────────────────────────────────
                    LinearLayout pinCard = glassCard();
                    pinCard.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams pcp = params(-1, dp(140));
                    pcp.topMargin = dp(20);
                    page.addView(pinCard, pcp);

                    TextView pinLabel = label("PAIRING CODE", 10, MUTED);
                    pinLabel.setLetterSpacing(0.14f);
                    pinCard.addView(pinLabel);

                    TextView pinView = new TextView(this);
                    pinView.setText(pairingCode);
                    pinView.setTextSize(52);
                    pinView.setTextColor(TEAL);
                    pinView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    pinView.setGravity(Gravity.CENTER);
                    pinView.setLetterSpacing(0.14f);
                    LinearLayout.LayoutParams pvp = params(-1, -2);
                    pvp.topMargin = dp(6);
                    pinCard.addView(pinView, pvp);

                    // ── Accessibility button ──────────────────────────────
                    Button access = accentButton("Enable TV Navigation", false);
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

    // ─────────────────────────────────────────────────────────────────────

    private void showRemotePair() {
        basePage();
        screenHeader("Connect", "Enter your PIN",
                "Type the six-digit code shown on your television.");

        // ── PIN input ────────────────────────────────────────────────────
        LinearLayout inputCard = glassCard();
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
        pin.setLetterSpacing(0.22f);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        inputCard.addView(pin, params(-1, dp(80)));

        status = label("", 13, Color.rgb(255, 90, 90));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = params(-1, -2);
        stp.topMargin = dp(12);
        page.addView(status, stp);

        Button connect = accentButton("Connect to TV", true);
        LinearLayout.LayoutParams bp = params(-1, dp(56));
        bp.topMargin = dp(16);
        page.addView(connect, bp);

        connect.setOnClickListener(v -> {
            String code = pin.getText().toString().trim();
            if (!code.matches("\\d{6}")) {
                status.setText("Need all six digits.");
                return;
            }
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

    // ─────────────────────────────────────────────────────────────────────

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
                        muteReadout.setTextColor(muted ? Color.rgb(255, 90, 90) : TEAL);
                    }
                });
            }
            @Override public void onConnection(String message) {
                runOnUiThread(() -> { if (status != null) status.setText(message); });
            }
        });

        RemoteApi.connectRemote(deviceId, remoteToken);
        basePage();

        // ── Header strip ─────────────────────────────────────────────────
        LinearLayout header = hbox(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hdp = params(-1, dp(44));
        hdp.topMargin = dp(8);
        page.addView(header, hdp);

        View led = new View(this);
        GradientDrawable ledBg = new GradientDrawable();
        ledBg.setShape(GradientDrawable.OVAL);
        ledBg.setColor(TEAL);
        led.setBackground(ledBg);
        header.addView(led, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView tvLabel = label("  Your Television", 15, TEXT);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1));

        status = label("Connecting…", 11, MUTED);
        header.addView(status);

        // ── Volume strip ─────────────────────────────────────────────────
        LinearLayout volCard = glassCard();
        volCard.setOrientation(LinearLayout.HORIZONTAL);
        volCard.setGravity(Gravity.CENTER_VERTICAL);
        volCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams vcp = params(-1, -2);
        vcp.topMargin = dp(14);
        page.addView(volCard, vcp);

        LinearLayout volText = vbox();
        TextView volTitle = label("Volume", 11, MUTED);
        volText.addView(volTitle);
        volumeReadout = label("— / —", 20, TEXT);
        volumeReadout.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        volText.addView(volumeReadout);
        volCard.addView(volText, new LinearLayout.LayoutParams(0, -2, 1));

        muteReadout = label("Live", 13, TEAL);
        muteReadout.setTypeface(Typeface.DEFAULT_BOLD);
        volCard.addView(muteReadout);

        volumeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volumeProgress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams prp = params(-1, dp(3));
        prp.topMargin = dp(8);
        page.addView(volumeProgress, prp);

        // ── D-pad ────────────────────────────────────────────────────────
        LinearLayout dpadCard = glassCard();
        dpadCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dcp = params(-1, -2);
        dcp.topMargin = dp(16);
        page.addView(dpadCard, dcp);

        LinearLayout up = row();
        dpadCard.addView(up, params(-1, dp(60)));
        spacer(up);
        addDpadButton(up, "▲", "DPAD_UP", false);
        spacer(up);

        LinearLayout mid = row();
        dpadCard.addView(mid, params(-1, dp(72)));
        addDpadButton(mid, "◀", "DPAD_LEFT", false);
        addDpadButton(mid, "OK",  "OK",        true);
        addDpadButton(mid, "▶", "DPAD_RIGHT", false);

        LinearLayout dn = row();
        dpadCard.addView(dn, params(-1, dp(60)));
        spacer(dn);
        addDpadButton(dn, "▼", "DPAD_DOWN", false);
        spacer(dn);

        // ── Quick actions ────────────────────────────────────────────────
        LinearLayout quick = glassCard();
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams qp = params(-1, dp(62));
        qp.topMargin = dp(14);
        page.addView(quick, qp);

        addActionButton(quick, "Back",  "BACK",  false);
        addActionButton(quick, "Home",  "HOME",  false);
        addActionButton(quick, "⏻",    "POWER", false);

        // ── Volume row ───────────────────────────────────────────────────
        LinearLayout volRow = glassCard();
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams vrp = params(-1, dp(62));
        vrp.topMargin = dp(10);
        page.addView(volRow, vrp);

        addActionButton(volRow, "−",    "VOLUME_DOWN", false);
        addActionButton(volRow, "Mute", "MUTE",        false);
        addActionButton(volRow, "+",    "VOLUME_UP",   false);

        // ── Disconnect ───────────────────────────────────────────────────
        Button unpair = ghostButton("Disconnect this phone");
        LinearLayout.LayoutParams up2 = params(-1, dp(48));
        up2.topMargin = dp(24);
        up2.bottomMargin = dp(8);
        page.addView(unpair, up2);

        unpair.setOnClickListener(v -> {
            RemoteApi.setStatusListener(null);
            getSharedPreferences("remote_settings", MODE_PRIVATE)
                    .edit().clear().apply();
            deviceId    = null;
            remoteToken = null;
            showHome();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI COMPONENTS
    // ══════════════════════════════════════════════════════════════════════

    /** Full-screen scroll shell. Sets contentView. */
    private void basePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(20), dp(28), dp(20), dp(32));

        scroll.addView(page);
        setContentView(scroll);
    }

    /** Screen-level header block (eyebrow, big title, subtitle). */
    private void screenHeader(String eyebrow, String title, String subtitle) {
        TextView ew = label(eyebrow.toUpperCase(), 10, MUTED);
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

        TextView sub = label(subtitle, 14, MUTED);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(6);
        page.addView(sub, sp);
    }

    /** Frosted-glass card container. */
    private LinearLayout glassCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), CARD_EDGE);
        box.setBackground(bg);

        return box;
    }

    /** D-pad directional / OK button. */
    private void addDpadButton(LinearLayout row, String label,
                               String action, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(primary ? 15 : 20);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable bg = new GradientDrawable();
        if (primary) {
            bg.setColor(BLUE);
            bg.setShape(GradientDrawable.OVAL);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setShape(GradientDrawable.OVAL);
            bg.setStroke(dp(1), CARD_EDGE);
        }
        b.setBackground(bg);
        b.setPadding(0, 0, 0, 0);

        int size = primary ? dp(68) : dp(54);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(dp(6), 0, dp(6), 0);
        p.gravity = Gravity.CENTER_VERTICAL;
        row.addView(b, p);

        b.setOnClickListener(v -> sendCommand(action));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN)
                b.setAlpha(0.6f);
            else b.setAlpha(1f);
            return false;
        });
    }

    /** Pill action button (Back / Home / Vol / Mute). */
    private void addActionButton(LinearLayout row, String label,
                                 String action, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? BLUE : Color.argb(80, 61, 123, 255));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), primary ? BLUE : CARD_EDGE);
        b.setBackground(bg);
        b.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        row.addView(b, p);

        b.setOnClickListener(v -> sendCommand(action));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN)
                b.setAlpha(0.55f);
            else b.setAlpha(1f);
            return false;
        });
    }

    /** Filled primary CTA button. */
    private Button accentButton(String text, boolean filled) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(filled ? Color.WHITE : BLUE);
        b.setAllCaps(false);
        b.setSingleLine(true);
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
    private Button ghostButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(MUTED);
        b.setAllCaps(false);
        b.setSingleLine(true);
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

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void sendCommand(String action) {
        if (deviceId == null || remoteToken == null) {
            if (status != null) status.setText("Pair with a TV first.");
            return;
        }
        RemoteApi.sendRemoteCommand(action);
        if (status != null) status.setText("↑ " + action.replace('_', ' ').toLowerCase());
    }

    private TextView label(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
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

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        return l;
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

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}