package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Rechecks editing semantics on Android, in addition to the executable host regression suite. */
@RunWith(AndroidJUnit4.class)
public final class KeyboardEditingInstrumentedTest {
    private static final class Editor extends BaseInputConnection {
        final Editable text=new SpannableStringBuilder();
        int actions;
        Editor(Context context,String initial){
            super(new View(context),true);text.append(initial);Selection.setSelection(text,text.length());
        }
        @Override public Editable getEditable(){return text;}
        @Override public boolean performEditorAction(int action){actions++;return true;}
        @Override public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){
            ExtractedText result=new ExtractedText();result.text=text.toString();result.startOffset=0;
            result.partialStartOffset=result.partialEndOffset=-1;
            result.selectionStart=Selection.getSelectionStart(text);result.selectionEnd=Selection.getSelectionEnd(text);return result;
        }
    }
    private static final class Fixture implements KeyboardTextEngine.ConnectionProvider {
        final Editor connection;
        final EditorInfo info=new EditorInfo();
        final KeyboardTextEngine engine;
        List<String> candidates;
        Fixture(String initial){
            Context context=ApplicationProvider.getApplicationContext();
            SharedPreferences prefs=context.getSharedPreferences("keyboard-editing-tests",0);
            prefs.edit().clear().putBoolean("keyboardSuggestions",true).putBoolean("keyboardAutoCorrect",true).commit();
            connection=new Editor(context,initial);info.inputType=InputType.TYPE_CLASS_TEXT;
            info.initialSelStart=info.initialSelEnd=initial.length();
            engine=new KeyboardTextEngine(this,prefs);engine.begin(info);
        }
        @Override public InputConnection current(){return connection;}
        @Override public EditorInfo editor(){return info;}
        @Override public void candidates(List<String> values){candidates=values;}
        void type(String value){for(int i=0;i<value.length();i++)engine.input(value.substring(i,i+1));}
        void move(int start,int end){Selection.setSelection(connection.text,start,end);engine.selectionChanged(start,end);}
        String text(){return connection.text.toString();}
    }
    private void onMain(Runnable assertions){InstrumentationRegistry.getInstrumentation().runOnMainSync(assertions);}

    @Test public void backspaceReplacesSelectionAndPreservesAdjacentCharacters(){onMain(()->{
        Fixture f=new Fixture("hello world");f.move(0,5);f.engine.backspace();
        assertEquals(" world",f.text());assertEquals(0,Selection.getSelectionStart(f.connection.text));
    });}
    @Test public void lateSelectionCallbackDoesNotCauseAdjacentDeletion(){onMain(()->{
        Fixture f=new Fixture("hello world");Selection.setSelection(f.connection.text,0,5);f.engine.backspace();assertEquals(" world",f.text());
    });}
    @Test public void backspaceDeletesEmojiAndCombiningCharacter(){onMain(()->{
        Fixture emoji=new Fixture("a\uD83D\uDE03");emoji.engine.backspace();assertEquals("a",emoji.text());
        Fixture combined=new Fixture("ae\u0301");combined.engine.backspace();assertEquals("a",combined.text());
    });}
    @Test public void delayedOwnCallbacksPreserveFastTypingAndAutocorrectUndo(){onMain(()->{
        Fixture f=new Fixture("");f.type("teh");
        f.engine.selectionChanged(1,1);f.engine.selectionChanged(2,2);f.engine.selectionChanged(3,3);
        assertEquals("teh",f.engine.activeWord());f.engine.input(" ");assertEquals("the ",f.text());
        f.engine.selectionChanged(4,4);f.engine.backspace();assertEquals("teh",f.text());
    });}
    @Test public void coalescedCursorCallbackDoesNotDropCharactersOrTrackedWord(){onMain(()->{
        Fixture f=new Fixture("");f.type("holiday");f.engine.selectionChanged(7,7);
        assertEquals("holiday",f.text());assertEquals("holiday",f.engine.activeWord());
        f.move(2,2);assertEquals("",f.engine.activeWord());assertTrue(f.candidates.isEmpty());
        f.engine.input("x");assertEquals("hoxliday",f.text());
    });}
    @Test public void coalescedDeleteRetypeThenCursorMoveDoesNotUndoDifferentWord(){onMain(()->{
        Fixture f=new Fixture("");f.type("the cat ");for(int i=0;i<4;i++)f.engine.backspace();
        f.type("teh ");assertEquals("the the ",f.text());f.engine.selectionChanged(8,8);
        f.move(4,4);f.engine.backspace();assertEquals("thethe ",f.text());
    });}
    @Test public void lateCursorNotificationCannotMakeUndoDeleteAnotherMatchingWord(){onMain(()->{
        Fixture f=new Fixture("the ");f.type("teh ");Selection.setSelection(f.connection.text,4);
        f.engine.backspace();assertEquals("thethe ",f.text());
    });}
    @Test public void finishingEditorClearsWordAndCandidatesBeforeNextField(){onMain(()->{
        Fixture f=new Fixture("");f.type("hol");assertFalse(f.candidates.isEmpty());
        f.engine.finish();f.engine.input("x");assertEquals("hol",f.text());assertTrue(f.candidates.isEmpty());
        f.info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;
        f.info.initialSelStart=f.info.initialSelEnd=f.connection.text.length();f.engine.begin(f.info);
        f.type("teh ");assertEquals("holteh ",f.text());assertTrue(f.candidates.isEmpty());
    });}
    @Test public void noEnterActionInMultilineEditorInsertsNewline(){onMain(()->{
        Fixture f=new Fixture("");f.info.imeOptions=EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        f.engine.enter();assertEquals("\n",f.text());assertEquals(0,f.connection.actions);
    });}
    @Test public void longWordAndIdentifiersAreNeverPartiallyCorrected(){onMain(()->{
        Fixture f=new Fixture("");String text="abcdefghijklmnopqrstuvwxyzabcdehteh teh123 adn_test ";
        f.type(text);assertEquals(text,f.text());
    });}
    @Test public void cursorMovementKeepsSupplementaryCharactersIntact(){onMain(()->{
        Fixture f=new Fixture("A\uD83D\uDE03B");f.engine.moveCursor(-1);assertEquals(3,Selection.getSelectionEnd(f.connection.text));
        f.engine.moveCursor(-1);assertEquals(1,Selection.getSelectionEnd(f.connection.text));
        f.engine.input("X");assertEquals("AX\uD83D\uDE03B",f.text());
    });}
}
