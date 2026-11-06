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
            // Depend on the actual destination controls, not a header that may animate during startup.
            SystemClock.sleep(700);
            screenshot("17-christmas-home.png");
            // Scroll by viewport portions to discover the setup and scene library.
            int cx=device.getDisplayWidth()/2, h=device.getDisplayHeight();
            device.swipe(cx,(int)(h*.76f),cx,(int)(h*.35f),16);
            assertTrue("Pixel setup must be visible",device.wait(Until.hasObject(By.text("Pixel setup")),10000));
            screenshot("17-pixel-setup-summary.png");
            // Both sections are visible after a single scroll on Pixel 6 emulators.
            // Do not scroll past the carousel before asserting its presence.
            for(int attempt=0;attempt<6&&!device.hasObject(By.text("Christmas magic"));attempt++){
                device.swipe(cx,(int)(h*.72f),cx,(int)(h*.40f),18);
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
    @Test public void snowVisibilityAndShadeAffectRenderedPixelsWithoutChangingSafetyState(){
        SharedPreferences p=context.getSharedPreferences("snowglobe",0);
        try{
            p.edit().putBoolean("effectsEnabled",false).putBoolean("lights",false).putBoolean("snow",true)
                .putBoolean("screenSparkles",false).putBoolean("edgeOnly",false)
                .putBoolean("candy",false).putBoolean("gifts",false).putBoolean("trees",false)
                .putBoolean("snowmen",false).putBoolean("bulbs",false).putBoolean("stars",false)
                .putBoolean("confetti",false).putInt("density",90)
                .putInt("snowVisibility",0).putInt("snowShade",0).commit();
            Bitmap bmp=Bitmap.createBitmap(480,900,Bitmap.Config.ARGB_8888);
            try{
                SceneRenderer scene=new SceneRenderer(3f,new SceneSettings(p));
                scene.resize(480,900,0,0,3f);
                scene.draw(new Canvas(bmp),false);
                assertEquals(0,nonTransparent(bmp));
                p.edit().putInt("snowVisibility",100).commit();
                scene.apply(new SceneSettings(p));scene.draw(new Canvas(bmp),false);
                assertTrue("Bright snow must draw",nonTransparent(bmp)>100);
                long bright=luma(bmp);
                bmp.eraseColor(0);
                p.edit().putInt("snowShade",2).commit();
                scene.apply(new SceneSettings(p));scene.draw(new Canvas(bmp),false);
                assertTrue("Dark snow must draw",nonTransparent(bmp)>100);
                assertTrue("Charcoal flakes must be darker than white flakes",luma(bmp)<bright);
                assertFalse("Changing snow never starts the overlay",EffectsService.isActive());
                assertFalse(PremiumPolicy.mayLaunchCheckout());
            }finally{bmp.recycle();}
        }finally{p.edit().putInt("snowVisibility",100).putInt("snowShade",0).putBoolean("edgeOnly",false).commit();}
    }
    @Test public void presetsDoNotResetGlobalVisibilityShadeOrTouchSafety(){
        SharedPreferences p=context.getSharedPreferences("snowglobe",0);
        try{
            p.edit().putInt("snowVisibility",21).putInt("snowShade",2).putBoolean("edgeOnly",true)
                .putBoolean("effectsEnabled",false).commit();
            for(SceneCatalog.Preset scene:SceneCatalog.presets()){
                assertTrue(SceneCatalog.apply(p,scene.name));
                assertEquals(21,p.getInt("snowVisibility",-1));
                assertEquals(2,p.getInt("snowShade",-1));
                assertTrue(p.getBoolean("edgeOnly",false));
                assertFalse(p.getBoolean("effectsEnabled",true));
            }
        }finally{p.edit().putInt("snowVisibility",100).putInt("snowShade",0).putBoolean("edgeOnly",false).commit();}
    }
    @Test public void snowControlsAreVisibleInStudio() throws Exception {
        try(ActivityScenario<StudioActivity> launched=ActivityScenario.launch(StudioActivity.class)){
            // Depend on the actual destination controls, not a header that may animate during startup.
            SystemClock.sleep(700);
            UiObject2 tab=device.wait(Until.findObject(By.text("Snow")),15000);
            assertNotNull(tab);tab.click();
            assertTrue(device.wait(Until.hasObject(By.text("Make snow visible")),10000));
            screenshot("18-snow-visibility-controls.png");
        }
    }
    private int nonTransparent(Bitmap bmp){
        int[] pixels=new int[bmp.getWidth()*bmp.getHeight()];bmp.getPixels(pixels,0,bmp.getWidth(),0,0,bmp.getWidth(),bmp.getHeight());
        int count=0;for(int color:pixels)if((color>>>24)!=0)count++;return count;
    }
    private long luma(Bitmap bmp){
        int[] pixels=new int[bmp.getWidth()*bmp.getHeight()];bmp.getPixels(pixels,0,bmp.getWidth(),0,0,bmp.getWidth(),bmp.getHeight());
        long sum=0;for(int color:pixels)if((color>>>24)!=0)sum+=((color>>16)&255)+((color>>8)&255)+(color&255);
        return sum;
    }
}
