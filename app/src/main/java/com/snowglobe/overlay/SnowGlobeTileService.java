package com.snowglobe.overlay;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/** User-added Quick Settings toggle. Never adds itself, bypasses unlock, or changes wallpaper. */
public final class SnowGlobeTileService extends TileService {
    private SharedPreferences prefs;
    private boolean listening;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::updateState;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (p, key) -> {
        if ("effectsEnabled".equals(key) || "_serviceState".equals(key)) {
            main.removeCallbacks(refresh);
            main.post(refresh);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("snowglobe", MODE_PRIVATE);
    }
    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            listening = true;
            prefs.registerOnSharedPreferenceChangeListener(changes);
        }
        updateState();
    }
    @Override public void onStopListening() {
        listening = false;
        prefs.unregisterOnSharedPreferenceChangeListener(changes);
        main.removeCallbacks(refresh);
        super.onStopListening();
    }
    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::toggleUnlocked);
        else toggleUnlocked();
    }
    private void toggleUnlocked() {
        QuickTilePolicy.Action action = QuickTilePolicy.action(
            Settings.canDrawOverlays(this), EffectsService.isActive());
        if (action == QuickTilePolicy.Action.OPEN_SETUP) {
            openStudio();
            return;
        }
        if (action == QuickTilePolicy.Action.STOP) {
            prefs.edit().putBoolean("effectsEnabled", false).apply();
            stopService(new Intent(this, EffectsService.class));
        } else {
            prefs.edit().putBoolean("effectsEnabled", true).apply();
            try {
                // Explicit tile interaction; no boot receiver or background timer starts effects.
                startForegroundService(new Intent(this, EffectsService.class).setAction(EffectsService.START));
            } catch (RuntimeException failure) {
                Log.w("SnowGlobeTile", "Android declined the foreground-service start", failure);
                prefs.edit().putBoolean("effectsEnabled", false).putString("_serviceState", "error").apply();
                openStudio();
            }
        }
        updateState();
    }
    private void updateState() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean permission = Settings.canDrawOverlays(this);
        boolean active = EffectsService.isActive();
        String status = QuickTilePolicy.status(permission, active, prefs.getString("_serviceState", ""));
        tile.setLabel("SnowGlobe");
        // Keep setup/retry clickable. STATE_UNAVAILABLE would disable the action.
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setContentDescription("SnowGlobe over-app effects. " + status);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(status);
        tile.updateTile();
    }
    @SuppressWarnings("deprecation")
    private void openStudio() {
        Intent intent = new Intent(this, StudioActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent launch = PendingIntent.getActivity(this, 15, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(launch);
            } else startActivityAndCollapse(intent);
        } catch (RuntimeException failure) {
            Log.w("SnowGlobeTile", "Could not open Studio from Quick Settings", failure);
        }
    }
    @Override public void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(changes);
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
