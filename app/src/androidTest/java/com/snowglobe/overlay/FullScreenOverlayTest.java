package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Checks the attached window and actual snow pixels, not only MATCH_PARENT policy. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=30)
public final class FullScreenOverlayTest {
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    private Context context;
    private UiDevice device;
    private SharedPreferences prefs;
    private Map<String,?> saved;
    private String oldIme,oldHardwareKeyboard;

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.setOrientationNatural();
        device.executeShellCommand("appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        assertTrue(Settings.canDrawOverlays(context));
        prefs=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
        saved=prefs.getAll();
        oldIme=device.executeShellCommand("settings get secure default_input_method").trim();
        oldHardwareKeyboard=device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // A fixed all-snow scene lets every quarter be checked without animation timing noise.
        prefs.edit().putBoolean("_coverageRestored0215",true).putBoolean("edgeOnly",false)
            .putBoolean("snow",true).putBoolean("lights",false).putBoolean("screenSparkles",false)
            .putBoolean("reduceMotion",true).putInt("density",100).putInt("snowVisibility",100)
            .putInt("snowStyle",0).putBoolean("candy",false).putBoolean("gifts",false)
            .putBoolean("trees",false).putBoolean("snowmen",false).putBoolean("bulbs",false)
            .putBoolean("stars",false).putBoolean("confetti",false).putBoolean("effectsEnabled",true).commit();
    }
    @After public void cleanup() throws Exception {
        if(context==null)return;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        device.pressBack();device.setOrientationNatural();device.unfreezeRotation();
        device.executeShellCommand("am force-stop com.snowglobe.taptest");
        if(oldIme!=null&&!oldIme.isEmpty()&&!"null".equals(oldIme))device.executeShellCommand("ime set "+oldIme);
        if(oldHardwareKeyboard==null||oldHardwareKeyboard.isEmpty()||"null".equals(oldHardwareKeyboard))
            device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
        else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard "+oldHardwareKeyboard);
        if(saved!=null){
            SharedPreferences.Editor editor=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Set){
                    @SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;
                    editor.putStringSet(key,strings);
                }
            }
            editor.putBoolean("effectsEnabled",false).commit();
        }
    }
    private SnowView overlay(){
        for(View root:WindowInspector.getGlobalWindowViews()){
            if(root instanceof SnowView&&root.isAttachedToWindow()
                &&root.getLayoutParams() instanceof WindowManager.LayoutParams
                &&((WindowManager.LayoutParams)root.getLayoutParams()).type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                return (SnowView)root;
        }
        return null;
    }
    private Rect bounds(){
        Rect result=new Rect();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            SnowView view=overlay();if(view==null)return;
            int[] origin=new int[2];view.getLocationOnScreen(origin);
            result.set(origin[0],origin[1],origin[0]+view.getWidth(),origin[1]+view.getHeight());
        });
        return result;
    }
    private void awaitFullSize(){
        long deadline=SystemClock.uptimeMillis()+10000;
        Rect actual=new Rect();
        while(SystemClock.uptimeMillis()<deadline){
            actual=bounds();
            if(Math.abs(actual.left)<=2&&Math.abs(actual.top)<=2
                &&Math.abs(actual.right-device.getDisplayWidth())<=2
                &&Math.abs(actual.bottom-device.getDisplayHeight())<=2)return;
            SystemClock.sleep(100);
        }
        fail("Snow window must fill the display: "+actual+" vs "+device.getDisplayWidth()+"x"+device.getDisplayHeight());
    }
    private void assertSnowInEveryQuarter(){
        int[] visible=new int[4];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            SnowView view=overlay();assertNotNull(view);
            int width=view.getWidth(),height=view.getHeight();
            assertTrue(width>0&&height>0);
            Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            try{
                view.draw(new Canvas(image));
                for(int y=0;y<height;y+=2)for(int x=0;x<width;x+=2)
                    if((image.getPixel(x,y)>>>24)>20)visible[Math.min(3,y*4/height)]++;
            }finally{image.recycle();}
        });
        for(int quarter=0;quarter<4;quarter++)
            assertTrue("Actual snow pixels missing from quarter "+(quarter+1)+": "+visible[quarter],visible[quarter]>20);
    }
    private void capture(String name){
        File directory=new File(context.getExternalFilesDir(null),"screenshots");
        assertTrue(directory.mkdirs()||directory.isDirectory());
        assertTrue(device.takeScreenshot(new File(directory,name)));
    }
    private void launchProbe() throws Exception {
        device.pressHome();
        String result=device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");
        assertFalse(result,result.contains("Error"));
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000));
    }
    private boolean visibleKeyboard(View root){
        if(root instanceof FastKeyboardView&&root.isShown()&&root.getWindowVisibility()==View.VISIBLE&&root.getHeight()>0)return true;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)if(visibleKeyboard(group.getChildAt(i)))return true;
        }
        return false;
    }
    private void awaitKeyboard(boolean expected){
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){
            boolean[] shown={false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(View root:WindowInspector.getGlobalWindowViews())if(visibleKeyboard(root)){shown[0]=true;break;}
            });
            if(shown[0]==expected)return;
            SystemClock.sleep(100);
        }
        fail("Keyboard visibility did not become "+expected);
    }
    @Test public void actualWindowAndSnowPixelsReachAllFourQuarters() throws Exception {
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            launchProbe();awaitFullSize();assertSnowInEveryQuarter();capture("27-full-height-snow.png");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                WindowManager.LayoutParams params=(WindowManager.LayoutParams)overlay().getLayoutParams();
                assertEquals(0,params.getFitInsetsTypes());
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,params.softInputMode);
                assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
                assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
                assertTrue(params.alpha<=.60f);
            });
            prefs.edit().putBoolean("edgeOnly",true).commit();
            long deadline=SystemClock.uptimeMillis()+8000;
            while(SystemClock.uptimeMillis()<deadline&&bounds().height()>device.getDisplayHeight()/2)SystemClock.sleep(100);
            assertTrue("Top-band mode remains an explicit working choice",bounds().height()>0&&bounds().height()<device.getDisplayHeight()/2);
            prefs.edit().putBoolean("edgeOnly",false).commit();
            awaitFullSize();assertSnowInEveryQuarter();
        }
    }
    @Test public void rotatingAndOpeningKeyboardCannotPermanentlyShrinkSnowfall() throws Exception {
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            launchProbe();awaitFullSize();
            device.setOrientationLeft();
            long deadline=SystemClock.uptimeMillis()+8000;
            while(SystemClock.uptimeMillis()<deadline&&device.getDisplayWidth()<=device.getDisplayHeight())SystemClock.sleep(100);
            assertTrue("Rotation must actually occur",device.getDisplayWidth()>device.getDisplayHeight());
            awaitFullSize();assertSnowInEveryQuarter();capture("28-snow-landscape.png");
            device.setOrientationNatural();
            deadline=SystemClock.uptimeMillis()+8000;
            while(SystemClock.uptimeMillis()<deadline&&device.getDisplayHeight()<=device.getDisplayWidth())SystemClock.sleep(100);
            assertTrue(device.getDisplayHeight()>device.getDisplayWidth());
            awaitFullSize();
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
            device.executeShellCommand("ime enable com.snowglobe.overlay/.ChristmasKeyboardService");
            device.executeShellCommand("ime set com.snowglobe.overlay/.ChristmasKeyboardService");
            UiObject2 field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000);assertNotNull(field);field.click();
            awaitKeyboard(true);awaitFullSize();assertSnowInEveryQuarter();capture("29-snow-with-keyboard.png");
            device.pressBack();awaitKeyboard(false);awaitFullSize();assertSnowInEveryQuarter();capture("30-snow-after-keyboard.png");
        }
    }
}
