package com.snowglobe.overlay;
import android.content.SharedPreferences;

/** Immutable settings snapshot: no preference reads in the frame loop. */
public final class SceneSettings {
    public final boolean snow,lights,twinkle,sparks,reduced,gentle;
    public final boolean wallpaperSnow,wakeSnow,fallingBlink,edgeOnly;
    public final int palette,design,bulbShape,motion,direction,flake,count,quality,snowShade;
    public final float speed,size,bulbSize,brightness,glow,wind,spin,sparkDensity,tempo,snowVisibility;
    public final int[] decorations;
    public SceneSettings(SharedPreferences p) {
        snow=p.getBoolean("snow",true);lights=p.getBoolean("lights",true);
        twinkle=p.getBoolean("twinkle",true);sparks=p.getBoolean("screenSparkles",false);
        reduced=p.getBoolean("reduceMotion",false);gentle=p.getBoolean("gentleMotion",true);
        wallpaperSnow=p.getBoolean("wallpaperSnow",true);wakeSnow=p.getBoolean("wakeSnow",true);
        fallingBlink=p.getBoolean("fallingLightBlink",true);
        edgeOnly=p.getBoolean("edgeOnly",true);snowShade=integer(p,"snowShade",0,0,2);
        snowVisibility=value(p,"snowVisibility",100);
        palette=integer(p,"palette",0,0,3);design=integer(p,"lightDesign",0,0,2);
        bulbShape=integer(p,"bulbShape",0,0,2);motion=twinkle?integer(p,"lightMotion",1,0,5):0;
        direction=integer(p,"particleDirection",0,0,7);flake=integer(p,"snowStyle",1,0,2);
        count=Math.round(integer(p,"density",55,0,100)*1.2f);quality=integer(p,"qualityMode",3,0,3);
        speed=0.2f+value(p,"speed",50)*1.5f;size=0.45f+value(p,"size",50)*1.15f;
        bulbSize=0.65f+value(p,"lightSize",55)*0.9f;
        brightness=value(p,"lightBrightness",75);glow=value(p,"lightGlow",55);
        wind=value(p,"particleWind",30);spin=value(p,"particleRotation",35);
        sparkDensity=value(p,"sparkleDensity",30);tempo=value(p,"lightTempo",30);
        String[] keys={"candy","gifts","trees","snowmen","bulbs","stars","confetti"};
        int n=0;for(String k:keys)if(p.getBoolean(k,false))n++;
        decorations=new int[n];int j=0;for(int i=0;i<keys.length;i++)if(p.getBoolean(keys[i],false))decorations[j++]=i;
    }
    private static int integer(SharedPreferences p,String key,int def,int min,int max){return Math.max(min,Math.min(max,p.getInt(key,def)));}
    private static float value(SharedPreferences p,String key,int def){return integer(p,key,def,0,100)/100f;}
    public boolean animated(boolean wallpaper) {
        return ((!reduced)&&((snow&&snowVisibility>0&&count>0)||(wallpaper&&wallpaperSnow&&snowVisibility>0)||(decorations.length>0&&count>0)||(sparks&&sparkDensity>0)))
            || lights&&motion!=0&&brightness>0&&!reduced;
    }
}
