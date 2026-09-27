package com.floatsound.volume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)) {
            try {
                SharedPreferences p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
                boolean enabled = p.getBoolean(MainActivity.KEY_ENABLED, false);
                if (!enabled) return;
                if (!Settings.canDrawOverlays(context)) return;
                Intent i = new Intent(context, FloatingService.class);
                i.setAction(FloatingService.ACTION_START);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(i);
                } else {
                    context.startService(i);
                }
            } catch (Exception ignored) {}
        }
    }
}
