package com.snowglobe.overlay;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.*;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses the actual system picker. Does not grant a privileged wallpaper permission. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=34)
@android.annotation.TargetApi(34)
public class WallpaperSmokeTest {
    @Test public void wallpaperIsSelectableAndDrawsAfterWake() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        prefs.edit().putBoolean("effectsEnabled",false).putBoolean("wallpaperSnow",true)
            .putBoolean("wakeSnow",true).putBoolean("reduceMotion",false).commit();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Configurator.getInstance().setWaitForIdleTimeout(500);
        File images=new File(context.getExternalFilesDir(null),"screenshots");images.mkdirs();
        WallpaperManager wallpapers=WallpaperManager.getInstance(context);
        try(ActivityScenario<StudioActivity> activity=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),10000));
            activity.onActivity(a->a.startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(a,SnowWallpaperService.class))));
            UiObject2 apply=device.wait(Until.findObject(By.text(Pattern.compile("(?i)(set wallpaper|apply|set as wallpaper)"))),15000);
            device.takeScreenshot(new File(images,"06-wallpaper-picker.png"));
            if(apply==null){device.dumpWindowHierarchy(new File(images,"wallpaper-picker.xml"));fail("System wallpaper picker did not expose its apply button");}
            apply.click();
            UiObject2 both=device.wait(Until.findObject(By.text(Pattern.compile("(?i)home.*lock.*"))),6000);
            if(both!=null)both.click();
            else {
                UiObject2 lock=device.findObject(By.text(Pattern.compile("(?i)lock screen")));
                if(lock!=null)lock.click();
            }
            long deadline=SystemClock.uptimeMillis()+10000;
            boolean selected=false;
            while(SystemClock.uptimeMillis()<deadline){
                WallpaperInfo info=wallpapers.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
                selected=info!=null&&info.getPackageName().equals(context.getPackageName());
                if(selected)break;
                SystemClock.sleep(200);
            }
            device.takeScreenshot(new File(images,"07-wallpaper-selected.png"));
            if(!selected)device.dumpWindowHierarchy(new File(images,"wallpaper-selection.xml"));
            assertTrue("SnowGlobe must actually be selected for the lock screen",selected);
            device.pressHome();SystemClock.sleep(600);
            device.sleep();SystemClock.sleep(600);
            long wake=System.currentTimeMillis();
            device.wakeUp();
            deadline=SystemClock.uptimeMillis()+10000;
            while(SystemClock.uptimeMillis()<deadline&&prefs.getLong("_wallpaperFrameAt",0)<wake)SystemClock.sleep(100);
            assertTrue("The live wallpaper must draw after screen wake",prefs.getLong("_wallpaperFrameAt",0)>=wake);
            assertNull("No swallowed wallpaper render failure",prefs.getString("_wallpaperError",null));
            SystemClock.sleep(600);assertTrue(device.takeScreenshot(new File(images,"08-lock-screen-awake.png")));
            device.executeShellCommand("wm dismiss-keyguard");
        }
    }
}
