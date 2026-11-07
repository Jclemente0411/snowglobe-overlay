package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
        SharedPreferences p=context.getSharedPreferences("snowglobe",0);
        p.edit().putBoolean("effectsEnabled",false).putBoolean("keyboardRgb",true).putInt("keyboardRgbBrightness",90)
            .putInt("keyboardRgbPattern",0).putBoolean("keyboardAutoCorrect",true).putBoolean("keyboardSuggestions",true).commit();
    }
    @After public void cleanup() throws Exception {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0");
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
        File images=new File(context.getExternalFilesDir(null),"screenshots");
        assertTrue(images.mkdirs()||images.isDirectory());
        device.takeScreenshot(new File(images,"21-fast-keyboard-layout.png"));
        UiObject2 q=device.wait(Until.findObject(By.text("Q")),10000);
        if(q==null)q=device.findObject(By.desc("Q"));
        if(q==null){
            device.takeScreenshot(new File(images,"21-missing-virtual-key.png"));
            device.dumpWindowHierarchy(new File(images,"21-keyboard-accessibility.xml"));
            captureImeMetadata(images);
        }
        assertNotNull("SnowGlobe keyboard key Q must be visible",q);
        UiObject2 space=device.wait(Until.findObject(By.text("space")),2000);
        if(space==null)space=device.findObject(By.desc("space"));
        assertNotNull("Spacebar must be accessible",space);
        Rect spaceBounds=space.getVisibleBounds();
        assertTrue("Spacebar must not cover system bottom gesture area",
            spaceBounds.bottom<device.getDisplayHeight()-10);
        q.click();
        assertTrue("Q key must commit to another app",device.wait(Until.hasObject(By.text("Q")),6000));
        assertEquals("Q",field.getText());
        UiObject2 backspace=device.wait(Until.findObject(By.text("⌫")),5000);
        assertNotNull(backspace);backspace.click();
        SystemClock.sleep(300);
        String after=field.getText();
        assertTrue("Backspace must delete text",after.isEmpty()||"TYPE HERE".equals(after));
        for(char ch:new char[]{'t','e','h'}){
            String lower=String.valueOf(ch),upper=lower.toUpperCase(java.util.Locale.ROOT);
            UiObject2 letter=device.wait(Until.findObject(By.text(lower)),2500);
            if(letter==null)letter=device.wait(Until.findObject(By.text(upper)),2500);
            if(letter==null)letter=device.findObject(By.desc(lower));
            if(letter==null)letter=device.findObject(By.desc(upper));
            if(letter==null){
                device.takeScreenshot(new File(images,"23-missing-typing-key.png"));
                device.dumpWindowHierarchy(new File(images,"23-typing-key-hierarchy.xml"));
            }
            assertNotNull("Expected spelling test letter "+ch,letter);
            letter.click();
        }
        UiObject2 spacing=device.wait(Until.findObject(By.text("space")),5000);
        assertNotNull(spacing);spacing.click();
        SystemClock.sleep(350);
        assertTrue("Autocorrect should replace common transposition",field.getText().trim().equalsIgnoreCase("the"));
        UiObject2 erase=device.wait(Until.findObject(By.text("⌫")),5000);
        assertNotNull(erase);erase.click();
        SystemClock.sleep(250);
        assertTrue("Backspace must undo autocorrect",field.getText().equalsIgnoreCase("teh"));
        device.takeScreenshot(new File(images,"23-autocorrect-and-rgb.png"));
    }
    private void captureImeMetadata(File images) throws Exception {
        // Retain only editor flags, cursor offsets and lifecycle state; never retain editor text.
        String dump=device.executeShellCommand("dumpsys input_method");
        Matcher fields=Pattern.compile("(?:inputType|imeOptions|initialSelStart|initialSelEnd|initialCapsMode|mInputStarted|mInputViewStarted|mDecorViewVisible|mShowInputRequested|mCurMethodId)=[^\\s,;}]+").matcher(dump);
        StringBuilder metadata=new StringBuilder();
        while(fields.find())metadata.append(fields.group()).append('\n');
        try(FileOutputStream output=new FileOutputStream(new File(images,"21-ime-metadata.txt"))){
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
