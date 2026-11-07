package com.snowglobe.overlay;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.*;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One user-enabled, noninteractive overlay; hiding removes the window completely. */
public class EffectsService extends Service {
    public static final String START="com.snowglobe.overlay.START", STOP="com.snowglobe.overlay.STOP",
        HIDE="com.snowglobe.overlay.HIDE", SHOW="com.snowglobe.overlay.SHOW";
    private static final String CHANNEL="snowglobe_effects";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Set<Object> studioOwners=Collections.newSetFromMap(new IdentityHashMap<>());
    private static EffectsService current;
    private SharedPreferences prefs;
    private Context windowContext;
    private WindowManager manager;
    private SnowView view, retainedView;
    private WindowManager.LayoutParams overlayParams;
    private int attachedHeight=0;
    private boolean foregroundStarted, failed;
    private static boolean keyboardTyping=false;
    /** Cap visual work while a native keyboard is visible; never intercept key touches. */
    public static void setKeyboardTyping(boolean active){
        keyboardTyping=active;
        Runnable update=()->{if(current!=null&&current.view!=null)current.view.setKeyboardTyping(active);};
        if(Looper.myLooper()==Looper.getMainLooper())update.run();else MAIN.post(update);
    }
    // A screen-on broadcast may arrive before the keyguard has finished dismissing.
    // Retry for a short, bounded interval; never run a permanent timer.
    private int unlockChecks;
    private final Runnable unlockCheckTask=new Runnable(){
        @Override public void run(){
            if(!shouldCheckUnlock())return;
            unlockChecks++;
            reconcile();
            if(shouldCheckUnlock())MAIN.postDelayed(this,UnlockResumePolicy.CHECK_DELAY_MS);
        }
    };
    private final Runnable reconcileTask=this::reconcile;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged=(p,key)->{
        if ("effectsEnabled".equals(key) && !p.getBoolean(key,true)) {
            detachOverlay(); state("paused"); stopSelf(); return;
        }
        if (key!=null && !key.startsWith("_")) {
            MAIN.removeCallbacks(reconcileTask); MAIN.post(reconcileTask);
        }
    };
    private final BroadcastReceiver screenChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent==null?"":intent.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action)){
                cancelUnlockChecks();reconcile();return;
            }
            reconcile();
            if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action)
                ||Intent.ACTION_CONFIGURATION_CHANGED.equals(action))scheduleUnlockChecks();
        }
    };
    public static void studioStarted(Object owner){studioOwners.add(owner);refreshVisibility();}
    public static void studioStopped(Object owner){
        studioOwners.remove(owner);refreshVisibility();
        if(current!=null)current.scheduleUnlockChecks();
    }
    public static void refreshVisibility(){if(current!=null)current.reconcile();}
    public static boolean isActive(){return current!=null&&current.foregroundStarted&&!current.failed
        &&current.prefs.getBoolean("effectsEnabled",true)&&Settings.canDrawOverlays(current);}
    public static boolean isOverlayAttached(){return current!=null&&current.view!=null&&current.view.isAttachedToWindow();}
    /** Pause is sticky until a user explicitly resumes. Never automatically resumes over a payment. */
    public static void pauseNow(Context context){
        Context app=context.getApplicationContext();
        app.getSharedPreferences("snowglobe",MODE_PRIVATE).edit().putBoolean("effectsEnabled",false).apply();
        Runnable stop=()->{if(current!=null){current.detachOverlay();current.state("paused");current.stopSelf();}};
        if(Looper.myLooper()==Looper.getMainLooper())stop.run();else MAIN.post(stop);
        app.stopService(new Intent(app,EffectsService.class));
    }
    @Override public void onCreate(){
        super.onCreate(); current=this; prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
        // A service context does not describe a particular window area. Keep one
        // overlay window context so its metrics/resources follow display changes.
        windowContext=this;
        if(Build.VERSION.SDK_INT>=30){
            DisplayManager displays=getSystemService(DisplayManager.class);
            Display display=displays==null?null:displays.getDisplay(Display.DEFAULT_DISPLAY);
            if(display!=null)windowContext=createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        }
        manager=windowContext.getSystemService(WindowManager.class);
        OverlayCoverageMigration.apply(prefs);
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged);
        state("starting");
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Christmas effects",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Pause the overlay before shopping, payment or protected screens");channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenChanges,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenChanges,filter);
    }
    private boolean shouldCheckUnlock(){
        if(prefs==null)return false;
        PowerManager power=getSystemService(PowerManager.class);
        return foregroundStarted&&!failed&&
            UnlockResumePolicy.shouldRetry(unlockChecks,prefs.getBoolean("effectsEnabled",false),
                Settings.canDrawOverlays(this),power!=null&&power.isInteractive(),
                !studioOwners.isEmpty(),isOverlayAttached());
    }
    private void cancelUnlockChecks(){
        MAIN.removeCallbacks(unlockCheckTask);unlockChecks=0;
    }
    private void scheduleUnlockChecks(){
        MAIN.removeCallbacks(unlockCheckTask);unlockChecks=0;
        if(shouldCheckUnlock())MAIN.postDelayed(unlockCheckTask,UnlockResumePolicy.CHECK_DELAY_MS);
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,StudioActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,EffectsService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_snowflake)
            .setContentTitle("SnowGlobe effects enabled").setContentText("Tap Pause before shopping or payment screens.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Pause effects",pause).build()).build();
    }
    private void state(String value){
        if(prefs!=null&&!value.equals(prefs.getString("_serviceState",""))){
            prefs.edit().putString("_serviceState",value).apply();
            try{TileService.requestListeningState(this,new ComponentName(this,SnowGlobeTileService.class));}
            catch(RuntimeException ignored){ /* Tile may not have been added. */ }
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?START:intent.getAction();
        if(STOP.equals(action)){cancelUnlockChecks();pauseNow(this);return START_NOT_STICKY;}
        if(!prefs.getBoolean("effectsEnabled",true)){state("paused");stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){state("permission_required");stopSelf();return START_NOT_STICKY;}
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(101,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(101,notification());
            foregroundStarted=true;failed=false;reconcile();scheduleUnlockChecks();
            return failed?START_NOT_STICKY:START_STICKY;
        }catch(RuntimeException error){fail(error);return START_NOT_STICKY;}
    }
    private void reconcile(){
        if(!foregroundStarted||failed||prefs==null)return;
        boolean enabled=prefs.getBoolean("effectsEnabled",true), permitted=Settings.canDrawOverlays(this);
        PowerManager power=getSystemService(PowerManager.class);KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        boolean interactive=power!=null&&power.isInteractive(), locked=keyguard!=null&&keyguard.isKeyguardLocked();
        boolean studio=!studioOwners.isEmpty(), wallpaper=SnowWallpaperService.hasVisibleScene();
        String desired=OverlaySafetyPolicy.state(enabled,permitted,studio,interactive,locked,wallpaper);
        if(!OverlaySafetyPolicy.shouldAttach(enabled,permitted,studio,interactive,locked,wallpaper)){
            detachOverlay();state(desired);if(!enabled||!permitted)stopSelf();return;
        }
        // Window geometry is what Android uses to classify obscured touches.
        // Restricting the overlay to the top band is an opt-in convenience, not a
        // bypass for protected payment controls.
        int targetHeight=OverlayWindowPolicy.height(windowHeight(),windowContext.getResources().getDisplayMetrics().density,
            prefs.getBoolean("edgeOnly",false));
        if(view!=null&&attachedHeight!=targetHeight){
            try{
                overlayParams.height=targetHeight;
                manager.updateViewLayout(view,overlayParams);
                attachedHeight=targetHeight;
            }catch(RuntimeException error){fail(error);return;}
        }
        if(view==null){
            SnowView candidate=null;
            try{
                float maximum=.8f;
                if(Build.VERSION.SDK_INT>=31){InputManager input=getSystemService(InputManager.class);if(input!=null)maximum=input.getMaximumObscuringOpacityForTouch();}
                float alpha=OverlaySafetyPolicy.windowAlpha(maximum);
                if(alpha<=0f)throw new IllegalStateException("Android does not permit a touch-through overlay");
                // Keep cached sprites across a lock/unlock or Studio visit. A
                // detached view has no frame callbacks, receiver or preference listener.
                candidate=retainedView!=null?retainedView:new SnowView(windowContext,prefs);
                candidate.setAnimating(true);
                candidate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,targetHeight,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
                params.gravity=Gravity.TOP|Gravity.START;params.alpha=alpha;params.setTitle("SnowGlobe visual effects");
                // The IME covers the lower scene without resizing its coordinate
                // space. System bars/cutouts likewise must not crop the snow canvas.
                params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
                if(Build.VERSION.SDK_INT>=30){
                    params.setFitInsetsTypes(0);
                    params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                }else if(Build.VERSION.SDK_INT>=28){
                    params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                manager.addView(candidate,params);view=candidate;retainedView=candidate;
                overlayParams=params;attachedHeight=targetHeight;
                candidate.setKeyboardTyping(keyboardTyping);
            }catch(RuntimeException error){
                if(candidate!=null)try{manager.removeViewImmediate(candidate);}catch(RuntimeException ignored){}
                fail(error);return;
            }
        }
        state("visible");
    }
    private int windowHeight(){
        if(Build.VERSION.SDK_INT>=30){
            android.view.WindowMetrics metrics=manager.getCurrentWindowMetrics();
            if(metrics!=null)return metrics.getBounds().height();
        }
        return windowContext.getResources().getDisplayMetrics().heightPixels;
    }
    private void fail(RuntimeException error){
        cancelUnlockChecks();
        Log.e("SnowGlobeEffects","Overlay stopped safely",error);failed=true;detachOverlay();state("error");stopSelf();
    }
    private void detachOverlay(){
        SnowView old=view;view=null;overlayParams=null;attachedHeight=0;
        if(old!=null){old.setAnimating(false);try{manager.removeViewImmediate(old);}catch(RuntimeException ignored){}}
    }
    @Override public void onDestroy(){
        cancelUnlockChecks();
        MAIN.removeCallbacks(reconcileTask);
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        try{unregisterReceiver(screenChanges);}catch(IllegalArgumentException ignored){}
        detachOverlay();retainedView=null;foregroundStarted=false;stopForeground(STOP_FOREGROUND_REMOVE);
        if(prefs!=null&&!failed&&!"permission_required".equals(prefs.getString("_serviceState","")))state(prefs.getBoolean("effectsEnabled",true)?"stopped":"paused");
        if(current==this)current=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
