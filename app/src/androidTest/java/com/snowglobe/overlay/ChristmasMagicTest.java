package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChristmasMagicTest {
    private Context context;
    private UiDevice device;
    private File screenshots;

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("effectsEnabled",false)
            .putString("selectedScene","Custom").putInt("qualityMode",3).commit();
        screenshots=new File(context.getExternalFilesDir(null),"screenshots");
        assertTrue(screenshots.mkdirs()||screenshots.isDirectory());
    }
    @After public void cleanup() {
        EffectsService.pauseNow(context);
    }
    private void screenshot(String file){
        SystemClock.sleep(500);
        assertTrue(device.takeScreenshot(new File(screenshots,file)));
    }
    @Test public void pixelSetupAndChristmasScenesAreDiscoverable() throws Exception {
        try(ActivityScenario<StudioActivity> launched=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            screenshot("17-christmas-home.png");
            // Scroll by viewport portions to discover the setup and scene library.
            int cx=device.getDisplayWidth()/2, h=device.getDisplayHeight();
            device.swipe(cx,(int)(h*.76f),cx,(int)(h*.35f),16);
            assertTrue("Pixel setup must be visible",device.wait(Until.hasObject(By.text("Pixel setup")),10000));
            screenshot("17-pixel-setup-summary.png");
            // Both sections are visible after a single scroll on Pixel 6 emulators.
            // Do not scroll past the carousel before asserting its presence.
            if(!device.hasObject(By.text("Christmas magic"))){
                device.swipe(cx,(int)(h*.68f),cx,(int)(h*.52f),12);
            }
            assertTrue("Christmas scene section must be visible",device.wait(Until.hasObject(By.text("Christmas magic")),10000));
            screenshot("17-curated-scenes.png");
            assertFalse(EffectsService.isActive());
            assertFalse(PremiumPolicy.mayLaunchCheckout());
        }
    }
    @Test public void sceneSelectionIsDeterministicAndDoesNotTurnEffectsOn(){
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putBoolean("effectsEnabled",false).putBoolean("reduceMotion",true).putInt("qualityMode",3).commit();
        for(SceneCatalog.Preset preset:SceneCatalog.presets()){
            assertTrue(SceneCatalog.apply(prefs,preset.name));
            assertEquals(preset.name,prefs.getString("selectedScene",null));
            assertEquals(3,prefs.getInt("qualityMode",-1));
            assertTrue(prefs.getBoolean("reduceMotion",false));
            assertFalse(prefs.getBoolean("effectsEnabled",true));
        }
    }
    @Test public void rendererCanDrawAndSwitchEverySceneWithoutSurfaceCrashes(){
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        Bitmap bitmap=Bitmap.createBitmap(400,860,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        try{
            SceneRenderer renderer=new SceneRenderer(2f,new SceneSettings(prefs));
            renderer.resize(400,860,34,0,2f);
            long time=1_000_000_000L;
            for(SceneCatalog.Preset preset:SceneCatalog.presets()){
                assertTrue(SceneCatalog.apply(prefs,preset.name));
                renderer.apply(new SceneSettings(prefs));
                for(int frame=0;frame<12;frame++){
                    time+=16_666_667L;
                    renderer.advance(time);
                    renderer.draw(canvas,true);
                }
            }
        }finally{bitmap.recycle();}
    }
}
