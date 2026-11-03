package com.snowglobe.overlay;

/** The smaller app-overlay window leaves ordinary touch points below the top edge unobscured. */
public final class OverlayWindowPolicy {
    private OverlayWindowPolicy(){}
    public static int height(int screenPixels,float density,boolean topBand){
        if(!topBand)return -1;
        if(screenPixels<=0)return -1; // Never infer screen bounds from missing display data.
        float safeDensity=Float.isFinite(density)&&density>0?density:1f;
        int minimum=Math.round(156*safeDensity);
        int band=Math.max(minimum,Math.round(screenPixels*.25f));
        return Math.min(screenPixels,Math.max(1,band));
    }
}