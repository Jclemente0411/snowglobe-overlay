package com.snowglobe.overlay;

import java.util.*;

/** Explicit allowlist: scene loading never changes playback, permissions, battery or billing state. */
public final class SceneSnapshot {
    private static final String HEADER="SNOWGLOBE_SCENE_1";
    private static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> d=new LinkedHashMap<>();
        for(String k:new String[]{"snow","lights","twinkle","fallingLightBlink"})d.put(k,true);
        for(String k:new String[]{"screenSparkles","candy","gifts","trees","snowmen","bulbs","stars","confetti"})d.put(k,false);
        d.put("palette",0);d.put("lightDesign",0);d.put("bulbShape",0);d.put("lightMotion",1);
        d.put("particleDirection",0);d.put("snowStyle",1);d.put("speed",50);d.put("size",50);
        d.put("density",55);d.put("lightSize",55);d.put("lightBrightness",75);d.put("lightGlow",55);
        d.put("particleWind",30);d.put("particleRotation",35);d.put("sparkleDensity",30);d.put("lightTempo",30);
        DEFAULTS=Collections.unmodifiableMap(d);
    }
    private final Map<String,Object> values;
    private SceneSnapshot(Map<String,Object> values){this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
    public Map<String,Object> values(){return values;}
    private static int max(String k){
        if(k.equals("palette"))return 3;if(k.equals("lightDesign")||k.equals("bulbShape")||k.equals("snowStyle"))return 2;
        if(k.equals("lightMotion"))return 5;if(k.equals("particleDirection"))return 7;return 100;
    }
    public static SceneSnapshot capture(Map<String,?> source){
        Map<String,Object> result=new LinkedHashMap<>();
        for(Map.Entry<String,Object> e:DEFAULTS.entrySet()){
            Object raw=source.get(e.getKey()),value=e.getValue();
            if(value instanceof Boolean&&raw instanceof Boolean)value=raw;
            if(value instanceof Integer&&raw instanceof Number)value=Math.max(0,Math.min(max(e.getKey()),((Number)raw).intValue()));
            result.put(e.getKey(),value);
        }
        return new SceneSnapshot(result);
    }
    public String encode(){
        StringBuilder out=new StringBuilder(HEADER).append('\n');
        for(Map.Entry<String,Object> e:values.entrySet())out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return out.toString();
    }
    public static SceneSnapshot decode(String encoded){
        if(encoded==null||encoded.length()>8192)throw new IllegalArgumentException("Invalid scene");
        String[] lines=encoded.split("\n");
        if(lines.length<3||!HEADER.equals(lines[0]))throw new IllegalArgumentException("Unsupported scene");
        Map<String,Object> parsed=new LinkedHashMap<>();
        for(int i=1;i<lines.length;i++){
            String[] pair=lines[i].split("=",-1);
            if(pair.length!=2||!DEFAULTS.containsKey(pair[0])||parsed.containsKey(pair[0]))throw new IllegalArgumentException("Invalid scene field");
            Object def=DEFAULTS.get(pair[0]);String raw=pair[1];
            if(def instanceof Boolean){if(!raw.equals("true")&&!raw.equals("false"))throw new IllegalArgumentException("Invalid switch");parsed.put(pair[0],Boolean.parseBoolean(raw));}
            else{int n;try{n=Integer.parseInt(raw);}catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid slider",bad);}
                if(n<0||n>max(pair[0]))throw new IllegalArgumentException("Invalid range");parsed.put(pair[0],n);}
        }
        if(parsed.size()!=DEFAULTS.size())throw new IllegalArgumentException("Incomplete scene");
        return new SceneSnapshot(parsed);
    }
}
