package com.snowglobe.overlay;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class KeyboardPrivacyInstrumentedTest {
    @Test public void passwordsAndNoPersonalizedLearningAlwaysDisableSuggestions() {
        EditorInfo password=new EditorInfo();
        password.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;
        assertTrue(KeyboardTextEngine.privateEditor(password));
        assertFalse(KeyboardTextEngine.ordinaryText(password));
        EditorInfo numericPassword=new EditorInfo();
        numericPassword.inputType=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        assertTrue(KeyboardTextEngine.privateEditor(numericPassword));
        EditorInfo protectedEditor=new EditorInfo();
        protectedEditor.inputType=InputType.TYPE_CLASS_TEXT;
        protectedEditor.imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        assertTrue(KeyboardTextEngine.privateEditor(protectedEditor));
        assertFalse(KeyboardTextEngine.ordinaryText(protectedEditor));
        EditorInfo web=new EditorInfo();
        web.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        assertFalse(KeyboardTextEngine.ordinaryText(web));
        EditorInfo ordinary=new EditorInfo();ordinary.inputType=InputType.TYPE_CLASS_TEXT;
        assertTrue(KeyboardTextEngine.ordinaryText(ordinary));
    }
}
