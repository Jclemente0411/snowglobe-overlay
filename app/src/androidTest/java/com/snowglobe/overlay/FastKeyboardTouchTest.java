package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
            prefs.edit().putBoolean("keyboardRgb",false).putBoolean("keyboardHaptics",false).commit();
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
        });
        assertTrue("Tap dispatch must remain bounded under test load",elapsed.get()<5000);
    }
}
