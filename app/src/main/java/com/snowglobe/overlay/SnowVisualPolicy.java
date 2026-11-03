package com.snowglobe.overlay;

/** Snow color/visibility is an artistic choice; never alters touch-security window opacity. */
public final class SnowVisualPolicy {
    private SnowVisualPolicy(){}
    public static int color(int shade){
        if(shade==1)return 0xffbdcad6;  // Silver gray, softer on dark scenes
        if(shade==2)return 0xff253345;  // Charcoal, more visible over light apps
        return 0xffeffaff;              // Classic snow white, same as earlier releases
    }
    public static int alpha(int original,float visibility){
        if(!Float.isFinite(visibility))visibility=0f;
        return Math.max(0,Math.min(255,Math.round(Math.max(0,Math.min(255,original))*Math.max(0f,Math.min(1f,visibility)))));
    }
    /** Limit simulations to visible content; particle arrays are allocated only once. */
    public static int particleCount(int nominal,boolean snow,boolean decor,boolean wallpaperSnow,boolean wake,boolean topBand){
        if(!snow&&!decor&&!wallpaperSnow&&!wake)return 0;
        int n=Math.max(0,Math.min(128,nominal));
        if(wallpaperSnow)n=Math.max(n,48);
        if(wake)n=Math.max(n,64);
        if(topBand)n=Math.max(1,Math.round(n*.45f));
        return Math.min(128,n);
    }
}