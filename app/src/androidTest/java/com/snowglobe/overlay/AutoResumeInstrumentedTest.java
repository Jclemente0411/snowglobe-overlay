package com.snowglobe.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercise the actual screen-off / wake / unlock broadcasts; never reopen Studio. */
@RunWith(AndroidJUnit4.class)
public class AutoResumeInstrumentedTest {
    private Context context;
    private UiDevice device;
    private SharedPreferences prefs;

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("appops set "+context.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        assertTrue(Settings.canDrawOverlays(context));
        prefs=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            ()->EffectsService.pauseNow(context));
        prefs.edit().putBoolean("edgeOnly",false).commit();
    }
    @After public void cleanup() throws Exception {
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            ()->EffectsService.pauseNow(context));
    }
    private void awaitOverlay(boolean attached,int timeoutMs){
        long until=SystemClock.uptimeMillis()+timeoutMs;
        while(SystemClock.uptimeMillis()<until){
            boolean[] answer={false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                ()->answer[0]=EffectsService.isOverlayAttached());
            if(answer[0]==attached)return;
            SystemClock.sleep(125);
        }
        fail("Expected attached="+attached+" after unlock without opening Studio");
    }

    @Test public void unlockReturnsEffectsToHomeThreeTimesWithoutOpeningStudio() throws Exception {
        prefs.edit().putBoolean("effectsEnabled",true).commit();
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            device.pressHome();
            awaitOverlay(true,12000);
            for(int cycle=0;cycle<3;cycle++){
                device.sleep();
                awaitOverlay(false,8000);
                SystemClock.sleep(300);
                device.wakeUp();
                device.executeShellCommand("wm dismiss-keyguard");
                device.pressHome();
                awaitOverlay(true,12000);
            }
            File directory=new File(context.getExternalFilesDir(null),"screenshots");
            assertTrue(directory.mkdirs()||directory.isDirectory());
            assertTrue(device.takeScreenshot(new File(directory,"19-auto-resumed-after-unlock.png")));
            assertTrue(prefs.getBoolean("effectsEnabled",false));
            assertTrue(EffectsService.isActive());
        }
    }

    @Test public void pauseCannotBeUndoneByWakeOrPackageUpdate() throws Exception {
        prefs.edit().putBoolean("effectsEnabled",false).commit();
        new EffectsStartupReceiver().onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),20000));
            device.pressHome();
            awaitOverlay(false,1000);
            device.sleep();
            SystemClock.sleep(300);
            device.wakeUp();
            device.executeShellCommand("wm dismiss-keyguard");
            device.pressHome();
            SystemClock.sleep(750);
            awaitOverlay(false,1000);
            assertFalse(prefs.getBoolean("effectsEnabled",true));
            assertFalse(EffectsService.isActive());
        }
    }
}
