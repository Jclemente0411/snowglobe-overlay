package com.snowglobe.overlay;

/** User-facing truth comes from active service & system selection, not just a saved toggle. */
public final class PixelSetupStatus {
    private PixelSetupStatus(){}
    public static String overlay(boolean permission,boolean running,boolean requested,String serviceState){
        if(!permission)return "Permission needed";
        if(running)return "On · hidden while editing";
        if("error".equals(serviceState))return "Start failed · retry in Studio";
        if("background_blocked".equals(serviceState))return "Android blocked auto-start · open Studio";
        if(requested)return "Ready · tap Start";
        return "Paused · tap Start";
    }
    public static String wallpaper(boolean supported,boolean applied) {
        if(!supported)return "Check in Android wallpaper settings";
        return applied?"Selected for lock screen":"Choose lock-screen wallpaper";
    }
    public static String quickTile(){return "Optional · add from Quick Settings";}
    public static boolean likelyPixel(String manufacturer,String model){
        return manufacturer!=null&&model!=null&&manufacturer.equalsIgnoreCase("Google")
                &&model.toLowerCase(java.util.Locale.ROOT).startsWith("pixel");
    }
}