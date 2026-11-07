package com.snowglobe.overlay;

import android.app.Activity;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Covers the service lifecycle and asynchronous caps query with Android's real EditText connection. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class KeyboardServiceRegressionTest {
    private ActivityController<Activity> activityController;
    private ServiceController<ChristmasKeyboardService> serviceController;
    private ChristmasKeyboardService service;
    private FastKeyboardView keyboard;
    private EditText editor;
    private EditorInfo info;

    private void start(String text,int cursor) {
        activityController=Robolectric.buildActivity(Activity.class).setup().visible();
        Activity activity=activityController.get();
        editor=new EditText(activity);
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setText(text);editor.setSelection(cursor);
        activity.setContentView(editor);editor.requestFocus();
        info=new EditorInfo();
        InputConnection connection=editor.onCreateInputConnection(info);
        assertNotNull(connection);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,info.inputType&InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        assertEquals(cursor,info.initialSelStart);assertEquals(cursor,info.initialSelEnd);
        serviceController=Robolectric.buildService(ChristmasKeyboardService.class).create();
        service=serviceController.get();
        ReflectionHelpers.setField(service,"mInputEditorInfo",info);
        ReflectionHelpers.setField(service,"mStartedInputConnection",connection);
        service.onStartInput(info,false);
        keyboard=(FastKeyboardView)service.onCreateInputView();
        service.onStartInputView(info,false);
    }
    private void completeCapsQuery() throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));
        ThreadPoolExecutor worker=ReflectionHelpers.getField(service,"capsWorker");
        CountDownLatch complete=new CountDownLatch(1);
        worker.execute(complete::countDown);
        assertTrue("Capitalization worker must finish",complete.await(3,TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private boolean shifted(){return ReflectionHelpers.getField(keyboard,"autoShift");}
    @After public void cleanup(){
        if(serviceController!=null)serviceController.destroy();
        if(activityController!=null)activityController.pause().stop().destroy();
    }
    @Test public void emptySentenceEditorKeepsInitialCapitalAfterRealCapsQuery() throws Exception {
        start("",0);assertTrue("Editor requests initial capitalization",shifted());
        assertEquals("Android reports a word boundary for a sentence at the start of an editor",
            InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            service.getCurrentInputConnection().getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        completeCapsQuery();assertTrue("Real empty editor must keep capital letters",shifted());
    }
    @Test public void paragraphBoundaryUsesAndroidInitialAndLiveCapsHints() throws Exception {
        start("hello\n",6);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS,info.initialCapsMode);
        assertTrue("Initial paragraph boundary must be capitalized",shifted());
        completeCapsQuery();assertTrue("Live paragraph boundary must stay capitalized",shifted());
    }
    @Test public void reopenedSentenceEditorUsesCurrentCaretAndRealCapsQuery() throws Exception {
        start("hello. ",7);completeCapsQuery();assertTrue(shifted());
        service.onFinishInputView(false);
        service.onStartInputView(info,true);completeCapsQuery();assertTrue(shifted());
        editor.setSelection(3);service.onUpdateSelection(7,7,3,3,-1,-1);
        completeCapsQuery();assertFalse("Inside a word must stay lowercase",shifted());
    }
}
