package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.shadows.ShadowView;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

/** Real Android measurement, Canvas rendering and pointer dispatch on a local Android runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w460dp-h1024dp-port-hdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FastKeyboardViewRegressionTest {
    private Context context;
    private SharedPreferences prefs;
    private FastKeyboardView view;
    private final Recorder recorder=new Recorder();

    @Implements(PowerManager.class)
    public static class HealthyThermalService extends ShadowPowerManager {
        @Implementation(minSdk=29)
        protected void addThermalStatusListener(Executor executor,PowerManager.OnThermalStatusChangedListener listener){}
        @Implementation(minSdk=29)
        protected void removeThermalStatusListener(PowerManager.OnThermalStatusChangedListener listener){}
    }
    /** Observe Android invalidation requests without replacing the keyboard renderer. */
    @Implements(View.class)
    public static class KeyInvalidationObserver extends ShadowView {
        static int keyInvalidations;
        @Implementation protected void invalidate(int left,int top,int right,int bottom){
            if(realView instanceof FastKeyboardView)keyInvalidations++;
            Shadow.directlyOn(realView,View.class,"invalidate",ClassParameter.from(int.class,left),
                ClassParameter.from(int.class,top),ClassParameter.from(int.class,right),ClassParameter.from(int.class,bottom));
        }
    }

    private static final class Recorder implements FastKeyboardView.InputActions {
        final StringBuilder text=new StringBuilder();
        int deletes,enters,switches,cursor;
        String suggestion;
        @Override public void commit(String value){text.append(value);}
        @Override public void delete(){deletes++;}
        @Override public void enter(){enters++;}
        @Override public void choose(String value){suggestion=value;}
        @Override public void switchKeyboard(){switches++;}
        @Override public void moveCursor(int codePoints){cursor+=codePoints;}
    }
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        prefs=context.getSharedPreferences("keyboard-view-regression",Context.MODE_PRIVATE);
        prefs.edit().clear().putBoolean("keyboardRgb",false).putBoolean("keyboardHaptics",false).commit();
    }
    @After public void cleanup(){if(view!=null)view.stop();}
    private int px(float dp){return Math.round(dp*context.getResources().getDisplayMetrics().density);}
    private EditorInfo editor(int input){
        EditorInfo info=new EditorInfo();info.inputType=input;info.initialSelStart=0;info.initialSelEnd=0;return info;
    }
    private void create(EditorInfo info){
        view=new FastKeyboardView(context,prefs,recorder);view.configureEditor(info);view.start();
        measure(460,1000,View.MeasureSpec.AT_MOST);
    }
    private void create(){create(editor(InputType.TYPE_CLASS_TEXT));}
    private void measure(int width,int height,int heightMode){
        view.measure(View.MeasureSpec.makeMeasureSpec(px(width),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(height),heightMode));
        view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
    }
    private RectF key(String label){RectF key=view.keyHitBoxForTest(label);assertNotNull("Missing key "+label,key);return key;}
    private void single(int action,float x,float y){
        long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);
        view.onTouchEvent(event);event.recycle();
    }
    private void tap(String label){RectF key=key(label);tapAt(key.centerX(),key.centerY());}
    private void tapAt(float x,float y){single(MotionEvent.ACTION_DOWN,x,y);single(MotionEvent.ACTION_UP,x,y);}
    private void pointers(int action,int[] ids,RectF... locations){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=locations[i].centerX();coordinates[i].y=locations[i].centerY();
            coordinates[i].pressure=1;coordinates[i].size=1;
        }
        long now=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,action,ids.length,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        view.onTouchEvent(event);event.recycle();
    }
    private void idle(long milliseconds){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));}
    private void createAnimatedRgb(){
        prefs.edit().putBoolean("keyboardRgb",true).putInt("keyboardRgbPattern",0).commit();
        PowerManager power=context.getSystemService(PowerManager.class);
        Shadows.shadowOf(power).setIsInteractive(true);Shadows.shadowOf(power).setIsPowerSaveMode(false);
        create();KeyInvalidationObserver.keyInvalidations=0;
    }

    @Test @Config(shadows={HealthyThermalService.class,KeyInvalidationObserver.class})
    public void reactiveGlowRequestsFramesBeforeTheAmbientAnimationResumes(){
        createAnimatedRgb();RectF original=new RectF(key("q"));tap("q");
        KeyInvalidationObserver.keyInvalidations=0;Shadows.shadowOf(view).setDidRequestLayout(false);idle(90);
        assertEquals("q",recorder.text.toString());
        assertTrue("A short tap receives multiple display-synchronized updates",KeyInvalidationObserver.keyInvalidations>1);
        assertFalse("Reactive lighting cannot relayout keys",Shadows.shadowOf(view).didRequestLayout());
        assertEquals(original,key("q"));
    }
    @Test @Config(shadows={HealthyThermalService.class,KeyInvalidationObserver.class})
    public void holdingANewPointerCannotExtendThePreviousKeysGlow(){
        createAnimatedRgb();tap("q");idle(110);RectF w=key("w");
        single(MotionEvent.ACTION_DOWN,w.centerX(),w.centerY());idle(170);
        KeyInvalidationObserver.keyInvalidations=0;idle(100);
        assertEquals("The old glow expires while the new key is held",0,KeyInvalidationObserver.keyInvalidations);
        single(MotionEvent.ACTION_CANCEL,w.centerX(),w.centerY());assertEquals("q",recorder.text.toString());
    }
    @Test @Config(shadows={HealthyThermalService.class,KeyInvalidationObserver.class})
    public void reducedMotionAndKeyboardStopCancelReactiveFrames(){
        createAnimatedRgb();tap("q");idle(20);
        prefs.edit().putBoolean("reduceMotion",true).commit();idle(5);
        KeyInvalidationObserver.keyInvalidations=0;idle(100);
        assertEquals("Reduced motion cancels the short effect",0,KeyInvalidationObserver.keyInvalidations);
        prefs.edit().putBoolean("reduceMotion",false).commit();idle(5);tap("w");idle(20);view.stop();
        KeyInvalidationObserver.keyInvalidations=0;idle(300);
        assertEquals("No reactive frame survives keyboard stop",0,KeyInvalidationObserver.keyInvalidations);
        assertEquals("qw",recorder.text.toString());
    }

    @Test public void normalRowsHaveFamiliarProportionsAndIndependentBottomClearance(){
        create();RectF q=key("q"),footer=key("space");
        assertEquals(px(58),q.height(),1);
        assertEquals(px(62),footer.height(),1);
        assertTrue("The user's raised bottom position is separate from key height",view.safeBottomForTest()>=px(48));
        assertTrue(footer.bottom<=view.getHeight()-px(48));
        assertTrue(footer.width()>q.width()*3);
    }
    @Test public void constrainedPortraitKeepsEveryKeyInsideTheMeasuredView(){
        create();measure(320,230,View.MeasureSpec.EXACTLY);assertGeometryContained();
    }
    @Test @Config(qualifiers="w1024dp-h460dp-land-hdpi")
    public void constrainedLandscapeKeepsAllActionsAboveNavigation(){
        create();measure(800,180,View.MeasureSpec.AT_MOST);assertGeometryContained();
        assertTrue(key("space").bottom<=view.getHeight()-view.safeBottomForTest());
    }
    private void assertGeometryContained(){
        Rect bounds=new Rect();
        for(int id=0;id<view.keyCountForTest();id++){
            AccessibilityNodeInfo node=view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(id);
            assertNotNull(node);node.getBoundsInParent(bounds);
            assertTrue("Positive key bounds",bounds.width()>0&&bounds.height()>0);
            assertTrue("Key left inside view",bounds.left>=0);
            assertTrue("Key top inside view",bounds.top>=0);
            assertTrue("Key right inside view",bounds.right<=view.getWidth());
            assertTrue("No key enters the navigation area",bounds.bottom<=Math.ceil(view.getHeight()-view.safeBottomForTest()));
            node.recycle();
        }
    }
    @Test public void homeRowEdgesAndVisibleKeyGapsAreTouchTargets(){
        create();RectF a=key("a"),q=key("q");
        tapAt(1,a.centerY());tapAt(view.getWidth()-1,a.centerY());
        tapAt(q.right-1,q.centerY());tapAt(q.right+1,q.centerY());
        assertEquals("alqw",recorder.text.toString());
    }
    @Test public void navigationClearanceNeverCommitsASpace(){
        create();tapAt(view.getWidth()/2f,view.getHeight()-1);assertEquals("",recorder.text.toString());
        tap("space");assertEquals(" ",recorder.text.toString());
    }
    @Test public void twoThumbsCommitInPressOrderWhenSecondThumbLiftsFirst(){
        create();RectF q=key("q"),w=key("w");
        pointers(MotionEvent.ACTION_DOWN,new int[]{7},q);
        pointers(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{7,12},q,w);
        assertEquals("q",recorder.text.toString());
        pointers(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{7,12},q,w);
        pointers(MotionEvent.ACTION_UP,new int[]{7},q);
        assertEquals("qw",recorder.text.toString());
    }
    @Test public void heldShiftProducesOneCapitalWhenShiftLiftsBeforeTheLetter(){
        create();RectF shift=key("⇧"),a=key("a");
        pointers(MotionEvent.ACTION_DOWN,new int[]{2},shift);
        pointers(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{2,9},shift,a);
        pointers(MotionEvent.ACTION_POINTER_UP,new int[]{2,9},shift,a);
        pointers(MotionEvent.ACTION_UP,new int[]{9},a);tap("b");
        assertEquals("Ab",recorder.text.toString());
    }
    @Test public void heldShiftProducesOneCapitalWhenLetterLiftsFirst(){
        create();RectF shift=key("⇧"),a=key("a");
        pointers(MotionEvent.ACTION_DOWN,new int[]{2},shift);
        pointers(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{2,9},shift,a);
        pointers(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{2,9},shift,a);
        pointers(MotionEvent.ACTION_UP,new int[]{2},shift);tap("b");
        assertEquals("Ab",recorder.text.toString());
    }
    @Test public void doubleTapShiftLocksCaseAndSingleTapUnlocksIt(){
        create();tap("⇧");idle(80);tap("⇧");view.updateAutoShift(false);tap("q");tap("w");tap("⇧");tap("a");
        assertEquals("QWa",recorder.text.toString());
    }
    @Test public void aLetterBetweenShiftTapsCannotAccidentallyEnableCapsLock(){
        create();tap("⇧");tap("q");tap("⇧");tap("w");tap("e");assertEquals("QWe",recorder.text.toString());
    }
    @Test public void cursorCapsRepliesRespectManualShiftOnAndOff(){
        create();tap("⇧");view.updateAutoShift(false);tap("q");
        view.updateAutoShift(true);tap("⇧");view.updateAutoShift(true);tap("w");
        assertEquals("Qw",recorder.text.toString());
    }
    @Test public void cancelledShiftDragCannotLeaveCapitalizationEnabled(){
        create();RectF shift=key("⇧");single(MotionEvent.ACTION_DOWN,shift.centerX(),shift.centerY());
        single(MotionEvent.ACTION_MOVE,-10,-10);single(MotionEvent.ACTION_UP,-10,-10);tap("q");
        assertEquals("q",recorder.text.toString());
    }
    @Test public void allCharactersFieldStaysUppercaseAfterEveryLetter(){
        create(editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS));
        view.updateAutoShift(false);tap("q");tap("w");tap("e");assertEquals("QWE",recorder.text.toString());
    }
    @Test public void immediateSentenceBoundaryDoesNotWaitForAsyncEditorCaps(){
        create(editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        tap("h");tap("i");tap(".");
        RectF space=key("space"),q=key("q");
        pointers(MotionEvent.ACTION_DOWN,new int[]{4},space);
        pointers(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{4,8},space,q);
        pointers(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{4,8},space,q);
        pointers(MotionEvent.ACTION_UP,new int[]{4},space);
        assertEquals("Hi. Q",recorder.text.toString());
    }
    @Test public void immediateWordBoundaryCapitalizesTheNextWord(){
        create(editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS));
        tap("h");tap("i");tap("space");tap("q");tap("w");assertEquals("Hi Qw",recorder.text.toString());
    }
    @Test public void deletingThePeriodClearsItsPendingSentenceCapitalizationHint(){
        create(editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        tap("h");tap("i");tap(".");tap("⌫");tap("space");tap("q");
        assertEquals(1,recorder.deletes);
        // The recorder records commits; the receiving editor applies the separate deletion.
        assertTrue("The next commit after deleting the period must be lowercase",recorder.text.toString().endsWith(" q"));
    }
    @Test public void cursorMovementDoesNotCarryASentenceBoundaryIntoADifferentWord(){
        create(editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        tap("h");tap("i");tap(".");RectF space=key("space");
        single(MotionEvent.ACTION_DOWN,space.centerX(),space.centerY());
        single(MotionEvent.ACTION_MOVE,space.centerX()-px(36),space.centerY());
        single(MotionEvent.ACTION_UP,space.centerX()-px(36),space.centerY());
        tap("space");tap("q");
        assertEquals(-3,recorder.cursor);
        assertTrue("A moved cursor must not reuse the old period hint",recorder.text.toString().endsWith(" q"));
    }
    @Test public void cursorGestureBetweenShiftTapsCannotEnableCapsLock(){
        create();tap("⇧");RectF space=key("space");
        single(MotionEvent.ACTION_DOWN,space.centerX(),space.centerY());
        single(MotionEvent.ACTION_MOVE,space.centerX()-px(36),space.centerY());
        single(MotionEvent.ACTION_UP,space.centerX()-px(36),space.centerY());
        tap("⇧");tap("q");assertEquals("q",recorder.text.toString());
    }
    @Test public void emailAndPasswordFieldsStartLowercaseEvenWithSentenceFlag(){
        EditorInfo info=editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        create(info);tap("q");tap("@");assertEquals("q@",recorder.text.toString());
        info=editor(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        view.configureEditor(info);view.updateAutoShift(true);tap("w");assertEquals("q@w",recorder.text.toString());
    }
    @Test public void deliberateSpaceDragMovesCursorWithoutInsertingASpace(){
        create();RectF space=key("space");float x=space.centerX(),y=space.centerY();
        single(MotionEvent.ACTION_DOWN,x,y);single(MotionEvent.ACTION_MOVE,x+px(36),y);single(MotionEvent.ACTION_UP,x+px(36),y);
        assertEquals("",recorder.text.toString());assertEquals(3,recorder.cursor);
        tap("space");assertEquals(" ",recorder.text.toString());
    }
    @Test public void aSmallSpaceTapDriftStillTypesExactlyOneSpace(){
        create();RectF space=key("space");float x=space.centerX(),y=space.centerY();
        single(MotionEvent.ACTION_DOWN,x,y);single(MotionEvent.ACTION_MOVE,x+px(4),y);single(MotionEvent.ACTION_UP,x+px(4),y);
        assertEquals(" ",recorder.text.toString());assertEquals(0,recorder.cursor);
    }
    @Test public void cancelRemovesTheDeleteStarterBeforeItCanAffectALaterPress(){
        create();RectF delete=key("⌫");single(MotionEvent.ACTION_DOWN,delete.centerX(),delete.centerY());idle(300);
        single(MotionEvent.ACTION_CANCEL,delete.centerX(),delete.centerY());idle(1000);assertEquals(1,recorder.deletes);
        single(MotionEvent.ACTION_DOWN,delete.centerX(),delete.centerY());idle(399);assertEquals(2,recorder.deletes);
        idle(1);assertEquals(3,recorder.deletes);single(MotionEvent.ACTION_UP,delete.centerX(),delete.centerY());idle(1000);
        assertEquals(3,recorder.deletes);
    }
    @Test public void draggingAHeldDeleteOutOfItsTargetStopsAllFurtherRepeats(){
        create();RectF delete=key("⌫"),space=key("space");single(MotionEvent.ACTION_DOWN,delete.centerX(),delete.centerY());idle(500);
        assertTrue(recorder.deletes>1);int before=recorder.deletes;
        single(MotionEvent.ACTION_MOVE,space.centerX(),space.centerY());idle(1000);
        single(MotionEvent.ACTION_UP,space.centerX(),space.centerY());assertEquals(before,recorder.deletes);
        assertEquals("",recorder.text.toString());tap("q");assertEquals("q",recorder.text.toString());
    }
    @Test public void hidingTheKeyboardCancelsADeleteHeldBeforeItsRepeatStarts(){
        create();RectF delete=key("⌫");single(MotionEvent.ACTION_DOWN,delete.centerX(),delete.centerY());view.stop();idle(1000);
        assertEquals(1,recorder.deletes);
    }
    @Test public void decimalNumberFieldHasOneNumericPadAndItsOwnEnterAction(){
        EditorInfo info=editor(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        info.imeOptions=EditorInfo.IME_ACTION_DONE;create(info);
        assertNull(view.keyBoundsForTest("q"));assertNotNull(view.keyBoundsForTest("-"));tap("1");tap(".");tap("0");tap("Done");
        assertEquals("1.0",recorder.text.toString());assertEquals(1,recorder.enters);assertGeometryContained();
    }
    @Test public void symbolPagesAvoidDuplicatedDigitRowsAndReachBracketsAndCurrency(){
        create();tap("?123");int ones=0;
        for(int i=0;i<view.keyCountForTest();i++){
            AccessibilityNodeInfo node=view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(i);
            if("1".contentEquals(node.getText()))ones++;node.recycle();
        }
        assertEquals(1,ones);tap("@");tap("=\\<");tap("[");tap("€");tap("ABC");tap("q");
        assertEquals("@[€q",recorder.text.toString());
    }
    @Test public void emojiKeyOpensALocalPaletteAndKeyboardSwitchRemainsAvailable(){
        create();tap("☺");assertEquals("",recorder.text.toString());tap("🎄");tap("ABC");tap("🌐");
        assertEquals("🎄",recorder.text.toString());assertEquals(1,recorder.switches);assertNotNull(view.keyBoundsForTest("q"));
    }
    @Test public void virtualKeysSupportFocusClickAndUsefulActionDescriptions(){
        create();AccessibilityNodeProvider provider=view.getAccessibilityNodeProvider();
        assertTrue(provider.performAction(0,AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
        AccessibilityNodeInfo focus=provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);assertNotNull(focus);assertTrue(focus.isAccessibilityFocused());focus.recycle();
        assertTrue(provider.performAction(0,AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals("1",recorder.text.toString());
        assertTrue(provider.performAction(0,AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,null));
        assertNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
        assertFalse(provider.performAction(999,AccessibilityNodeInfo.ACTION_CLICK,null));
        assertFalse(provider.findAccessibilityNodeInfosByText("Switch keyboard",-1).isEmpty());
        assertFalse(provider.findAccessibilityNodeInfosByText("Delete",-1).isEmpty());
        view.setSuggestions(Arrays.asList("hello","help","holiday"));
        assertTrue(provider.performAction(1000,AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals("hello",recorder.suggestion);
    }
    @Test public void nativePortraitRenderProducesReviewableSyntheticEvidence() throws Exception {
        prefs.edit().putBoolean("keyboardRgb",true).putInt("keyboardRgbPattern",2).commit();create();
        view.setSuggestions(Arrays.asList("holiday","hello","happy"));render("portrait.png");
    }
    @Test @Config(qualifiers="w1024dp-h460dp-land-hdpi")
    public void nativeConstrainedLandscapeRenderProducesReviewableSyntheticEvidence() throws Exception {
        prefs.edit().putBoolean("keyboardRgb",true).putInt("keyboardRgbPattern",2).commit();create();measure(1024,180,View.MeasureSpec.AT_MOST);
        view.setSuggestions(Arrays.asList("holiday","hello","happy"));assertGeometryContained();render("landscape-constrained.png");
    }
    private void render(String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));File directory=new File("build/reports/keyboard-visuals");
        assertTrue(directory.isDirectory()||directory.mkdirs());File file=new File(directory,name);
        try(FileOutputStream stream=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
        bitmap.recycle();System.out.println("Native keyboard visual evidence: "+file.getAbsolutePath());
    }
}
