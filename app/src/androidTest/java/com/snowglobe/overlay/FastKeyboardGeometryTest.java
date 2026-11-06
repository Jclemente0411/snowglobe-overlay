package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual Android View measurement and hitbox checks, not mocked CSS dimensions. */
@RunWith(AndroidJUnit4.class)
public final class FastKeyboardGeometryTest {
    @Test public void centeredRowsStayAboveSystemGestureArea() {
        Context context=ApplicationProvider.getApplicationContext();
        AtomicReference<FastKeyboardView> ref=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            FastKeyboardView view=new FastKeyboardView(context,
                 context.getSharedPreferences("snowglobe",0),new FastKeyboardView.InputActions(){
                     public void commit(String value){}
                     public void delete(){}
                     public void enter(){}
                 });
            int width=Math.round(420*context.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(3000,View.MeasureSpec.AT_MOST));
            view.layout(0,0,width,view.getMeasuredHeight());
            ref.set(view);
        });
        FastKeyboardView view=ref.get();
        assertNotNull(view);
        assertEquals("Exactly 10 numeric, 10 top, 9 middle, 9 bottom letters/actions, 6 footers",44,view.keyCountForTest());
        RectF q=view.keyBoundsForTest("q");
        RectF a=view.keyBoundsForTest("a");
        RectF z=view.keyBoundsForTest("z");
        RectF space=view.keyBoundsForTest("space");
        RectF enter=view.keyBoundsForTest("↵");
        assertNotNull(q);assertNotNull(a);assertNotNull(z);assertNotNull(space);assertNotNull(enter);
        assertTrue(q.top<a.top);
        assertTrue(a.top<z.top);
        assertTrue(z.top<space.top);
        assertTrue("Space must clear navigation gesture bar",space.bottom<view.getHeight()-view.safeBottomForTest());
        assertTrue("Enter must clear navigation gesture bar",enter.bottom<view.getHeight()-view.safeBottomForTest());
        assertTrue("Centered home row must be inset from top QWERTY",a.left>q.left);
        assertTrue("Space must be wide enough for touch typing",space.width()>q.width()*3f);
    }
}
