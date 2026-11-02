package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/** A separate preference file prevents an appearance choice from changing/resuming effects. */
public final class HolidayAppearanceStore {
    public static final String FILE="snowglobe_studio_appearance";
    private final SharedPreferences preferences;
    public HolidayAppearanceStore(Context context){
        preferences=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        if(!preferences.contains("theme")){
            boolean legacy=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE).getBoolean("pixelColors",false);
            preferences.edit().putString("theme",HolidayThemeTokens.initial(null,legacy)).apply();
        }
    }
    public String theme(){return HolidayThemeTokens.normalize(preferences.getString("theme",HolidayThemeTokens.CLASSIC));}
    public void select(String value){preferences.edit().putString("theme",HolidayThemeTokens.normalize(value)).apply();}
}
