package com.floatsound.volume;

import android.content.Context;
import android.media.AudioManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Single volume tile: each tap steps media volume up and pops the native
 *  slider, where you can also drag down. */
public class VolumeTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        try {
            tile.setLabel("Vol");
            tile.setContentDescription("Volume up (native slider pops for fine control)");
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        } catch (Exception ignored) {}
    }

    @Override
    public void onClick() {
        super.onClick();
        try {
            AudioManager a = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (a != null) {
                a.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            }
        } catch (Exception ignored) {}
    }
}
