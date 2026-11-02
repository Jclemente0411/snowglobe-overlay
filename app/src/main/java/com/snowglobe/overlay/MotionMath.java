package com.snowglobe.overlay;

/** Pure, allocation-free motion functions shared by renderer and regression tests. */
public final class MotionMath {
    private MotionMath() {}
    public static float clamp(float v, float min, float max) {
        return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : min;
    }
    public static float seconds(long previous, long now) {
        if (previous <= 0 || now <= previous) return 0f;
        return Math.min(0.05f, (now - previous) / 1_000_000_000f);
    }
    public static float directionX(int direction) {
        switch (direction) {
            case 1: case 6: return 0.70710678f;
            case 2: case 7: return -0.70710678f;
            case 3: return 1f;
            case 4: return -1f;
            default: return 0f;
        }
    }
    public static float directionY(int direction) {
        switch (direction) {
            case 1: case 2: return 0.70710678f;
            case 3: case 4: return 0f;
            case 5: return -1f;
            case 6: case 7: return -0.70710678f;
            default: return 1f;
        }
    }
    public static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
    /** Soft entry and exit. No single-frame full-intensity flashes. */
    public static float envelope(float age, float life) {
        if (age <= 0f || life <= 0f || age >= life) return 0f;
        float edge = Math.min(0.8f, life * 0.25f);
        return smooth(age / edge) * smooth((life - age) / edge);
    }
    /** Cycles are deliberately limited to at most 0.8 Hz; never a full-screen strobe. */
    public static float brightness(int mode, float time, float seed, float rate, boolean reduced) {
        if (reduced || mode == 0) return 1f;
        double phase = time * (0.15f + clamp(rate, 0f, 1f) * 0.65f) * Math.PI * 2;
        if (mode == 1) return (float)(0.68 + 0.32 * (0.5 + 0.5 * Math.sin(phase * 0.63 + seed * 2.31)));
        if (mode == 2) return 0.08f + 0.92f * smooth((float)(0.5 + 0.5 * Math.sin(phase + seed * 0.12)));
        if (mode == 3) return (float)(0.22 + 0.78 * Math.pow(0.5 + 0.5 * Math.sin(phase - seed * 0.85), 2));
        return 0.14f + 0.86f * smooth((float)(0.5 + 0.5 * Math.sin(phase + ((int)seed % 2) * Math.PI)));
    }
    public static int fps(int mode, boolean powerSave) {
        if (powerSave || mode == 0) return 30;
        return mode == 2 ? 120 : 60;
    }
    public static boolean frameDue(long previous, long now, int fps) {
        return previous == 0 || now - previous >= 1_000_000_000L / Math.max(1, fps) - 1_000_000L;
    }
}
