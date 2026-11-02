package com.snowglobe.overlay;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small local, deterministic compositions. Never edits permissions, playback or billing. */
public final class SceneCatalog {
    public static final class Preset {
        public final String name, description;
        public final int illustration;
        private final SceneSnapshot visual;
        private Preset(String name, String description, int illustration, SceneSnapshot visual) {
            this.name=name;this.description=description;this.illustration=illustration;this.visual=visual;
        }
        public Map<String,Object> visualValues(){return visual.values();}
    }
    private static Preset preset(String name,String subtitle,int artwork,Object... overrides) {
        Map<String,Object> changes=new HashMap<>();
        if(overrides.length%2!=0)throw new IllegalArgumentException("Invalid recipe");
        for(int i=0;i<overrides.length;i+=2)changes.put((String)overrides[i],overrides[i+1]);
        return new Preset(name,subtitle,artwork,SceneSnapshot.capture(changes));
    }
    private static final List<Preset> SCENES=Collections.unmodifiableList(Arrays.asList(
        preset("Christmas Eve","Warm glass bulbs and peaceful snow",0,
            "palette",0,"bulbShape",0,"lightMotion",5,"lightDesign",0,"speed",22,"size",54,
            "density",34,"lightBrightness",70,"lightGlow",80,"lightSize",62,"lightTempo",22),
        preset("Snowy Village","Snow-covered pines and glowing windows",1,
            "palette",2,"bulbShape",2,"lightMotion",1,"speed",25,"density",65,
            "lightBrightness",62,"lightGlow",58,"trees",true,"stars",true,
            "screenSparkles",true,"sparkleDensity",18,"lightTempo",18),
        preset("Vintage Christmas","Big colorful bulbs and nostalgic warmth",2,
            "palette",0,"bulbShape",0,"lightDesign",1,"lightMotion",5,"speed",28,
            "density",30,"lightSize",75,"lightBrightness",78,"lightGlow",77,
            "bulbs",true,"fallingLightBlink",false,"lightTempo",15),
        preset("Arctic Night","Icy crystals and delicate blue lights",3,
            "palette",1,"bulbShape",1,"lightMotion",1,"snowStyle",2,"speed",30,
            "size",44,"density",70,"lightBrightness",58,"lightGlow",48,
            "screenSparkles",true,"sparkleDensity",13,"lightTempo",15),
        preset("North Pole Party","Colorful lights, presents and celebration",4,
            "palette",3,"bulbShape",1,"lightMotion",3,"speed",64,"density",62,
            "size",57,"lightBrightness",82,"lightGlow",67,"candy",true,
            "gifts",true,"trees",true,"snowmen",true,"bulbs",true,"stars",true,
            "confetti",true,"screenSparkles",true,"sparkleDensity",60,
            "lightTempo",40)
    ));
    private SceneCatalog(){}
    public static List<Preset> presets(){return SCENES;}
    public static Preset find(String name){
        for(Preset p:SCENES)if(p.name.equals(name))return p;
        return null;
    }
    public static boolean apply(SharedPreferences prefs,String name){
        Preset preset=find(name);
        if(preset==null)return false;
        SharedPreferences.Editor editor=prefs.edit();
        for(Map.Entry<String,Object> value:preset.visualValues().entrySet()){
            if(value.getValue() instanceof Boolean)editor.putBoolean(value.getKey(),(Boolean)value.getValue());
            else editor.putInt(value.getKey(),(Integer)value.getValue());
        }
        editor.putString("selectedScene",name).apply();
        return true;
    }
}