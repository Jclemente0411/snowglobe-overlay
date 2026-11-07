package com.snowglobe.overlay;

/** Bounds, not a promise of measured FPS or battery savings. */
public final class RenderPolicy {
    private RenderPolicy() {}
    public static int fps(int quality, boolean preview, boolean powerSaver, int thermal) {
        return fps(quality,preview,powerSaver,thermal,120f);
    }
    /** Auto mode uses a conservative cap based on the actual screen, not an assumed Pixel refresh rate. */
    public static int fps(int quality, boolean preview, boolean powerSaver, int thermal, float displayHz) {
        float hz=Float.isFinite(displayHz)&&displayHz>=30f?displayHz:60f;
        int result=quality<=0?30:quality==1?60:quality==3?(hz>=90f?60:30):120;
        if(preview)result=Math.min(result,30);
        if(powerSaver||thermal>=2)result=Math.min(result,30);
        if(thermal>=4)result=Math.min(result,15);
        // Use a whole number of display refreshes per scene frame. For example,
        // 45 FPS on a 90 Hz screen has steady pacing; requesting 60 cannot.
        int cadence=Math.max(1,(int)Math.ceil(hz/result-.01f));
        return Math.max(1,Math.min(result,(int)Math.ceil(hz/cadence)));
    }
    public static long delayMillis(long presented,long now,int fps,float displayHz) {
        if(presented<=0||now<presented||fps<=0)return 0;
        float hz=Float.isFinite(displayHz)&&displayHz>=30?displayHz:60;
        long remaining=1_000_000_000L/fps-(now-presented)-(long)(1_000_000_000d/hz);
        return Math.max(0,Math.min(66,remaining/1_000_000L));
    }
}
