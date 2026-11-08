package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.view.*;

/** Canvas host with bounded preview rate, thermal response and coalesced setting changes. */
public class SnowView extends View implements Choreographer.FrameCallback {
    private final SharedPreferences preferences;
    private final SceneRenderer renderer;
    private final RenderPowerMonitor power;
    private boolean posted=false,enabled=true,preview=false,reloadQueued=false,keyboardTyping=false,imeVisible=false;
    private long presented=0L;
    private final Runnable reloadTask=()->{reloadQueued=false;reload();};
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key!=null&&!key.startsWith("_")&&!reloadQueued){reloadQueued=true;postOnAnimation(reloadTask);}
    };
    public SnowView(Context context,SharedPreferences prefs){
        super(context);preferences=prefs;
        renderer=new SceneRenderer(getResources().getDisplayMetrics().density,settingsSnapshot());
        power=new RenderPowerMonitor(context,()->{if(!powerInteractive())cancel();else {invalidate();updateSchedule();}});
        setWillNotDraw(false);
    }
    private boolean powerInteractive(){return power!=null&&power.interactive;}
    public void setPreviewMode(boolean value){preview=value;setClickable(value);resizeScene();invalidate();}
    public void setAnimating(boolean value){enabled=value;if(renderer!=null)updateSchedule();}
    /** Reduce shared UI-thread/GPU work while the user types, but keep flakes visible. */
    public void setKeyboardTyping(boolean typing){
        if(keyboardTyping==typing)return;
        keyboardTyping=typing;
        renderer.setTypingBudget(keyboardTyping||imeVisible);
        cancel();
        invalidate();
        updateSchedule();
    }
    public void refresh(){reload();}
    /** Entitlement is read at snapshot boundaries only, never inside a frame. */
    private SceneSettings settingsSnapshot(){
        return new SceneSettings(preferences,
            HolidayFeatureAccess.hasAccess(getContext()));
    }
    private void reload(){
        renderer.apply(settingsSnapshot());power.read();resizeScene();invalidate();updateSchedule();
    }
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();preferences.registerOnSharedPreferenceChangeListener(changes);power.attach();reload();}
    @Override public void onDetachedFromWindow(){
        cancel();removeCallbacks(reloadTask);reloadQueued=false;
        imeVisible=false;renderer.setTypingBudget(keyboardTyping);
        preferences.unregisterOnSharedPreferenceChangeListener(changes);power.close();super.onDetachedFromWindow();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);resizeScene();}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        WindowInsets result=super.onApplyWindowInsets(insets);
        if(Build.VERSION.SDK_INT>=30){
            boolean visible=insets.isVisible(WindowInsets.Type.ime());
            if(imeVisible!=visible){
                imeVisible=visible;renderer.setTypingBudget(keyboardTyping||imeVisible);
                cancel();invalidate();updateSchedule();
            }
        }
        resizeScene();return result;
    }
    private void resizeScene(){
        if(renderer==null)return;
        float top=0,bottom=0;WindowInsets insets=getRootWindowInsets();
        if(!preview&&insets!=null){
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=safe.top;bottom=safe.bottom;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
        }
        float density=getResources().getDisplayMetrics().density;
        if(preview&&getWidth()>0)density=Math.min(density,getWidth()/330f);
        renderer.resize(getWidth(),getHeight(),top,bottom,density);
    }
    @Override protected void onVisibilityChanged(View changed,int visibility){
        super.onVisibilityChanged(changed,visibility);
        if(renderer!=null&&power!=null){if(visibility==VISIBLE)reload();else updateSchedule();}
    }
    @Override protected void onWindowVisibilityChanged(int visibility){
        super.onWindowVisibilityChanged(visibility);
        if(renderer!=null&&power!=null){if(visibility==VISIBLE)reload();else updateSchedule();}
    }
    private boolean visible(){return enabled&&powerInteractive()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
    private int fps(){Display display=getDisplay();float hz=display==null?60f:display.getRefreshRate();
        int quality=keyboardTyping||imeVisible?0:renderer.settings().quality;
        return RenderPolicy.fps(quality,preview,power.powerSaver,power.thermal,hz);}
    private void updateSchedule(){
        if(!visible()){cancel();return;}
        if(!posted&&renderer.animated()){
            posted=true;Display display=getDisplay();float hz=display==null?60:display.getRefreshRate();
            Choreographer.getInstance().postFrameCallbackDelayed(this,RenderPolicy.delayMillis(presented,System.nanoTime(),fps(),hz));
        }
    }
    private void cancel(){if(posted)Choreographer.getInstance().removeFrameCallback(this);posted=false;presented=0;if(renderer!=null)renderer.resetClock();}
    @Override public void doFrame(long nanos){
        posted=false;if(!visible())return;
        if(MotionMath.frameDue(presented,nanos,fps())){presented=nanos;renderer.advance(nanos);invalidate();}
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
