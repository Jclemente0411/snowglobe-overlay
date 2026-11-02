package com.snowglobe.overlay;

import android.app.WallpaperColors;
import android.content.*;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.*;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.*;

/** System-selected wallpaper. Never draws above the keyguard, notification panel or fingerprint UI. */
public class SnowWallpaperService extends WallpaperService {
    private static int visibleScenes;
    public static boolean hasVisibleScene(){return visibleScenes>0;}
    @Override public Engine onCreateEngine(){return new SnowEngine();}
    private final class SnowEngine extends Engine implements Choreographer.FrameCallback {
        private SharedPreferences prefs;
        private SceneRenderer renderer;
        private RenderPowerMonitor power;
        private final Handler main=new Handler(Looper.getMainLooper());
        private boolean visible,surfaceReady,posted,countedVisible,reloadQueued;
        private long presented,lastTouch;
        private int frames,failures,surfaceWidth=1,surfaceHeight=1;
        private float density=1,safeTop,safeBottom,displayHz=60;
        private final Runnable reload=()->{
            reloadQueued=false;if(renderer==null)return;
            renderer.apply(new SceneSettings(prefs));failures=0;render();requestFrame();
        };
        private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
            if(key!=null&&!key.startsWith("_")&&!reloadQueued){reloadQueued=true;main.post(reload);}
        };
        private void countVisibility(boolean shown){
            if(countedVisible==shown)return;
            countedVisible=shown;visibleScenes=Math.max(0,visibleScenes+(shown?1:-1));EffectsService.refreshVisibility();
        }
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
            Context context=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            density=context.getResources().getDisplayMetrics().density;
            WindowManager wm=context.getSystemService(WindowManager.class);if(wm!=null)displayHz=wm.getDefaultDisplay().getRefreshRate();
            renderer=new SceneRenderer(density,new SceneSettings(prefs));renderer.setWallpaper(true);
            power=new RenderPowerMonitor(context,()->{
                cancel();if(power!=null&&power.interactive&&visible){frames=0;renderer.wake();requestFrame();}
            });
            power.attach();prefs.registerOnSharedPreferenceChangeListener(changes);
            setTouchEventsEnabled(true);setOffsetNotificationsEnabled(false);
        }
        private void updateGeometry(){if(renderer!=null)renderer.resize(surfaceWidth,surfaceHeight,Math.max(safeTop,32*density),safeBottom,density);}
        @Override public void onApplyWindowInsets(WindowInsets insets){
            super.onApplyWindowInsets(insets);
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safeTop=safe.top;safeBottom=safe.bottom;}
            else{safeTop=insets.getStableInsetTop();safeBottom=insets.getStableInsetBottom();}
            updateGeometry();render();requestFrame();
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder){super.onSurfaceCreated(holder);surfaceReady=true;failures=0;countVisibility(visible);requestFrame();}
        @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int width,int height){
            super.onSurfaceChanged(holder,format,width,height);surfaceReady=true;surfaceWidth=width;surfaceHeight=height;failures=0;
            Context context=Build.VERSION.SDK_INT>=29?getDisplayContext():SnowWallpaperService.this;
            density=context.getResources().getDisplayMetrics().density;updateGeometry();render();requestFrame();
        }
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder){super.onSurfaceRedrawNeeded(holder);render();}
        @Override public void onSurfaceDestroyed(SurfaceHolder holder){surfaceReady=false;countVisibility(false);cancel();super.onSurfaceDestroyed(holder);}
        @Override public void onVisibilityChanged(boolean shown){
            visible=shown;countVisibility(shown&&surfaceReady);cancel();
            if(shown&&power!=null){power.read();failures=0;frames=0;renderer.wake();render();requestFrame();}
        }
        @Override public void onTouchEvent(MotionEvent event){
            if(renderer!=null&&visible&&power!=null&&power.interactive&&event.getActionMasked()==MotionEvent.ACTION_DOWN){
                long now=SystemClock.uptimeMillis();if(now-lastTouch>250){lastTouch=now;renderer.touch(event.getX(),event.getY());requestFrame();}
            }
            super.onTouchEvent(event);
        }
        private int fps(){return RenderPolicy.fps(renderer.settings().quality,isPreview(),power.powerSaver,power.thermal);}
        private void requestFrame(){
            if(!posted&&visible&&surfaceReady&&power!=null&&power.interactive&&failures<3){
                posted=true;Choreographer.getInstance().postFrameCallbackDelayed(this,RenderPolicy.delayMillis(presented,System.nanoTime(),fps(),displayHz));
            }
        }
        private void cancel(){if(posted)Choreographer.getInstance().removeFrameCallback(this);posted=false;presented=0;if(renderer!=null)renderer.resetClock();}
        @Override public void doFrame(long nanos){
            posted=false;if(!visible||!surfaceReady||power==null||!power.interactive||failures>=3)return;
            if(MotionMath.frameDue(presented,nanos,fps())){presented=nanos;renderer.advance(nanos);render();}
            if(renderer.animated())requestFrame();
        }
        private void render(){
            if(!visible||!surfaceReady||renderer==null||power==null||!power.interactive||failures>=3||!getSurfaceHolder().getSurface().isValid())return;
            Canvas canvas=null;boolean drew=false;
            try{
                try{canvas=getSurfaceHolder().lockHardwareCanvas();}catch(RuntimeException unavailable){canvas=getSurfaceHolder().lockCanvas();}
                if(canvas!=null){renderer.draw(canvas,true);drew=true;}
            }catch(RuntimeException error){failures++;Log.w("SnowGlobeWallpaper","Wallpaper frame failed",error);}
            finally{if(canvas!=null)try{getSurfaceHolder().unlockCanvasAndPost(canvas);}catch(RuntimeException closed){drew=false;failures++;}}
            if(drew){failures=0;frames++;if(frames==1&&!isPreview())prefs.edit().putLong("_wallpaperFrameAt",System.currentTimeMillis()).remove("_wallpaperError").apply();}
            else if(failures>=3){cancel();if(!isPreview())prefs.edit().putString("_wallpaperError","Rendering paused after a surface error. Reopen the wallpaper picker to retry.").apply();}
        }
        @android.annotation.TargetApi(27)
        @Override public WallpaperColors onComputeColors(){return new WallpaperColors(Color.valueOf(0xff0b2037),Color.valueOf(0xff9df2da),Color.valueOf(0xffffd68c));}
        @Override public void onDestroy(){
            visible=false;surfaceReady=false;countVisibility(false);cancel();main.removeCallbacks(reload);
            if(power!=null)power.close();if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy();
        }
    }
}
