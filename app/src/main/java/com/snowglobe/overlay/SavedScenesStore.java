package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** Three private local scene slots. No network, account, photo access or runtime-state restore. */
public final class SavedScenesStore {
    private final SharedPreferences storage,settings;
    public SavedScenesStore(Context context){storage=context.getSharedPreferences("snowglobe_scenes",Context.MODE_PRIVATE);settings=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);}
    private static void slot(int slot){if(slot<0||slot>=3)throw new IllegalArgumentException("Invalid slot");}
    public String name(int index){slot(index);return storage.getString("name"+index,"Scene "+(index+1));}
    public boolean exists(int index){slot(index);return storage.contains("scene"+index);}
    public SceneSnapshot capture(){return SceneSnapshot.capture(settings.getAll());}
    public void save(int index,String name){
        slot(index);String cleaned=name==null?"":name.trim().replaceAll("[\\p{Cntrl}]","");
        if(cleaned.isEmpty())cleaned="Scene "+(index+1);if(cleaned.length()>32)cleaned=cleaned.substring(0,32);
        storage.edit().putString("name"+index,cleaned).putString("scene"+index,capture().encode()).apply();
    }
    public void delete(int index){slot(index);storage.edit().remove("name"+index).remove("scene"+index).apply();}
    public SceneSnapshot read(int index){slot(index);return SceneSnapshot.decode(storage.getString("scene"+index,null));}
    public void apply(SceneSnapshot snapshot,String name){
        SharedPreferences.Editor editor=settings.edit();
        for(Map.Entry<String,Object> item:snapshot.values().entrySet()){
            if(item.getValue() instanceof Boolean)editor.putBoolean(item.getKey(),(Boolean)item.getValue());
            else editor.putInt(item.getKey(),(Integer)item.getValue());
        }
        editor.putString("selectedScene",name==null?"Custom":name).apply();
    }
}
