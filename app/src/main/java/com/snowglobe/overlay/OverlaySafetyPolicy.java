package com.snowglobe.overlay;

/** Pure safety decisions. A low alpha does NOT bypass another app's touch protection. */
public final class OverlaySafetyPolicy {
    private OverlaySafetyPolicy() {}
    public static float windowAlpha(float systemMaximum) {
        if (!Float.isFinite(systemMaximum) || systemMaximum <= 0f) return 0f;
        return Math.max(0f, Math.min(0.60f, systemMaximum - 0.05f));
    }
    public static boolean shouldAttach(boolean enabled, boolean permitted, boolean studio,
            boolean interactive, boolean locked, boolean wallpaperVisible) {
        return enabled && permitted && !studio && interactive && !locked && !wallpaperVisible;
    }
    public static String state(boolean enabled, boolean permitted, boolean studio,
            boolean interactive, boolean locked, boolean wallpaperVisible) {
        if (!enabled) return "paused";
        if (!permitted) return "permission_required";
        if (!interactive) return "screen_off";
        if (locked) return "locked";
        if (studio) return "studio";
        if (wallpaperVisible) return "wallpaper";
        return "visible";
    }
}
