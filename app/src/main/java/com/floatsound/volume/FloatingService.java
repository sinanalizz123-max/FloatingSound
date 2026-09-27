package com.floatsound.volume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingService extends Service {

    public static final String ACTION_START = "com.floatsound.volume.START";
    public static final String ACTION_STOP = "com.floatsound.volume.STOP";
    private static final String CHANNEL_ID = "float_ctl";
    private static final int NOTIF_ID = 1001;

    private static final String KEY_LAST_RING = "last_ring_vol";
    private static final String KEY_DOCK_RIGHT = "dock_right";

    private WindowManager wm;
    private AudioManager audio;
    private FrameLayout dotView;
    private View expandRoot;
    private WindowManager.LayoutParams dotParams;
    private WindowManager.LayoutParams expandParams;
    private boolean expanded = false;

    private boolean dockRight = true;
    private boolean wantSilent = false;
    private int lastDotY = 400;

    private SeekBar sbMusic, sbRing, sbAlarm, sbCall;
    private Button btnRing, btnVibrate, btnSilent;
    private TextView tvMusicVal, tvRingVal, tvAlarmVal, tvCallVal;
    private boolean updatingSliders = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver volReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshPanel();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createChannel();
        IntentFilter f = new IntentFilter();
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(volReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(volReceiver, f);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        setEnabled(true);
        startAsForeground();
        ensureDot();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(volReceiver); } catch (Exception ignored) {}
        handler.removeCallbacksAndMessages(null);
        removeDot();
        removeExpanded();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void setEnabled(boolean on) {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit().putBoolean(MainActivity.KEY_ENABLED, on).apply();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID, getString(R.string.notif_channel_name),
                            NotificationManager.IMPORTANCE_MIN);
                    ch.setDescription("Keeps floating volume control alive");
                    nm.createNotificationChannel(ch);
                }
            } catch (Exception ignored) {}
        }
    }

    private void startAsForeground() {
        try {
            Intent open = new Intent(this, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent stop = new Intent(this, FloatingService.class);
            stop.setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
            else b = new Notification.Builder(this);

            b.setContentTitle(getString(R.string.notif_title))
                    .setContentText(getString(R.string.notif_text))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build());

            Notification n = b.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            try {
                Notification n;
                if (Build.VERSION.SDK_INT >= 26) {
                    n = new Notification.Builder(this, CHANNEL_ID)
                            .setContentTitle(getString(R.string.notif_title))
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .build();
                } else {
                    n = new Notification.Builder(this)
                            .setContentTitle(getString(R.string.notif_title))
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .build();
                }
                startForeground(NOTIF_ID, n);
            } catch (Exception ignored) {}
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int screenW() {
        try { return getResources().getDisplayMetrics().widthPixels; }
        catch (Exception e) { return 1080; }
    }

    private int screenH() {
        try { return getResources().getDisplayMetrics().heightPixels; }
        catch (Exception e) { return 1920; }
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    private GradientDrawable rounded(int fill, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radiusDp));
        d.setColor(fill);
        return d;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
    }

    private int currentMode() {
        try {
            int rm = audio.getRingerMode();
            if (rm == AudioManager.RINGER_MODE_SILENT) return 2;
            if (rm == AudioManager.RINGER_MODE_VIBRATE) return 1;
            if (audio.isStreamMute(AudioManager.STREAM_RING)) return 2;
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void buzz(long ms) {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    private void askForDnd() {
        Toast.makeText(this, "Allow Notification Policy Access to change Silent mode", Toast.LENGTH_LONG).show();
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void setMode(int mode) {
        try {
            if (mode == AudioManager.RINGER_MODE_NORMAL) {
                wantSilent = false;
                audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_RING);
                audio.setStreamVolume(AudioManager.STREAM_RING, max, 0);
                Toast.makeText(this, "Ring · full " + max + "/" + max, Toast.LENGTH_SHORT).show();
            } else if (mode == AudioManager.RINGER_MODE_VIBRATE) {
                wantSilent = false;
                audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                buzz(40);
                Toast.makeText(this, "Vibrate", Toast.LENGTH_SHORT).show();
            } else {
                // Silent = mute the ring stream only: calls stay quiet,
                // media/alarm/call keep working. DND is a separate function
                // and is never touched here.
                try { audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL); }
                catch (SecurityException se) { askForDnd(); }
                catch (Exception ignored) {}
                try {
                    audio.adjustStreamVolume(AudioManager.STREAM_RING,
                            AudioManager.ADJUST_MUTE, 0);
                } catch (Exception ignored) {}
                wantSilent = true;
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (!wantSilent) return;
                        try {
                            // Some ROMs flip a muted ring into vibrate mode; keep it quiet NORMAL.
                            if (audio.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                                audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                            }
                            if (!audio.isStreamMute(AudioManager.STREAM_RING)) {
                                audio.adjustStreamVolume(AudioManager.STREAM_RING,
                                        AudioManager.ADJUST_MUTE, 0);
                            }
                        } catch (Exception ignored) {}
                        refreshPanel();
                    }
                }, 350);
                Toast.makeText(this, "Silent · calls muted", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException se) {
            askForDnd();
        } catch (Exception ignored) {}
        refreshPanel();
        handler.postDelayed(new Runnable() {
            @Override public void run() { refreshPanel(); }
        }, 700);
    }

    private void ensureDot() {
        if (expanded) return;
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        if (dotView != null) return;
        dockRight = prefs().getBoolean(KEY_DOCK_RIGHT, true);
        buildDot();
        try { wm.addView(dotView, dotParams); }
        catch (Exception e) { dotView = null; }
    }

    private void buildDot() {
        dotView = new FrameLayout(this);
        int dotSize = dp(48);
        TextView tv = new TextView(this);
        tv.setText("\u266A");
        tv.setTextSize(24f);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        dotView.addView(tv, new FrameLayout.LayoutParams(dotSize, dotSize));
        dotView.setBackground(circle(0xFF4F46E5));

        int y = prefs().getInt("dot_y", 400);
        y = Math.max(0, Math.min(y, screenH() - dp(120)));

        dotParams = new WindowManager.LayoutParams(
                dotSize, dotSize, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        dotParams.gravity = Gravity.TOP | Gravity.START;
        dotParams.x = dockRight ? Math.max(0, screenW() - dotSize) : 0;
        dotParams.y = y;

        final int[] start = new int[2];
        final float[] touch = new float[2];
        final boolean[] dragging = new boolean[1];
        final int slop = dp(10);

        dotView.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        start[0] = dotParams.x;
                        start[1] = dotParams.y;
                        touch[0] = e.getRawX();
                        touch[1] = e.getRawY();
                        dragging[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (e.getRawX() - touch[0]);
                        int dy = (int) (e.getRawY() - touch[1]);
                        if (!dragging[0] && (Math.abs(dx) > slop || Math.abs(dy) > slop)) dragging[0] = true;
                        if (dragging[0]) {
                            dotParams.x = start[0] + dx;
                            dotParams.y = Math.max(0, Math.min(start[1] + dy, screenH() - dp(120)));
                            try { wm.updateViewLayout(dotView, dotParams); } catch (Exception ignored) {}
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (dragging[0]) {
                            int centerX = dotParams.x + dotParams.width / 2;
                            dockRight = centerX >= screenW() / 2;
                            dotParams.x = dockRight ? Math.max(0, screenW() - dotParams.width) : 0;
                            dotParams.y = Math.max(0, Math.min(dotParams.y, screenH() - dp(120)));
                            prefs().edit().putBoolean(KEY_DOCK_RIGHT, dockRight).putInt("dot_y", dotParams.y).apply();
                            try { wm.updateViewLayout(dotView, dotParams); } catch (Exception ignored) {}
                        } else {
                            v.performClick();
                            showExpanded();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void removeDot() {
        if (dotView != null) {
            try { wm.removeView(dotView); } catch (Exception ignored) {}
            dotView = null;
        }
    }

    private void showExpanded() {
        if (expanded) return;
        if (!Settings.canDrawOverlays(this)) return;
        expanded = true;
        if (dotParams != null) lastDotY = dotParams.y;
        removeDot();
        buildExpanded();
        try {
            wm.addView(expandRoot, expandParams);
            refreshPanel();
        } catch (Exception e) {
            expanded = false;
            expandRoot = null;
            ensureDot();
        }
    }

    private void collapse() {
        if (!expanded) return;
        expanded = false;
        removeExpanded();
        ensureDot();
    }

    private void removeExpanded() {
        if (expandRoot != null) {
            try { wm.removeView(expandRoot); } catch (Exception ignored) {}
            expandRoot = null;
            sbMusic = sbRing = sbAlarm = sbCall = null;
            btnRing = btnVibrate = btnSilent = null;
        }
    }

    private void buildExpanded() {
        // Build the card first, then measure its real content size for accurate positioning.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x00000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { collapse(); }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(0xD91E1E28, 16));
        card.setPadding(dp(7), dp(6), dp(7), dp(7));
        card.setElevation(dp(6));
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {} });
        card.setClickable(true);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("\u266A Volume");
        title.setTextSize(12f);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextSize(13f);
        close.setTextColor(0xFFB0B0B8);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(6), dp(2), dp(6), dp(2));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { collapse(); } });
        titleRow.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(titleRow);

        LinearLayout sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER);
        sliderRow.setPadding(0, dp(2), 0, 0);
        card.addView(sliderRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sbMusic = addSliderColumn(sliderRow, AudioManager.STREAM_MUSIC, android.R.drawable.ic_media_play);
        sbCall = addSliderColumn(sliderRow, AudioManager.STREAM_VOICE_CALL, android.R.drawable.ic_menu_call);
        sbRing = addSliderColumn(sliderRow, AudioManager.STREAM_RING, android.R.drawable.ic_lock_silent_mode_off);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Sound mode");
        modeLabel.setTextSize(10f);
        modeLabel.setTextColor(0xFFB0B0B8);
        modeLabel.setPadding(0, dp(3), 0, dp(2));
        card.addView(modeLabel);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);

        btnRing = modeButton("\uD83D\uDD0A");
        btnRing.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(AudioManager.RINGER_MODE_NORMAL); } });
        btnVibrate = modeButton("\uD83D\uDCF3");
        btnVibrate.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(AudioManager.RINGER_MODE_VIBRATE); } });
        btnSilent = modeButton("\uD83D\uDD07");
        btnSilent.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(AudioManager.RINGER_MODE_SILENT); } });

        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        mLp.setMargins(dp(1), 0, dp(1), 0);
        modes.addView(btnRing, mLp);
        modes.addView(btnVibrate, new LinearLayout.LayoutParams(mLp));
        modes.addView(btnSilent, new LinearLayout.LayoutParams(mLp));
        card.addView(modes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Measure AFTER all content is added, so right-side anchoring uses the real width.
        card.measure(
                View.MeasureSpec.makeMeasureSpec(screenW(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenH(), View.MeasureSpec.AT_MOST));
        int panelWidth = Math.max(card.getMeasuredWidth(), dp(120));
        int panelHeight = Math.max(card.getMeasuredHeight(), dp(120));

        int px = dockRight
                ? screenW() - dp(48) - panelWidth - dp(6)
                : dp(48) + dp(6);
        px = Math.max(dp(2), Math.min(px, screenW() - panelWidth - dp(2)));

        int py = (lastDotY + dp(24)) - panelHeight / 2;
        py = Math.max(dp(24), Math.min(py, screenH() - panelHeight - dp(48)));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        cardLp.leftMargin = px;
        cardLp.topMargin = py;
        root.addView(card, cardLp);

        expandRoot = root;
        expandParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        expandParams.gravity = Gravity.TOP | Gravity.START;
    }

    private Button modeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17f);
        b.setMinimumHeight(dp(34));
        b.setMinHeight(dp(34));
        b.setPadding(dp(2), dp(2), dp(2), dp(2));
        return b;
    }

    private SeekBar addSliderColumn(LinearLayout row, final int stream, int iconRes) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        row.addView(col, new LinearLayout.LayoutParams(dp(43), ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.bottomMargin = dp(1);
        col.addView(icon, iconLp);

        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        col.addView(box, new LinearLayout.LayoutParams(dp(43), dp(108)));

        SeekBar sb = new SeekBar(this);
        try {
            int max = audio.getStreamMaxVolume(stream);
            sb.setMax(Math.max(1, max));
            sb.setProgress(audio.getStreamVolume(stream));
        } catch (Exception ignored) {}
        sb.setRotation(-90);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && !updatingSliders) {
                    try { audio.setStreamVolume(stream, progress, 0); } catch (Exception ignored) {}
                    handler.post(new Runnable() { @Override public void run() { refreshPanel(); } });
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(sb, new FrameLayout.LayoutParams(dp(100), dp(34), Gravity.CENTER));

        TextView val = new TextView(this);
        val.setTextSize(9f);
        val.setTextColor(0xFFB0B0B8);
        val.setGravity(Gravity.CENTER);
        col.addView(val, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (stream == AudioManager.STREAM_MUSIC) tvMusicVal = val;
        else if (stream == AudioManager.STREAM_RING) tvRingVal = val;
        else tvCallVal = val;
        return sb;
    }

    private void refreshPanel() {
        if (!expanded) return;
        updatingSliders = true;
        try {
            updateSlider(sbMusic, tvMusicVal, AudioManager.STREAM_MUSIC);
            updateSlider(sbCall, tvCallVal, AudioManager.STREAM_VOICE_CALL);
            updateSlider(sbRing, tvRingVal, AudioManager.STREAM_RING);
            highlightModes();
        } catch (Exception ignored) {
        } finally {
            updatingSliders = false;
        }
    }

    private void updateSlider(SeekBar sb, TextView label, int stream) {
        if (sb == null) return;
        int cur = 0, max = 1;
        try {
            cur = audio.getStreamVolume(stream);
            max = audio.getStreamMaxVolume(stream);
        } catch (Exception ignored) {}
        sb.setMax(Math.max(1, max));
        if (sb.getProgress() != cur) sb.setProgress(cur);
        if (label != null) label.setText(cur + "/" + max);
    }

    private void highlightModes() {
        int m = currentMode();
        styleMode(btnRing, m == 0);
        styleMode(btnVibrate, m == 1);
        styleMode(btnSilent, m == 2);
    }

    private void styleMode(Button b, boolean active) {
        if (b == null) return;
        if (active) {
            b.setBackground(rounded(0xFF4F46E5, 9));
            b.setTextColor(0xFFFFFFFF);
        } else {
            b.setBackground(rounded(0x33FFFFFF, 9));
            b.setTextColor(0xFFFFFFFF);
        }
    }
}