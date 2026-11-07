package com.snowglobe.overlay;

import android.content.*;
import android.os.SystemClock;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HolidayAppearanceTest {
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    private Context context;
    private UiDevice device;
    private File screenshots;
    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("effectsEnabled",false).putBoolean("pixelColors",false).commit();
        context.getSharedPreferences(HolidayAppearanceStore.FILE,0).edit().clear().commit();
        screenshots=new File(context.getExternalFilesDir(null),"screenshots");assertTrue(screenshots.mkdirs()||screenshots.isDirectory());
    }
    @After public void reset() throws Exception {device.executeShellCommand("settings put system font_scale 1.0");context.getSharedPreferences(HolidayAppearanceStore.FILE,0).edit().putString("theme","classic").commit();}
    private void clickDescription(String value){UiObject2 object=device.wait(Until.findObject(By.desc(value)),10000);assertNotNull(value,object);object.click();}
    private void capture(String name){SystemClock.sleep(400);assertTrue(device.takeScreenshot(new File(screenshots,name)));}
    @Test public void themesPersistAndNeverMutateEffectsOrBilling() throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putInt("qualityMode",0).putInt("palette",3).putInt("speed",67).putBoolean("reduceMotion",true).commit();
        Map<String,?> before=new HashMap<>(prefs.getAll());
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));capture("16-classic-home.png");
            clickDescription("Appearance");assertTrue(device.wait(Until.hasObject(By.text("Studio appearance")),5000));capture("16-appearance-picker.png");
            clickDescription("Use Candlelight theme");device.findObject(By.text("Done")).click();
            assertEquals("candlelight",new HolidayAppearanceStore(context).theme());capture("16-candlelight-home.png");
            activity.recreate();assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            assertEquals("candlelight",new HolidayAppearanceStore(context).theme());
            clickDescription("Appearance");clickDescription("Use Evergreen Lodge theme");device.findObject(By.text("Done")).click();capture("16-evergreen-home.png");
            clickDescription("Appearance");clickDescription("Use Pixel colors theme");device.findObject(By.text("Done")).click();capture("16-pixel-home.png");
            for(String key:new String[]{"effectsEnabled","qualityMode","palette","speed","reduceMotion"})assertEquals(key,before.get(key),prefs.getAll().get(key));
            assertFalse(EffectsService.isActive());assertTrue(PremiumPolicy.PAYWALL_VISIBLE);
            assertFalse("An appearance change cannot bypass receipt verification",PremiumPolicy.allFeaturesAvailable());
        }finally{prefs.edit().putBoolean("reduceMotion",false).putInt("qualityMode",1).commit();}
    }
    @Test public void settingsAndWallpaperControlsRemainReachableAtLargeText() throws Exception {
        device.executeShellCommand("settings put system font_scale 1.3");
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            clickDescription("Settings and safety");assertTrue(device.wait(Until.hasObject(By.text("Pause & open Google Play")),8000));capture("16-settings-large-text.png");
            device.findObject(By.text("Done")).click();
            UiObject2 lights=device.wait(Until.findObject(By.text("Lights")),10000);assertNotNull(lights);lights.click();
            assertTrue(device.wait(Until.hasObject(By.text("Signature lights")),10000));capture("16-lights-large-text.png");
        }
    }
    @Test public void appearanceStoreNeverWritesSceneOrPlaybackPreferences(){
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);Map<String,?> original=new HashMap<>(prefs.getAll());
        HolidayAppearanceStore appearance=new HolidayAppearanceStore(context);appearance.select("candlelight");appearance.select("evergreen");
        assertEquals(original,prefs.getAll());assertEquals("evergreen",new HolidayAppearanceStore(context).theme());
    }
}
