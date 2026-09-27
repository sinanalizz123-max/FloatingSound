package com.floatsound.volume;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class SoundTileService extends TileService {

    private AudioManager audio() {
        return (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (Build.VERSION.SDK_INT >= 24 && isLocked()) {
            unlockAndRun(new Runnable() {
                @Override public void run() { cycleMode(); }
            });
        } else {
            cycleMode();
        }
    }

    /** 0=ring, 1=vibrate, 2=silent (native ringer modes; muted ring also reads as silent) */
    private int currentMode() {
        try {
            AudioManager a = audio();
            if (a == null) return 0;
            int rm = a.getRingerMode();
            if (rm == AudioManager.RINGER_MODE_SILENT) return 2;
            if (rm == AudioManager.RINGER_MODE_VIBRATE) return 1;
            if (a.isStreamMute(AudioManager.STREAM_RING)) return 2;
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void setRing() {
        try {
            AudioManager a = audio();
            if (a == null) return;
            // Ring = unmute + FULL ring volume, always.
            try { a.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0); } catch (Exception ignored) {}
            a.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            int max = a.getStreamMaxVolume(AudioManager.STREAM_RING);
            a.setStreamVolume(AudioManager.STREAM_RING, max, 0);
            Toast.makeText(this, "Ring · full", Toast.LENGTH_SHORT).show();
        } catch (SecurityException se) {
            Toast.makeText(this, "Allow DND access in the Floating Sound app", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private void setSilentNoDnd() {
        try {
            AudioManager a = audio();
            if (a == null) return;
            // Silent = system native silent (no sound AND no vibration, needs DND access).
            a.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Toast.makeText(this, "Silent", Toast.LENGTH_SHORT).show();
        } catch (SecurityException se) {
            Toast.makeText(this, "Allow DND access in the Floating Sound app", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private void cycleMode() {
        try {
            AudioManager a = audio();
            if (a == null) return;
            int cur = currentMode();
            if (cur == 0) {
                try { a.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0); } catch (Exception ignored) {}
                try {
                    a.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    Toast.makeText(this, "Vibrate", Toast.LENGTH_SHORT).show();
                } catch (SecurityException se) {
                    Toast.makeText(this, "Allow DND access in the Floating Sound app", Toast.LENGTH_LONG).show();
                }
            } else if (cur == 1) {
                setSilentNoDnd();
            } else {
                setRing();
            }
        } catch (Exception ignored) {
        } finally {
            updateTile();
            // Late refresh: system applies mode async.
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { updateTile(); }
            }, 700);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        int mode = currentMode();

        String label;
        int state;
        if (mode == 2) {
            label = "Silent";
            state = Tile.STATE_INACTIVE;
        } else if (mode == 1) {
            label = "Vibrate";
            state = Tile.STATE_ACTIVE;
        } else {
            label = "Ring";
            state = Tile.STATE_ACTIVE;
        }
        try {
            tile.setLabel(label);
            tile.setContentDescription("Sound mode: " + label + " — tap to switch");
            tile.setState(state);
            tile.updateTile();
        } catch (Exception ignored) {}
    }
}
