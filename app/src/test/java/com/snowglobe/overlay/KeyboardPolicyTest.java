package com.snowglobe.overlay;

import android.text.InputType;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Host-JVM policy checks; device IME behavior is exercised by instrumentation tests. */
public class KeyboardPolicyTest {
    @Test public void privateFieldsNeverQualifyAsNormalTyping(){
        assertTrue(KeyboardPolicy.sensitive(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertTrue(KeyboardPolicy.sensitive(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertTrue(KeyboardPolicy.sensitive(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertTrue(KeyboardPolicy.sensitive(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        assertFalse(KeyboardPolicy.sensitive(InputType.TYPE_CLASS_TEXT));
    }
    @Test public void layoutSelectsEditorSpecificKeys(){
        assertEquals(KeyboardPolicy.Layout.PHONE,KeyboardPolicy.layout(InputType.TYPE_CLASS_PHONE));
        assertEquals(KeyboardPolicy.Layout.NUMBER,KeyboardPolicy.layout(InputType.TYPE_CLASS_NUMBER));
        assertEquals(KeyboardPolicy.Layout.EMAIL,KeyboardPolicy.layout(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertEquals(KeyboardPolicy.Layout.URL,KeyboardPolicy.layout(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI));
        assertEquals(KeyboardPolicy.Layout.LETTERS,KeyboardPolicy.layout(InputType.TYPE_CLASS_TEXT));
    }
    @Test public void offlineCompletionsAreBoundedAndNeverLearn(){
        List<String> results=KeyboardPolicy.complete("sno");
        assertTrue(results.contains("snow"));assertTrue(results.size()<=3);
        assertTrue(KeyboardPolicy.complete("A").isEmpty());
        assertTrue(KeyboardPolicy.complete("nonexistentprivateword").isEmpty());
    }
    @Test public void animationIsBoundedAndBatterySaverWins(){
        assertEquals(0,KeyboardPolicy.animationDelayMs(100,true));
        assertTrue(KeyboardPolicy.animationDelayMs(100,false)>=130);
        assertTrue(KeyboardPolicy.animationDelayMs(0,false)<=420);
    }
}
