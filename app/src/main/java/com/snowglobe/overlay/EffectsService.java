package com.snowglobe.overlay;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.input.InputManager;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;

/** User-controlled foreground overlay. No lock-screen bypass, touch capture or boot autostart. */
public class EffectsService extends Service {
    public static final String START="com.snowglobe.overlay.START",STOP="com.snowglobe.overlay.STOP",
        HIDE="com.snowglobe.overlay.HIDE",SHOW="com.snowglobe.overlay.SHOW";
    private static final String CHANNEL="snowglobe_effects";
    private static EffectsService current;
    private static boolean studioVisible=false;
    private SharedPreferences prefs;
    private WindowManager manager;
    private SnowView view;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged=(p,key)->{
        if("effectsEnabled".equals(key)&&!p.getBoolean(key,true)){stopSelf();return;}
        if(key!=null&&!key.startsWith("_"))applyVisibility();
    };
    private final BroadcastReceiver screenChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){applyVisibility();if(view!=null)view.refresh();}
    };
    public static void setStudioVisible(boolean value){studioVisible=value;if(current!=null)current.applyVisibility();}
    public static void refreshVisibility(){if(current!=null)current.applyVisibility();}
    public static boolean isActive(){return current!=null&&current.view!=null&&current.view.isAttachedToWindow();}
    @Override public void onCreate(){
        super.onCreate();current=this;prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged);state("starting");
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Christmas effects",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Control the Christmas effects you enabled");channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenChanges,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenChanges,filter);
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,StudioActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,EffectsService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_snowflake)
            .setContentTitle("SnowGlobe is on").setContentText("Tap to customize. Pause stops the overlay.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Pause",pause).build()).build();
    }
    private void state(String value){if(!value.equals(prefs.getString("_serviceState","")))prefs.edit().putString("_serviceState",value).apply();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?START:intent.getAction();
        if(STOP.equals(action)){prefs.edit().putBoolean("effectsEnabled",false).apply();stopSelf();return START_NOT_STICKY;}
        if(!prefs.getBoolean("effectsEnabled",true)||!Settings.canDrawOverlays(this)){state("permission_required");stopSelf();return START_NOT_STICKY;}
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(101,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(101,notification());
            if(view==null){
                manager=getSystemService(WindowManager.class);view=new SnowView(getApplicationContext(),prefs);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
                p.gravity=Gravity.TOP|Gravity.START;
                p.alpha=Build.VERSION.SDK_INT>=31?Math.min(.75f,getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch()):.75f;
                view.setVisibility(View.GONE);manager.addView(view,p);
            }
            if(HIDE.equals(action))studioVisible=true;else if(SHOW.equals(action))studioVisible=false;
            state("running");applyVisibility();return START_STICKY;
        }catch(RuntimeException failure){Log.e("SnowGlobeEffects","Unable to start overlay",failure);state("error");stopSelf();return START_NOT_STICKY;}
    }
    private void applyVisibility(){
        if(view==null)return;
        PowerManager power=getSystemService(PowerManager.class);KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        boolean allowed=Settings.canDrawOverlays(this)&&prefs.getBoolean("effectsEnabled",true);
        boolean show=allowed&&!studioVisible&&power.isInteractive()&&!keyguard.isKeyguardLocked()&&!SnowWallpaperService.hasVisibleScene();
        view.setVisibility(show?View.VISIBLE:View.GONE);
        if(!allowed){state("permission_required");stopSelf();}
    }
    @Override public void onDestroy(){
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        try{unregisterReceiver(screenChanges);}catch(IllegalArgumentException ignored){}
        if(view!=null){try{manager.removeViewImmediate(view);}catch(RuntimeException ignored){}view=null;}
        stopForeground(STOP_FOREGROUND_REMOVE);
        if(prefs!=null&&!"error".equals(prefs.getString("_serviceState","")))state("stopped");
        if(current==this)current=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
