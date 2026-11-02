package com.snowglobe.overlay;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StudioSmokeTest {
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    @Test public void navigatesAndCapturesActualUi() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("snowglobe",0).edit().putBoolean("effectsEnabled",false).putString("selectedScene","Custom").commit();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        File screenshots=new File(context.getExternalFilesDir(null),"screenshots");assertTrue(screenshots.mkdirs()||screenshots.isDirectory());
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            device.takeScreenshot(new File(screenshots,"00-launch.png"));
            assertTrue("Home title must be visible",device.wait(Until.hasObject(By.text("Christmas Studio")),30000));
            Thread.sleep(700);assertTrue(device.takeScreenshot(new File(screenshots,"01-home.png")));
            UiObject2 lights=device.wait(Until.findObject(By.text("Lights")),5000);assertNotNull(lights);lights.click();
            assertTrue(device.wait(Until.hasObject(By.text("Signature lights")),10000));
            Thread.sleep(700);assertTrue(device.takeScreenshot(new File(screenshots,"02-lights.png")));
            device.findObject(By.text("Snow")).click();
            assertTrue(device.wait(Until.hasObject(By.text("Snowfall")),10000));
            assertTrue(device.takeScreenshot(new File(screenshots,"03-snow.png")));
            device.findObject(By.text("Decor")).click();
            assertTrue(device.wait(Until.hasObject(By.text("Holiday details")),10000));
            assertTrue(device.takeScreenshot(new File(screenshots,"04-decor.png")));
            device.findObject(By.text("Home")).click();
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),10000));
        }
        try {
            device.executeShellCommand("settings put system font_scale 1.3");
            try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
                assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),15000));
                Thread.sleep(700);assertTrue(device.takeScreenshot(new File(screenshots,"05-large-text.png")));
                UiObject2 lights=device.wait(Until.findObject(By.text("Lights")),5000);assertNotNull(lights);lights.click();
                assertTrue(device.wait(Until.hasObject(By.text("Signature lights")),10000));
            }
        } finally {device.executeShellCommand("settings put system font_scale 1.0");}
    }
}
