package com.snowglobe.overlay;

/** Bounds, not a promise of measured FPS or battery savings. */
public final class RenderPolicy {
    private RenderPolicy() {}
    public static int fps(int quality, boolean preview, boolean powerSaver, int thermal) {
        int result=quality<=0?30:quality==1?60:120;
        if(preview)result=Math.min(result,30);
        if(powerSaver||thermal>=2)result=Math.min(result,30);
        if(thermal>=4)result=Math.min(result,15);
        return result;
    }
    public static long delayMillis(long presented,long now,int fps,float displayHz) {
        if(presented<=0||now<presented||fps<=0)return 0;
        float hz=Float.isFinite(displayHz)&&displayHz>=30?displayHz:60;
        long remaining=1_000_000_000L/fps-(now-presented)-(long)(1_000_000_000d/hz);
        return Math.max(0,Math.min(66,remaining/1_000_000L));
    }
}
