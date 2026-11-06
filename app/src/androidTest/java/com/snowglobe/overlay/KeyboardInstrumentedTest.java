package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Tests the actual IME on another app/UID, with no over-app effects enabled. */
@RunWith(AndroidJUnit4.class)
public final class KeyboardInstrumentedTest {
    private static final String IME="com.snowglobe.overlay/.ChristmasKeyboardService";
    private UiDevice device;
    private Context context;
    private String previousIme;
    @Before public void setUp() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        previousIme=device.executeShellCommand("settings get secure default_input_method").trim();
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        device.executeShellCommand("ime enable "+IME);
        String result=device.executeShellCommand("ime set "+IME);
        assertFalse("Cannot select IME: "+result,result.contains("Error"));
        String launch=device.executeShellCommand("am start -W -n com.snowglobe.taptest/.KeyboardProbeActivity");
        assertFalse("Cannot launch fixture: "+launch,launch.contains("Error"));
    }
    @After public void tearDown() throws Exception {
        if(previousIme!=null&&!previousIme.isEmpty()&&!"null".equals(previousIme))
            device.executeShellCommand("ime set "+previousIme);
        device.executeShellCommand("am force-stop com.snowglobe.taptest");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
    }
    private UiObject2 field(String label){
        UiObject2 view=device.wait(Until.findObject(By.desc(label+"_FIELD")),8000);
        assertNotNull("No "+label+" field",view);view.click();return view;
    }
    private void key(String label){
        UiObject2 button=device.wait(Until.findObject(By.text(label)),5000);
        assertNotNull("Missing keyboard key "+label,button);button.click();
    }
    @Test public void textEntryAndPrivacyFields() {
        UiObject2 plain=field("PLAIN");
        assertNotNull("Gemini button expected on ordinary fields",
            device.wait(Until.findObject(By.text("✦ Gemini")),5000));
        key("H");key("i");
        assertEquals("Hi",plain.getText());
        field("PASSWORD");
        SystemClock.sleep(200);
        assertFalse("Gemini must not show over password entry",device.hasObject(By.text("✦ Gemini")));
        assertTrue("Secure field must clearly be marked private",device.hasObject(By.text("Private input")));
        key("a");
    }
    @Test public void emailAndNumberFieldsHaveTheirOwnLayouts() {
        field("EMAIL");assertNotNull(device.wait(Until.findObject(By.text("@")),5000));
        field("NUMBER");
        assertNotNull(device.wait(Until.findObject(By.text("1")),5000));
        assertFalse("Numeric layout must omit alphabetic Q",device.hasObject(By.text("Q")));
        key("1");key("2");
    }
}
