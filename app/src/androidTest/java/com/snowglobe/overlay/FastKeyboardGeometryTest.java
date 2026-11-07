package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.Rect;
import android.text.InputType;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
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
                     public void choose(String suggestion){}
                 });
            int width=Math.round(420*context.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(3000,View.MeasureSpec.AT_MOST));
            view.layout(0,0,width,view.getMeasuredHeight());
            ref.set(view);
        });
        FastKeyboardView view=ref.get();
        assertNotNull(view);
        assertEquals("10 numeric, 10 top, 9 middle, 9 lower letters/actions, 7 footer controls",45,view.keyCountForTest());
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
        assertTrue("Keyboard must leave Gboard-like gesture clearance",view.safeBottomForTest()>=view.getResources().getDisplayMetrics().density*45);
        assertTrue("Footer row must visibly clear gesture area",view.getHeight()-enter.bottom>view.getResources().getDisplayMetrics().density*45);
    }

    @Test public void constrainedHeightFitsEveryKeyAndNumericEditorUsesOnePad() {
        Context context=ApplicationProvider.getApplicationContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            FastKeyboardView view=new FastKeyboardView(context,context.getSharedPreferences("keyboard-geometry-test",0),
                new FastKeyboardView.InputActions(){
                    public void commit(String value){}public void delete(){}public void enter(){}public void choose(String value){}
                });
            int width=Math.round(320*context.getResources().getDisplayMetrics().density);
            int height=Math.round(220*context.getResources().getDisplayMetrics().density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            view.layout(0,0,width,height);
            assertAllTargetsContained(view);
            EditorInfo editor=new EditorInfo();editor.inputType=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL;
            editor.imeOptions=EditorInfo.IME_ACTION_DONE;view.configureEditor(editor);
            assertNull("Number editor must not show a QWERTY row",view.keyBoundsForTest("q"));
            assertNotNull(view.keyBoundsForTest("0"));assertNotNull(view.keyBoundsForTest("."));assertNotNull(view.keyBoundsForTest("Done"));
            assertAllTargetsContained(view);view.stop();
        });
    }
    private static void assertAllTargetsContained(FastKeyboardView view){
        Rect bounds=new Rect();
        for(int id=0;id<view.keyCountForTest();id++){
            AccessibilityNodeInfo node=view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(id);
            assertNotNull(node);node.getBoundsInParent(bounds);
            assertTrue(bounds.width()>0&&bounds.height()>0);
            assertTrue(bounds.left>=0&&bounds.top>=0&&bounds.right<=view.getWidth());
            assertTrue("No clipped lower row",bounds.bottom<=Math.ceil(view.getHeight()-view.safeBottomForTest()));node.recycle();
        }
    }
}
