package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.PowerManager;
import android.view.*;

/** View host only. Wallpaper draws SceneRenderer directly into its own Surface. */
public class SnowView extends View implements Choreographer.FrameCallback {
    private final SharedPreferences preferences;
    private final SceneRenderer renderer;
    private boolean posted=false,enabled=true,preview=false,powerSave=false;
    private long presented=0L;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key!=null&&!key.startsWith("_"))reload();
    };
    public SnowView(Context context,SharedPreferences prefs){
        super(context);preferences=prefs;
        renderer=new SceneRenderer(getResources().getDisplayMetrics().density,new SceneSettings(prefs));
        setWillNotDraw(false);
    }
    public void setPreviewMode(boolean value){preview=value;setClickable(value);resizeScene();invalidate();}
    public void setAnimating(boolean value){enabled=value;updateSchedule();}
    public void refresh(){reload();}
    private void reload(){
        renderer.apply(new SceneSettings(preferences));
        PowerManager pm=getContext().getSystemService(PowerManager.class);powerSave=pm!=null&&pm.isPowerSaveMode();
        resizeScene();invalidate();updateSchedule();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();preferences.registerOnSharedPreferenceChangeListener(changes);reload();}
    @Override protected void onDetachedFromWindow(){cancel();preferences.unregisterOnSharedPreferenceChangeListener(changes);super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);resizeScene();}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){WindowInsets result=super.onApplyWindowInsets(insets);resizeScene();return result;}
    private void resizeScene(){
        if(renderer==null)return;
        float t=0,b=0;WindowInsets insets=getRootWindowInsets();
        if(!preview&&insets!=null){
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());t=safe.top;b=safe.bottom;}
            else {t=insets.getSystemWindowInsetTop();b=insets.getSystemWindowInsetBottom();}
        }
        float d=getResources().getDisplayMetrics().density;
        if(preview&&getWidth()>0)d=Math.min(d,getWidth()/330f);
        renderer.resize(getWidth(),getHeight(),t,b,d);
    }
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(renderer!=null)updateSchedule();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(renderer!=null)updateSchedule();}
    private boolean visible(){return enabled&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
    private void updateSchedule(){
        if(!visible()){cancel();return;}
        if(!posted&&renderer.animated()){posted=true;Choreographer.getInstance().postFrameCallback(this);}
    }
    private void cancel(){if(posted)Choreographer.getInstance().removeFrameCallback(this);posted=false;presented=0;renderer.resetClock();}
    @Override public void doFrame(long nanos){
        posted=false;if(!visible())return;
        int fps=MotionMath.fps(renderer.settings().quality,powerSave);
        if(MotionMath.frameDue(presented,nanos,fps)){presented=nanos;renderer.advance(nanos);invalidate();}
        updateSchedule();
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);renderer.draw(canvas,preview);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!preview)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){renderer.touch(event.getX(),event.getY());updateSchedule();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
