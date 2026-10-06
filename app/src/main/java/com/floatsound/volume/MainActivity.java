package com.floatsound.volume;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_NOTIF = 1002;

    private TextView statusView;

    public static final String PREFS = "floatsound";
    public static final String KEY_ENABLED = "enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Floating Sound");
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("Edge volume control — no DND, silent only mutes ring.");
        sub.setTextSize(14f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(12));
        root.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextSize(14f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(8), 0, dp(12));
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnOverlay = new Button(this);
        btnOverlay.setText("1. Allow display over other apps");
        btnOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestOverlay(); }
        });
        root.addView(btnOverlay, btnParams());

        Button btnDnd = new Button(this);
        btnDnd.setText("2. Allow Do Not Disturb access (needed for Silent)");
        btnDnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestDnd(); }
        });
        root.addView(btnDnd, btnParams());

        Button btnNotif = new Button(this);
        btnNotif.setText("3. Allow notifications (Android 13+)");
        btnNotif.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestNotif(); }
        });
        root.addView(btnNotif, btnParams());

        Button btnStart = new Button(this);
        btnStart.setText("Start floating control");
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startFloating(); }
        });
        root.addView(btnStart, btnParams());

        Button btnStop = new Button(this);
        btnStop.setText("Stop floating control");
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopFloating(); }
        });
        root.addView(btnStop, btnParams());

        Button btnTile = new Button(this);
        btnTile.setText("Add Quick Settings tile");
        btnTile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestTile(); }
        });
        root.addView(btnTile, btnParams());

        Button btnEdit = new Button(this);
        btnEdit.setText("Edit size & tap action");
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showEditDialog(); }
        });
        root.addView(btnEdit, btnParams());

        TextView hint = new TextView(this);
        hint.setText("How to use:\n• Dot sticks to LEFT or RIGHT edge only — drag it to move.\n• Tap dot: native popup + panel (change in Edit).\n• Edit: dot & panel size, tap action, hide dot while video plays.\n• QS tiles: Sound mode, Vol (add via QS Edit).\n• Tap outside panel to close; video behind stays visible.\n• Icons: Ring (full) / Vibrate / Silent (native, needs DND step 2).");
        hint.setTextSize(13f);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private LinearLayout.LayoutParams btnParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        return lp;
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean svc = isServiceEnabled();
        String s = "Overlay: " + (overlay ? "granted" : "NOT granted")
                + "\nDND access: " + (isDndGranted() ? "granted" : "NOT granted (needed for Silent)")
                + "\nFloating service: " + (svc ? "ON" : "OFF");
        statusView.setText(s);
    }

    private boolean isDndGranted() {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager)
                    getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationPolicyAccessGranted();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isServiceEnabled() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getBoolean(KEY_ENABLED, false);
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay already allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open overlay settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestDnd() {
        if (isDndGranted()) {
            Toast.makeText(this, "DND access already granted", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(i);
            Toast.makeText(this, "Enable DND access for Floating Sound", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open DND settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications already allowed", Toast.LENGTH_SHORT).show();
                return;
            }
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow overlay first", Toast.LENGTH_LONG).show();
            requestOverlay();
            return;
        }
        Intent i = new Intent(this, FloatingService.class);
        i.setAction(FloatingService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            Toast.makeText(this, "Floating control started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot start service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopFloating() {
        Intent i = new Intent(this, FloatingService.class);
        i.setAction(FloatingService.ACTION_STOP);
        try {
            startService(i);
        } catch (Exception e) {
            stopService(new Intent(this, FloatingService.class));
        }
        Toast.makeText(this, "Floating control stopped", Toast.LENGTH_SHORT).show();
    }

    private void requestTile() {
        Toast.makeText(this, "Pull down Quick Settings, tap Edit, add 'Sound mode'",
                Toast.LENGTH_LONG).show();
    }

    private void showEditDialog() {
        final SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int scale = p.getInt(FloatingService.KEY_SCALE, 100);
        int tap = p.getInt(FloatingService.KEY_TAP, 2);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(4));

        final TextView sizeLabel = new TextView(this);
        sizeLabel.setTextSize(14f);
        content.addView(sizeLabel);

        SeekBar sb = new SeekBar(this);
        sb.setMax(60); // 80%..140%
        sb.setProgress(Math.max(0, Math.min(60, scale - 80)));
        sizeLabel.setText("Dot & panel size: " + (sb.getProgress() + 80) + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                sizeLabel.setText("Dot & panel size: " + (progress + 80) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        content.addView(sb);

        TextView tapLabel = new TextView(this);
        tapLabel.setText("Tap floating dot:");
        tapLabel.setTextSize(14f);
        tapLabel.setPadding(0, dp(12), 0, dp(4));
        content.addView(tapLabel);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"Native volume popup", "My panel", "Both"};
        final int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setTextSize(15f);
            rb.setId(View.generateViewId());
            ids[i] = rb.getId();
            group.addView(rb);
        }
        group.check(ids[Math.max(0, Math.min(names.length - 1, tap))]);
        content.addView(group);

        final CheckBox hideBox = new CheckBox(this);
        hideBox.setText("Hide dot while video plays");
        hideBox.setTextSize(15f);
        hideBox.setChecked(p.getBoolean(FloatingService.KEY_HIDE_MEDIA, true));
        hideBox.setPadding(0, dp(8), 0, 0);
        content.addView(hideBox);

        final SeekBar fSb = sb;
        final RadioGroup fGroup = group;
        final int[] fIds = ids;
        new AlertDialog.Builder(this)
                .setTitle("Edit floating control")
                .setView(content)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int newScale = fSb.getProgress() + 80;
                        int checked = fGroup.getCheckedRadioButtonId();
                        int newTap = 2;
                        for (int i = 0; i < fIds.length; i++) {
                            if (fIds[i] == checked) newTap = i;
                        }
                        p.edit().putInt(FloatingService.KEY_SCALE, newScale)
                                .putInt(FloatingService.KEY_TAP, newTap)
                                .putBoolean(FloatingService.KEY_HIDE_MEDIA, hideBox.isChecked())
                                .apply();
                        if (isServiceEnabled()) {
                            try {
                                startService(new Intent(MainActivity.this, FloatingService.class)
                                        .setAction(FloatingService.ACTION_REBUILD));
                            } catch (Exception ignored) {}
                        }
                        Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            refreshStatus();
        }
    }
}
