package com.snowglobe.overlay;

import android.content.SharedPreferences;

/**
 * 0.21.5 restores the earlier full-screen snow default. Versions 0.20-0.21.4
 * had silently set edgeOnly=true for almost every user, so existing installations
 * need a one-time migration. The user may subsequently re-enable top-only mode.
 * Full-screen visual overlays can still be rejected by protected app controls.
 */
public final class OverlayCoverageMigration {
    private static final String MARKER="_coverageRestored0215";
    private OverlayCoverageMigration(){}
    public static void apply(SharedPreferences preferences){
        if(preferences.getBoolean(MARKER,false))return;
        preferences.edit().putBoolean("edgeOnly",false).putBoolean(MARKER,true).apply();
    }
}
