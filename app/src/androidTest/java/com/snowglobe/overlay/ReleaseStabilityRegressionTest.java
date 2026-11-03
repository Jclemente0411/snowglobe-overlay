package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Revalidation-only scenarios for build 18.
 * No performance speedup claims: log elapsed time and verify stability across state churn.
 */
@RunWith(AndroidJUnit4.class)
public class ReleaseStabilityRegressionTest {
    private static final String TAG="SnowGlobeStress";
    private Context context;
    private UiDevice device;

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        assertTrue(Settings.canDrawOverlays(context));
        // Each test starts with a safe paused state; never inherit a running service.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
    }

    @After public void cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        context.getSharedPreferences("snowglobe",0).edit()
            .putBoolean("edgeOnly",false).putInt("snowShade",0)
            .putInt("snowVisibility",100).commit();
    }

    private void awaitOverlay(boolean attached) {
        long deadline=SystemClock.uptimeMillis()+10000L;
        while(SystemClock.uptimeMillis()<deadline) {
            final boolean[] result={false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                ()->result[0]=EffectsService.isOverlayAttached());
            if(result[0]==attached)return;
            SystemClock.sleep(125);
        }
        fail("Timed out waiting for overlay attached="+attached);
    }

    @Test public void renderOneThousandFramesAcrossChangingScenesWallpaperAndContrasts() {
        // Reuse a single bitmap and one renderer; exercise highest-density scenes,
        // snow opacity, shade, resize, wallpaper wake and light modes together.
        SharedPreferences prefs=context.getSharedPreferences("snowglobe_qa18_stress",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Bitmap bitmap=Bitmap.createBitmap(320,680,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        int frames=0;
        long first=SystemClock.elapsedRealtime(),nanos=1_000_000_000L;
        try {
            prefs.edit().putInt("density",100).putInt("snowVisibility",100)
                .putInt("qualityMode",3).putBoolean("lights",true).commit();
            SceneRenderer renderer=new SceneRenderer(2.5f,new SceneSettings(prefs));
            renderer.resize(320,680,50,36,2.5f);
            for(int i=0;i<10;i++) {
                SceneCatalog.Preset scene=SceneCatalog.presets().get(i%SceneCatalog.presets().size());
                assertTrue(SceneCatalog.apply(prefs,scene.name));
                prefs.edit().putInt("snowVisibility",i%3==0?0:i%3==1?25:100)
                    .putInt("snowShade",i%3).putBoolean("edgeOnly",i%2==0)
                    .putInt("density",i%2==0?100:42)
                    .putBoolean("reduceMotion",i==7).commit();
                renderer.apply(new SceneSettings(prefs));
                boolean wallpaper=i%2==1;
                renderer.setWallpaper(wallpaper);
                renderer.resize(320,wallpaper?680:460,50,36,2.5f);
                if(wallpaper)renderer.wake();
                for(int step=0;step<100;step++) {
                    nanos+=16_666_667L;
                    renderer.advance(nanos);
                    renderer.draw(canvas,step%4==0);
                    frames++;
                }
                renderer.resetClock();
            }
            assertEquals(1000,frames);
            assertFalse(PremiumPolicy.PAYWALL_VISIBLE);
            assertFalse(PremiumPolicy.mayLaunchCheckout());
            assertFalse(EffectsService.isActive());
        } finally {
            Log.i(TAG,"drawnFrames="+frames+" totalElapsedMs="+
                (SystemClock.elapsedRealtime()-first)+" (emulator stress only, not FPS)");
            bitmap.recycle();
            prefs.edit().clear().commit();
        }
    }

    @Test public void overlayResizesRepeatedlyAndStillPausesWithoutTouchInterference()
        throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
        prefs.edit().putBoolean("effectsEnabled",true).putBoolean("edgeOnly",true)
            .putInt("snowVisibility",75).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            device.pressHome();
            awaitOverlay(true);
            long start=SystemClock.elapsedRealtime();
            for(int i=0;i<8;i++) {
                prefs.edit().putBoolean("edgeOnly",i%2==0)
                    .putInt("snowShade",i%3)
                    .putInt("snowVisibility",i*13%101)
                    .putInt("density",15+i*10).commit();
                awaitOverlay(true);
                assertTrue("Service must remain enabled through edits",EffectsService.isActive());
                SystemClock.sleep(150);
            }
            Log.i(TAG,"geometryChanges=8 elapsedMs="+(SystemClock.elapsedRealtime()-start));
            // Main-thread removal is the same path used by the user-facing Pause.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                ()->EffectsService.pauseNow(context));
            awaitOverlay(false);
            assertFalse("A manual Pause must remain off",prefs.getBoolean("effectsEnabled",true));
            // Preferences and presets can no longer auto-resume it.
            for(int i=0;i<5;i++) {
                assertTrue(SceneCatalog.apply(prefs,SceneCatalog.presets().get(i).name));
            }
            SystemClock.sleep(300);
            awaitOverlay(false);
            assertFalse(EffectsService.isActive());
        }
    }

    @Test public void settingsEditsCannotReactivateAPausedOverlay() throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            ()->EffectsService.pauseNow(context));
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)) {
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            for(int i=0;i<40;i++) {
                prefs.edit().putInt("snowVisibility",i*7%101)
                    .putInt("snowShade",i%3).putBoolean("edgeOnly",i%2==0)
                    .putInt("qualityMode",i%4).commit();
            }
            device.pressHome();
            awaitOverlay(false);
            assertFalse(prefs.getBoolean("effectsEnabled",true));
            assertFalse(EffectsService.isActive());
        }
    }
}
