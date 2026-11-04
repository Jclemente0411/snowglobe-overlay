package com.snowglobe.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Restores an explicitly enabled foreground effect after device reboot or app update.
 * These are Android-approved start events; ordinary screen-unlock broadcasts are handled
 * within a running foreground service, not by trying restricted background starts.
 */
public final class EffectsStartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(context==null||intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&
                !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
        if(!UnlockResumePolicy.shouldStartAfterBoot(
            prefs.getBoolean("effectsEnabled",false),
            Settings.canDrawOverlays(app),EffectsService.isActive()))return;
        try{
            Intent service=new Intent(app,EffectsService.class).setAction(EffectsService.START);
            if(Build.VERSION.SDK_INT>=26)app.startForegroundService(service);
            else app.startService(service);
        }catch(RuntimeException blocked){
            // Never set effectsEnabled=false: the user can reopen Studio and resume.
            Log.w("SnowGlobeStartup","System blocked restore; user can start from Studio",blocked);
            prefs.edit().putString("_serviceState","background_blocked").apply();
        }
    }
}