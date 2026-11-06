package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class KeyboardSmokeTest {
    private UiDevice device;
    private Context context;
    private String previousIme;
    private final String component="com.snowglobe.overlay/.ChristmasKeyboardService";
    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        previousIme=device.executeShellCommand("settings get secure default_input_method").trim();
        SharedPreferences p=context.getSharedPreferences("snowglobe",0);
        p.edit().putBoolean("effectsEnabled",false).putBoolean("keyboardRgb",false).commit();
    }
    @After public void cleanup() throws Exception {
        if(previousIme!=null&&!previousIme.isEmpty()&&!"null".equals(previousIme))
            device.executeShellCommand("ime set "+previousIme);
    }
    @Test public void keyboardCanCommitCharactersIntoAnotherApp() throws Exception {
        String enable=device.executeShellCommand("ime enable "+component);
        assertFalse("Keyboard must register with Android",enable.contains("Error"));
        String selected=device.executeShellCommand("ime set "+component);
        assertFalse("Keyboard must be selectable",selected.contains("Error"));
        String started=device.executeShellCommand("am start -W -n com.snowglobe.taptest/.TapProbeActivity");
        assertFalse("Typing probe must install",started.contains("Error"));
        UiObject2 field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),12000);
        assertNotNull("Probe input field",field);
        field.click();
        UiObject2 q=device.wait(Until.findObject(By.text("Q")),10000);
        assertNotNull("SnowGlobe keyboard key Q must be visible",q);
        q.click();
        assertTrue("Q key must commit to another app",device.wait(Until.hasObject(By.text("Q")),6000));
        assertEquals("Q",field.getText());
        UiObject2 backspace=device.wait(Until.findObject(By.text("⌫")),5000);
        assertNotNull(backspace);backspace.click();
        SystemClock.sleep(300);
        String after=field.getText();
        assertTrue("Backspace must delete text",after.isEmpty()||"TYPE HERE".equals(after));
    }
}
