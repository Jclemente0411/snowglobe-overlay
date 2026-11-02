package com.snowglobe.overlay;

import android.app.WallpaperColors;
import android.content.*;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.*;

/** System-selected wallpaper, not an overlay above the secure keyguard. */
public class SnowWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine(){return new SnowEngine();}
    private final class SnowEngine extends Engine implements Choreographer.FrameCallback {
        private SharedPreferences prefs;
        private SceneRenderer renderer;
        private boolean visible=false,surfaceReady=false,interactive=false,posted=false,powerSave=false;
        private long presented=0L,lastTouch=0L;
        private int frames=0;
        private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
            if(key!=null&&!key.startsWith("_")){renderer.apply(new SceneSettings(p));requestFrame();}
        };
        private final BroadcastReceiver powerChanges=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                boolean wasInteractive=interactive;
                readPower();
                if(!interactive)cancel();
                else {if(!wasInteractive&&visible)renderer.wake();requestFrame();}
            }
        };
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);
            prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
            Context display=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            renderer=new SceneRenderer(display.getResources().getDisplayMetrics().density,new SceneSettings(prefs));
            renderer.setWallpaper(true);prefs.registerOnSharedPreferenceChangeListener(changes);
            setTouchEventsEnabled(true);setOffsetNotificationsEnabled(false);readPower();
            IntentFilter filter=new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(powerChanges,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(powerChanges,filter);
        }
        private void readPower(){
            PowerManager pm=getSystemService(PowerManager.class);
            powerSave=pm!=null&&pm.isPowerSaveMode();interactive=pm!=null&&pm.isInteractive();
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder){super.onSurfaceCreated(holder);surfaceReady=true;requestFrame();}
        @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){
            super.onSurfaceChanged(holder,format,w,h);surfaceReady=true;
            Context display=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            renderer.resize(w,h,0,0,display.getResources().getDisplayMetrics().density);
            render();requestFrame();
        }
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder){super.onSurfaceRedrawNeeded(holder);render();}
        @Override public void onSurfaceDestroyed(SurfaceHolder holder){surfaceReady=false;cancel();super.onSurfaceDestroyed(holder);}
        @Override public void onVisibilityChanged(boolean shown){
            visible=shown;cancel();if(shown){readPower();renderer.wake();frames=0;requestFrame();}
        }
        @Override public void onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){long now=android.os.SystemClock.uptimeMillis();
                if(now-lastTouch>250){lastTouch=now;renderer.touch(event.getX(),event.getY());requestFrame();}}
            super.onTouchEvent(event);
        }
        private void requestFrame(){if(!posted&&visible&&surfaceReady&&interactive){posted=true;Choreographer.getInstance().postFrameCallback(this);}}
        private void cancel(){if(posted)Choreographer.getInstance().removeFrameCallback(this);posted=false;presented=0;if(renderer!=null)renderer.resetClock();}
        @Override public void doFrame(long nanos){
            posted=false;if(!visible||!surfaceReady||!interactive)return;
            if(MotionMath.frameDue(presented,nanos,MotionMath.fps(renderer.settings().quality,powerSave))){
                presented=nanos;renderer.advance(nanos);render();
            }
            if(renderer.animated())requestFrame();
        }
        private void render(){
            if(!surfaceReady||renderer==null||!getSurfaceHolder().getSurface().isValid())return;
            Canvas canvas=null;
            try {
                try{canvas=getSurfaceHolder().lockHardwareCanvas();}catch(RuntimeException unavailable){canvas=getSurfaceHolder().lockCanvas();}
                if(canvas!=null){renderer.draw(canvas,true);frames++;}
            }catch(RuntimeException error){
                Log.w("SnowGlobeWallpaper","Wallpaper frame failed",error);
                if(!isPreview())prefs.edit().putString("_wallpaperError","Wallpaper renderer could not draw. Reapply the wallpaper.").apply();
            }finally{
                if(canvas!=null)try{getSurfaceHolder().unlockCanvasAndPost(canvas);}catch(RuntimeException surfaceGone){Log.d("SnowGlobeWallpaper","Surface closed during frame");}
            }
            if(frames==1&&!isPreview())prefs.edit().putLong("_wallpaperFrameAt",System.currentTimeMillis()).remove("_wallpaperError").apply();
        }
        @android.annotation.TargetApi(27)
        @Override public WallpaperColors onComputeColors(){return new WallpaperColors(Color.valueOf(0xff0b2037),Color.valueOf(0xff9df2da),Color.valueOf(0xffffd68c));}
        @Override public void onDestroy(){
            visible=false;surfaceReady=false;cancel();
            if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(changes);
            try{unregisterReceiver(powerChanges);}catch(IllegalArgumentException ignored){}
            super.onDestroy();
        }
    }
}
