package com.snowglobe.overlay;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.io.FileWriter;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/** Makes test-environment state explicit; never changes the production app. */
public final class DeviceEvidence extends TestWatcher {
    @Override protected void starting(Description description) {
        try {
            UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            Configurator.getInstance().setWaitForIdleTimeout(500);
            d.wakeUp();d.executeShellCommand("wm dismiss-keyguard");
            d.executeShellCommand("settings put system screen_off_timeout 1800000");
            d.pressHome();d.waitForIdle(1500);
        } catch(Exception error){throw new AssertionError("Could not initialize the emulator display",error);}
    }
    @Override protected void failed(Throwable error,Description description) {
        try {
            Context c=ApplicationProvider.getApplicationContext();
            File folder=new File(c.getExternalFilesDir(null),"screenshots");folder.mkdirs();
            UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            d.takeScreenshot(new File(folder,"FAILED-"+description.getClassName()+".png"));
            d.dumpWindowHierarchy(new File(folder,"FAILED-"+description.getClassName()+".xml"));
            try(FileWriter out=new FileWriter(new File(folder,"FAILED-"+description.getClassName()+".txt"))){
                out.write(error.toString()+"\n"+d.executeShellCommand("dumpsys window windows"));
            }
        } catch(Exception capture){android.util.Log.e("SnowGlobeTest","Failure evidence capture failed",capture);}
    }
}
