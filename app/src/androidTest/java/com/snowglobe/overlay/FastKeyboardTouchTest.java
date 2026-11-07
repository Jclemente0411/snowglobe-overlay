package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Repeated real view dispatch, not simulated direct character callbacks. */
@RunWith(AndroidJUnit4.class)
public class FastKeyboardTouchTest {
    @Test public void highRateTapsDoNotDropLettersAndCandidatesAreAccessible() {
        Context context=ApplicationProvider.getApplicationContext();
        AtomicInteger calls=new AtomicInteger();
        AtomicLong elapsed=new AtomicLong();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.content.SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
            prefs.edit().putBoolean("keyboardRgb",true).putInt("keyboardRgbPattern",0).putBoolean("keyboardHaptics",false).commit();
            FastKeyboardView v=new FastKeyboardView(context,prefs,new FastKeyboardView.InputActions(){
                public void commit(String text){if(text.equalsIgnoreCase("q"))calls.incrementAndGet();}
                public void delete(){}
                public void enter(){}
                public void choose(String suggestion){}
            });
            int width=Math.round(context.getResources().getDisplayMetrics().density*420);
            v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.AT_MOST));
            v.layout(0,0,width,v.getMeasuredHeight());
            v.start();
            RectF q=v.keyBoundsForTest("q");assertNotNull(q);
            long begin=SystemClock.uptimeMillis();
            for(int i=0;i<140;i++){
                long t=SystemClock.uptimeMillis();
                MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,q.centerX(),q.centerY(),0);
                MotionEvent u=MotionEvent.obtain(t,t+12,MotionEvent.ACTION_UP,q.centerX(),q.centerY(),0);
                v.onTouchEvent(d);v.onTouchEvent(u);
                d.recycle();u.recycle();
            }
            elapsed.set(SystemClock.uptimeMillis()-begin);
            assertEquals(140,calls.get());
            v.setSuggestions(Arrays.asList("hello","help","holiday"));
            assertNotNull(v.getAccessibilityNodeProvider().createAccessibilityNodeInfo(1000));
            v.stop();
        });
        assertTrue("Tap dispatch must remain bounded under test load",elapsed.get()<5000);
    }

    @Test public void reverseLiftTwoThumbTypingKeepsPressOrder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            StringBuilder text=new StringBuilder();FastKeyboardView view=create(new FastKeyboardView.InputActions(){
                public void commit(String value){text.append(value);}public void delete(){}public void enter(){}public void choose(String value){}
            });
            RectF q=view.keyHitBoxForTest("q"),w=view.keyHitBoxForTest("w");
            dispatch(view,MotionEvent.ACTION_DOWN,new int[]{3},q);
            dispatch(view,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{3,8},q,w);
            dispatch(view,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{3,8},q,w);
            dispatch(view,MotionEvent.ACTION_UP,new int[]{3},q);
            assertEquals("qw",text.toString());view.stop();
        });
    }

    @Test public void draggingDeleteAwayStopsAndroidHandlerRepeats() {
        AtomicInteger deletes=new AtomicInteger();AtomicInteger atCancel=new AtomicInteger();
        AtomicReference<FastKeyboardView> ref=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            FastKeyboardView view=create(new FastKeyboardView.InputActions(){
                public void commit(String value){}public void delete(){deletes.incrementAndGet();}public void enter(){}public void choose(String value){}
            });
            ref.set(view);dispatch(view,MotionEvent.ACTION_DOWN,new int[]{4},view.keyHitBoxForTest("⌫"));
        });
        try{
            SystemClock.sleep(600);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                FastKeyboardView view=ref.get();atCancel.set(deletes.get());
                dispatch(view,MotionEvent.ACTION_MOVE,new int[]{4},view.keyHitBoxForTest("space"));
            });
            SystemClock.sleep(350);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                assertEquals("A held Delete must stop as soon as its finger leaves",atCancel.get(),deletes.get());
                dispatch(ref.get(),MotionEvent.ACTION_UP,new int[]{4},ref.get().keyHitBoxForTest("space"));
            });
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ref.get().stop());}
    }

    private static FastKeyboardView create(FastKeyboardView.InputActions actions){
        Context context=ApplicationProvider.getApplicationContext();
        android.content.SharedPreferences prefs=context.getSharedPreferences("keyboard-touch-regression",0);
        prefs.edit().putBoolean("keyboardHaptics",false).commit();FastKeyboardView view=new FastKeyboardView(context,prefs,actions);
        int width=Math.round(context.getResources().getDisplayMetrics().density*420);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.AT_MOST));
        view.layout(0,0,width,view.getMeasuredHeight());view.start();return view;
    }
    private static void dispatch(FastKeyboardView view,int action,int[] ids,RectF... positions){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=positions[i].centerX();coordinates[i].y=positions[i].centerY();
            coordinates[i].pressure=1;coordinates[i].size=1;
        }
        long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,ids.length,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        view.onTouchEvent(event);event.recycle();
    }
}
