package com.snowglobe.overlay;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OverlaySafetyTest {
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    private Context context;
    private UiDevice device;
    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        assertTrue(Settings.canDrawOverlays(context));
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("_coverageRestored0215",true).putBoolean("edgeOnly",false).putBoolean("snow",true).putBoolean("lights",true)
            .putInt("density",55).putInt("snowVisibility",100).commit();
    }
    @After public void cleanup() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        device.executeShellCommand("am force-stop com.snowglobe.taptest");
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("edgeOnly",false).commit();
    }
    private void awaitOverlay(boolean attached) {
        long until=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<until){
            boolean[] result={false};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->result[0]=EffectsService.isOverlayAttached());
            if(result[0]==attached)return;SystemClock.sleep(100);
        }
        fail("Overlay attachment did not become "+attached);
    }
    @Test public void separateAppReceivesNormalTapsAndPauseRestoresProtectedTaps() throws Exception {
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("effectsEnabled",true).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            device.pressHome();
            String launch=device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");
            assertFalse(launch,launch.contains("Error"));
            awaitOverlay(true);
            UiObject2 normal=device.wait(Until.findObject(By.text("NORMAL 0")),5000);assertNotNull(normal);normal.click();
            assertTrue("Ordinary cross-UID taps must pass through",device.wait(Until.hasObject(By.text("NORMAL 1")),5000));
            UiObject2 secure=device.wait(Until.findObject(By.text("PROTECTED 0")),5000);assertNotNull(secure);secure.click();
            SystemClock.sleep(250);
            // Android is allowed to reject this tap. Never disable the probe's protection.
            assertTrue(device.hasObject(By.text("PROTECTED 0"))||device.hasObject(By.text("PROTECTED 1")));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
            awaitOverlay(false);
            UiObject2 button=device.findObject(By.textStartsWith("PROTECTED "));assertNotNull(button);
            int before=Integer.parseInt(button.getText().substring("PROTECTED ".length()));button.click();
            assertTrue("A protected tap must work after removing the window",device.wait(Until.hasObject(By.text("PROTECTED "+(before+1))),5000));
            File output=new File(context.getExternalFilesDir(null),"screenshots");assertTrue(output.mkdirs()||output.isDirectory());
            assertTrue(device.takeScreenshot(new File(output,"09-tap-safety.png")));
        }
        assertFalse(context.getSharedPreferences("snowglobe",0).getBoolean("effectsEnabled",true));
    }
    @Test public void reopeningStudioDoesNotUndoExplicitPause() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            assertTrue(device.wait(Until.hasObject(By.text("Start over-app effects")),8000));
            device.pressHome();awaitOverlay(false);
        }
    }
    @Test public void topEdgeModeLeavesProtectedCentralButtonTouchable() throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putBoolean("edgeOnly",true).putBoolean("effectsEnabled",true).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            device.pressHome();
            String launch=device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");
            assertFalse(launch,launch.contains("Error"));
            awaitOverlay(true);
            UiObject2 secure=device.wait(Until.findObject(By.text("PROTECTED 0")),8000);
            assertNotNull(secure);
            assertTrue("Test fixture button must be below the top-edge overlay",
                secure.getVisibleBounds().centerY()>device.getDisplayHeight()*.30f);
            secure.click();
            assertTrue("Protected button outside the overlay should respond without defeating Android security",
                device.wait(Until.hasObject(By.text("PROTECTED 1")),5000));
            File output=new File(context.getExternalFilesDir(null),"screenshots");assertTrue(output.mkdirs()||output.isDirectory());
            assertTrue(device.takeScreenshot(new File(output,"18-edge-mode-protected-tap.png")));
        }
    }
    @Test public void repeatedCrossAppTapsAndEditsDoNotAccumulateOverlayWindows() throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putBoolean("effectsEnabled",true).putBoolean("snow",true)
            .putBoolean("lights",true).putInt("density",100).putInt("snowVisibility",100).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));device.pressHome();
            device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");awaitOverlay(true);
            for(int batch=0;batch<10;batch++){
                prefs.edit().putBoolean("edgeOnly",batch%2==0).putInt("snowShade",batch%3).commit();
                SystemClock.sleep(100);
                for(int tap=0;tap<10;tap++){
                    int expected=batch*10+tap;
                    UiObject2 normal=device.wait(Until.findObject(By.text("NORMAL "+expected)),5000);assertNotNull(normal);
                    normal.click();
                    assertTrue("Every ordinary cross-app tap must arrive",device.wait(Until.hasObject(By.text("NORMAL "+(expected+1))),5000));
                }
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    int count=0;
                    for(android.view.View window:android.view.inspector.WindowInspector.getGlobalWindowViews())
                        if(window.getLayoutParams() instanceof android.view.WindowManager.LayoutParams
                            && ((android.view.WindowManager.LayoutParams)window.getLayoutParams()).type==android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)count++;
                    assertEquals("Only one non-touchable visual overlay may exist",1,count);
                });
            }
            UiObject2 field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000);assertNotNull(field);field.click();
            device.executeShellCommand("input text snowglobe0223");
            assertTrue("Typing in another app must remain possible under active snow",device.wait(Until.hasObject(By.textContains("snowglobe0223")),5000));
            device.pressBack();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));awaitOverlay(false);
        }
    }
    @Test public void hidingEveryEffectRemovesTheWindowAndRestoresProtectedTaps() throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putBoolean("effectsEnabled",true).putBoolean("snow",true).putBoolean("lights",true)
            .putBoolean("screenSparkles",false).putInt("snowVisibility",100).putInt("density",55).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));device.pressHome();
            device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");awaitOverlay(true);
            android.content.SharedPreferences.Editor edit=prefs.edit().putInt("snowVisibility",0).putBoolean("lights",false);
            for(String key:new String[]{"candy","gifts","trees","snowmen","bulbs","stars","confetti"})edit.putBoolean(key,false);
            edit.commit();awaitOverlay(false);
            UiObject2 secure=device.wait(Until.findObject(By.text("PROTECTED 0")),5000);assertNotNull(secure);secure.click();
            assertTrue(device.wait(Until.hasObject(By.text("PROTECTED 1")),5000));
            assertTrue("Visual edits must not erase playback choice",prefs.getBoolean("effectsEnabled",false));
            prefs.edit().putInt("snowVisibility",100).commit();awaitOverlay(true);
        }finally{prefs.edit().putBoolean("lights",true).putInt("snowVisibility",100).commit();}
    }
    @Test public void wallpaperOnlyRecoveryRemovesOverlayAndStaysPausedOnReturn() throws Exception {
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("effectsEnabled",true).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            androidx.test.uiautomator.UiScrollable list=new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().scrollable(true));
            list.scrollTextIntoView("Use wallpaper only");
            UiObject2 recovery=device.wait(Until.findObject(By.text("Use wallpaper only")),5000);assertNotNull(recovery);recovery.click();
            assertFalse(context.getSharedPreferences("snowglobe",0).getBoolean("effectsEnabled",true));
            awaitOverlay(false);device.pressBack();device.pressHome();awaitOverlay(false);
        }
        try(ActivityScenario<StudioActivity> again=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Start over-app effects")),15000));
            device.pressHome();awaitOverlay(false);
        }
    }

}
