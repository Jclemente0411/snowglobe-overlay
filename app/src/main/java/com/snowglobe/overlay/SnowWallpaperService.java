package com.snowglobe.overlay;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * A real system-managed live wallpaper. Users explicitly select this in Android's
 * wallpaper picker; no keyguard bypass or privileged window types are required.
 */
public class SnowWallpaperService extends WallpaperService {
 @Override public Engine onCreateEngine(){return new SnowEngine();}
 private final class SnowEngine extends Engine {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final SharedPreferences prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
  private final SnowView scene=new SnowView(SnowWallpaperService.this,prefs);
  private boolean visible=false;
  private final Runnable frame=new Runnable(){@Override public void run(){render();}};
  private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{if(visible)render();};
  SnowEngine(){prefs.registerOnSharedPreferenceChangeListener(changes);}
  @Override public void onVisibilityChanged(boolean shown){visible=shown;handler.removeCallbacks(frame);if(shown)render();}
  @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int width,int height){super.onSurfaceChanged(holder,format,width,height);scene.layout(0,0,width,height);if(visible)render();}
  private void render(){
   handler.removeCallbacks(frame);
   if(!visible)return;
   SurfaceHolder holder=getSurfaceHolder();
   Canvas canvas=null;
   try{
    canvas=holder.lockCanvas();
    if(canvas!=null){
     canvas.drawColor(Color.rgb(8,19,34));
     scene.layout(0,0,canvas.getWidth(),canvas.getHeight());
     scene.draw(canvas);
    }
   }catch(Exception ignored){}finally{if(canvas!=null)holder.unlockCanvasAndPost(canvas);}
   if(visible)handler.postDelayed(frame,33);
  }
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy();}
 }
}
