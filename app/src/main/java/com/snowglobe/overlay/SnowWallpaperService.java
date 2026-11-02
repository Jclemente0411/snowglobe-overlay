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
    private static int visibleScenes=0;
    public static boolean hasVisibleScene(){return visibleScenes>0;}
    @Override public Engine onCreateEngine(){return new SnowEngine();}
    private final class SnowEngine extends Engine implements Choreographer.FrameCallback {
        private SharedPreferences prefs;
        private SceneRenderer renderer;
        private boolean visible=false,surfaceReady=false,interactive=false,posted=false,powerSave=false;
        private long presented=0L,lastTouch=0L;
        private int frames=0,surfaceWidth=1,surfaceHeight=1;
        private float screenDensity=1,safeTop=0,safeBottom=0;
        private boolean countedVisible=false;
        private void countVisibility(boolean shown){
            if(countedVisible==shown)return;
            countedVisible=shown;visibleScenes=Math.max(0,visibleScenes+(shown?1:-1));
            EffectsService.refreshVisibility();
        }
        private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
            if(key!=null&&!key.startsWith("_")){renderer.apply(new SceneSettings(p));requestFrame();}
        };
        private final BroadcastReceiver powerChanges=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                boolean wasInteractive=interactive;readPower();
                if(!interactive)cancel();
                else {if(!wasInteractive&&visible){frames=0;renderer.wake();}requestFrame();}
            }
        };
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);
            prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
            Context display=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            screenDensity=display.getResources().getDisplayMetrics().density;
            renderer=new SceneRenderer(screenDensity,new SceneSettings(prefs));
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
        private void updateGeometry(){
            if(renderer==null)return;
            // A minimum design margin also helps wallpaper hosts that report zero bar insets.
            renderer.resize(surfaceWidth,surfaceHeight,Math.max(safeTop,32*screenDensity),safeBottom,screenDensity);
        }
        @Override public void onApplyWindowInsets(WindowInsets insets){
            super.onApplyWindowInsets(insets);
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                safeTop=safe.top;safeBottom=safe.bottom;
            }else {safeTop=insets.getStableInsetTop();safeBottom=insets.getStableInsetBottom();}
            updateGeometry();render();requestFrame();
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder){
            super.onSurfaceCreated(holder);surfaceReady=true;countVisibility(visible);requestFrame();
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){
            super.onSurfaceChanged(holder,format,w,h);surfaceReady=true;surfaceWidth=w;surfaceHeight=h;
            Context display=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            screenDensity=display.getResources().getDisplayMetrics().density;
            updateGeometry();render();requestFrame();
        }
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder){super.onSurfaceRedrawNeeded(holder);render();}
        @Override public void onSurfaceDestroyed(SurfaceHolder holder){surfaceReady=false;countVisibility(false);cancel();super.onSurfaceDestroyed(holder);}
        @Override public void onVisibilityChanged(boolean shown){
            visible=shown;countVisibility(shown&&surfaceReady);cancel();
            if(shown){readPower();renderer.wake();frames=0;requestFrame();}
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
            if(MotionMath.frameDue(presented,nanos,MotionMath.fps(renderer.settings().quality,powerSave))){presented=nanos;renderer.advance(nanos);render();}
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
            visible=false;surfaceReady=false;countVisibility(false);cancel();
            if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(changes);
            try{unregisterReceiver(powerChanges);}catch(IllegalArgumentException ignored){}
            super.onDestroy();
        }
    }
}
