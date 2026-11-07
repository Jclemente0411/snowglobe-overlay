package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Executable host tests using Android's Editable and BaseInputConnection, not string-only edit mocks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public final class KeyboardTextEngineRegressionTest {
    private static final class Editor extends BaseInputConnection {
        final Editable text=new SpannableStringBuilder();
        final List<Integer> actions=new ArrayList<>(),keyDowns=new ArrayList<>();
        int reads,deletions;
        boolean unavailableContext,unknownOffset,rejectDeletion,rejectCommit,rejectActions;
        Editor(Context context,String initial){
            super(new View(context),true);text.append(initial);Selection.setSelection(text,text.length());
        }
        @Override public Editable getEditable(){return text;}
        @Override public SurroundingText getSurroundingText(int before,int after,int flags){
            reads++;if(unavailableContext)return null;
            SurroundingText value=super.getSurroundingText(before,after,flags);
            return unknownOffset&&value!=null?new SurroundingText(value.getText(),value.getSelectionStart(),value.getSelectionEnd(),-1):value;
        }
        @Override public CharSequence getSelectedText(int flags){reads++;return super.getSelectedText(flags);}
        @Override public CharSequence getTextBeforeCursor(int length,int flags){reads++;return super.getTextBeforeCursor(length,flags);}
        @Override public CharSequence getTextAfterCursor(int length,int flags){reads++;return super.getTextAfterCursor(length,flags);}
        @Override public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){
            reads++;if(unavailableContext)return null;
            ExtractedText value=new ExtractedText();value.text=text.toString();value.startOffset=0;
            value.partialStartOffset=value.partialEndOffset=-1;
            value.selectionStart=Selection.getSelectionStart(text);value.selectionEnd=Selection.getSelectionEnd(text);return value;
        }
        @Override public boolean deleteSurroundingText(int before,int after){
            deletions++;return !rejectDeletion&&super.deleteSurroundingText(before,after);
        }
        @Override public boolean commitText(CharSequence value,int cursor){return !rejectCommit&&super.commitText(value,cursor);}
        @Override public boolean performEditorAction(int action){actions.add(action);return !rejectActions;}
        @Override public boolean sendKeyEvent(KeyEvent event){
            if(event.getAction()==KeyEvent.ACTION_DOWN)keyDowns.add(event.getKeyCode());return true;
        }
    }
    private static final class Fixture implements KeyboardTextEngine.ConnectionProvider {
        final Editor connection;
        final EditorInfo info=new EditorInfo();
        final KeyboardTextEngine engine;
        List<String> candidates;
        Fixture(String initial){
            Context context=RuntimeEnvironment.getApplication();
            SharedPreferences prefs=context.getSharedPreferences("keyboard-editing-tests",0);
            prefs.edit().clear().putBoolean("keyboardSuggestions",true).putBoolean("keyboardAutoCorrect",true).commit();
            connection=new Editor(context,initial);info.inputType=InputType.TYPE_CLASS_TEXT;
            info.initialSelStart=info.initialSelEnd=initial.length();
            engine=new KeyboardTextEngine(this,prefs);engine.begin(info);
        }
        @Override public InputConnection current(){return connection;}
        @Override public EditorInfo editor(){return info;}
        @Override public void candidates(List<String> values){candidates=values;}
        void type(String value){
            for(int at=0;at<value.length();){int cp=value.codePointAt(at);engine.input(new String(Character.toChars(cp)));at+=Character.charCount(cp);}
        }
        void move(int start,int end){Selection.setSelection(connection.text,start,end);engine.selectionChanged(start,end);}
        int cursor(){return Selection.getSelectionEnd(connection.text);}
        String text(){return connection.text.toString();}
    }

    @Test public void fastTypingAndKnownWordSeparatorsMakeNoSynchronousReadCalls(){
        Fixture f=new Fixture("");String phrase="hello world ";StringBuilder expected=new StringBuilder();
        for(int i=0;i<100;i++){f.type(phrase);expected.append(phrase);}
        assertEquals(expected.toString(),f.text());assertEquals(0,f.connection.reads);
    }
    @Test public void incrementalOwnCallbacksPreserveFastTypingAndCorrectionUndo(){
        Fixture f=new Fixture("");f.type("teh");
        f.engine.selectionChanged(1,1);f.engine.selectionChanged(2,2);f.engine.selectionChanged(3,3);
        assertEquals("teh",f.engine.activeWord());f.engine.input(" ");assertEquals("the ",f.text());
        f.engine.selectionChanged(4,4);f.engine.backspace();assertEquals("teh",f.text());
    }
    @Test public void coalescedCallbackPreservesWordButActualCursorMoveClearsIt(){
        Fixture f=new Fixture("");f.type("holiday");f.engine.selectionChanged(7,7);
        assertEquals("holiday",f.engine.activeWord());f.move(2,2);
        assertEquals("",f.engine.activeWord());assertTrue(f.candidates.isEmpty());
        f.engine.input("x");assertEquals("hoxliday",f.text());
    }
    @Test public void coalescedDeleteRetypeAcknowledgesNewestMatchingOffset(){
        Fixture f=new Fixture("");f.type("the cat ");for(int i=0;i<4;i++)f.engine.backspace();
        f.type("teh ");assertEquals("the the ",f.text());f.engine.selectionChanged(8,8);
        f.move(4,4);f.engine.backspace();assertEquals("thethe ",f.text());
    }
    @Test public void unreportedCursorMoveCannotUndoAnotherIdenticalWord(){
        Fixture f=new Fixture("the ");f.type("teh ");assertEquals("the the ",f.text());
        Selection.setSelection(f.connection.text,4);f.engine.backspace();assertEquals("thethe ",f.text());
    }
    @Test public void unreportedCursorMoveCannotReplaceTrackedWordElsewhere(){
        Fixture f=new Fixture("");f.type("teh");Selection.setSelection(f.connection.text,0);
        f.engine.input(" ");assertEquals(" teh",f.text());
    }
    @Test public void selectedTextDeletionPreservesAdjacentCharacters(){
        Fixture f=new Fixture("hello world");f.move(0,5);f.engine.backspace();
        assertEquals(" world",f.text());assertEquals(0,f.cursor());assertEquals(0,f.connection.deletions);
    }
    @Test public void knownReversedSelectionDeletesEvenWithoutReadableEditorContext(){
        Fixture f=new Fixture("hello world");f.move(5,0);f.connection.unavailableContext=true;f.engine.backspace();
        assertEquals(" world",f.text());assertEquals(0,f.connection.reads);
    }
    @Test public void lateSelectionCallbackStillDeletesSelectionInsteadOfAdjacentText(){
        Fixture f=new Fixture("hello world");Selection.setSelection(f.connection.text,0,5);f.engine.backspace();
        assertEquals(" world",f.text());assertEquals(0,f.connection.deletions);
    }
    @Test public void unreadableSelectionDelegatesDeleteInsteadOfGuessingAroundIt(){
        Fixture f=new Fixture("hello world");Selection.setSelection(f.connection.text,0,5);
        f.connection.unavailableContext=true;f.engine.backspace();
        assertEquals(0,f.connection.deletions);assertEquals(Integer.valueOf(KeyEvent.KEYCODE_DEL),f.connection.keyDowns.get(0));
    }
    @Test public void backspaceDeletesSupplementaryEmojiAndCommonGraphemeClusters(){
        for(String character:new String[]{"\uD83D\uDE03","e\u0301","\uD83C\uDDFA\uD83C\uDDF8",
                "\uD83D\uDC4D\uD83C\uDFFD","\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"}){
            Fixture f=new Fixture("a"+character);f.engine.backspace();assertEquals("Delete logical character "+character,"a",f.text());
        }
    }
    @Test public void deletingTypedCombiningCharacterKeepsWordStateConsistent(){
        Fixture f=new Fixture("");f.engine.input("e\u0301");f.engine.backspace();
        assertEquals("",f.text());assertEquals("",f.engine.activeWord());assertTrue(f.candidates.isEmpty());
    }
    @Test public void correctionDoesNotRewriteFragmentsInsideExistingWords(){
        Fixture left=new Fixture("prefix");left.type("teh ");assertEquals("prefixteh ",left.text());
        Fixture right=new Fixture("suffix");right.move(0,0);right.type("teh ");assertEquals("teh suffix",right.text());
    }
    @Test public void identifiersAreNotPartiallyAutocorrected(){
        Fixture f=new Fixture("");String text="teh123 adn_test dont@example.com teh/path ";
        f.type(text);assertEquals(text,f.text());
    }
    @Test public void longTokenNeverAutocorrectsItsTrailingFragment(){
        Fixture f=new Fixture("");String text="abcdefghijklmnopqrstuvwxyzabcdehteh ";f.type(text);assertEquals(text,f.text());
    }
    @Test public void unknownAbsoluteCursorPositionDisablesAutomaticReplacement(){
        Fixture f=new Fixture("");f.connection.unknownOffset=true;f.type("teh ");assertEquals("teh ",f.text());
    }
    @Test public void failedReplacementDeletionNeverInsertsDuplicateCorrectedWord(){
        Fixture f=new Fixture("");f.connection.rejectDeletion=true;f.type("teh ");assertEquals("teh ",f.text());
    }
    @Test public void failedCommitDoesNotLeaveCandidatesForTextThatWasNotInserted(){
        Fixture f=new Fixture("");f.connection.rejectCommit=true;f.type("hel");
        assertEquals("",f.text());assertEquals("",f.engine.activeWord());assertTrue(f.candidates.isEmpty());
    }
    @Test public void selectedSuggestionAndItsUndoPreserveTypedSpelling(){
        Fixture f=new Fixture("");f.type("hel");assertTrue(f.candidates.contains("hello"));
        f.engine.choose("hello");assertEquals("hello ",f.text());f.engine.backspace();assertEquals("hel",f.text());
    }
    @Test public void staleSuggestionCannotReplaceNewWordOrTextAfterCursorMove(){
        Fixture f=new Fixture("");f.type("hel");f.move(0,0);f.engine.choose("hello");assertEquals("hel",f.text());
        Fixture other=new Fixture("");other.type("teh");other.engine.choose("hello");assertEquals("teh",other.text());
    }
    @Test public void noEnterActionOverridesSendAndInsertsNewline(){
        Fixture f=new Fixture("");f.info.imeOptions=EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        f.engine.enter();assertEquals("\n",f.text());assertTrue(f.connection.actions.isEmpty());
    }
    @Test public void customActionIdIsUsedAndRejectedActionsFallBackToNewline(){
        Fixture f=new Fixture("");f.info.actionLabel="Continue";f.info.actionId=42;
        f.engine.enter();assertEquals(Integer.valueOf(42),f.connection.actions.get(0));assertEquals("",f.text());
        Fixture rejected=new Fixture("");rejected.info.imeOptions=EditorInfo.IME_ACTION_SEND;rejected.connection.rejectActions=true;
        rejected.engine.enter();assertEquals("\n",rejected.text());
    }
    @Test public void specialEditorsNeverCorrectOrSuggestTypedWords(){
        int[] types={InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE,
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS};
        for(int type:types){
            Fixture f=new Fixture("");f.info.inputType=type;f.engine.begin(f.info);f.type("teh ");
            assertEquals("teh ",f.text());assertTrue(f.candidates.isEmpty());assertEquals(0,f.connection.reads);
        }
        Fixture f=new Fixture("");f.info.imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        f.engine.begin(f.info);f.type("teh ");assertEquals("teh ",f.text());assertTrue(f.candidates.isEmpty());
    }
    @Test public void finishClearsStateAndIgnoresOldCallbacksUntilNextEditorBegins(){
        Fixture f=new Fixture("");f.type("hol");assertFalse(f.candidates.isEmpty());f.engine.finish();
        f.engine.input("x");f.engine.backspace();f.engine.enter();f.engine.moveCursor(-1);
        assertEquals("hol",f.text());assertEquals("",f.engine.activeWord());assertTrue(f.candidates.isEmpty());
        f.info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;
        f.info.initialSelStart=f.info.initialSelEnd=3;f.engine.begin(f.info);f.type("teh ");assertEquals("holteh ",f.text());
    }
    @Test public void spaceCursorMovementUsesCodePointsAndDoesNotSplitSurrogatePairs(){
        Fixture f=new Fixture("A\uD83D\uDE03B");f.engine.moveCursor(-1);assertEquals(3,f.cursor());
        f.engine.moveCursor(-1);assertEquals(1,f.cursor());f.engine.moveCursor(1);assertEquals(3,f.cursor());
        f.engine.input("X");assertEquals("A\uD83D\uDE03XB",f.text());
    }
    @Test public void cursorMovementClampsAtTextBoundsAndCollapsesASelection(){
        Fixture f=new Fixture("abcdef");f.move(1,5);f.engine.moveCursor(-1);assertEquals(1,f.cursor());
        f.engine.moveCursor(-1000);assertEquals(0,f.cursor());f.engine.moveCursor(1000);assertEquals(6,f.cursor());
        assertEquals("abcdef",f.text());
    }
    @Test public void cursorDragClearsCorrectionUndo(){
        Fixture f=new Fixture("");f.type("teh ");f.engine.moveCursor(-1);f.engine.backspace();assertEquals("th ",f.text());
    }
    @Test public void firstKeyAfterOpeningUsesEditorCapsHintWithoutWaitingForAQuery(){
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        info.initialSelStart=info.initialSelEnd=7;info.initialCapsMode=InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        assertTrue(ChristmasKeyboardService.initialCapitalization(info,7,7));
        info.initialCapsMode=0;assertFalse(ChristmasKeyboardService.initialCapitalization(info,7,7));
    }
    @Test public void reopeningAtAnotherCaretDoesNotReuseStaleInitialCapitalization(){
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        info.initialSelStart=info.initialSelEnd=0;info.initialCapsMode=InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        assertFalse(ChristmasKeyboardService.initialCapitalization(info,4,4));
        assertTrue(ChristmasKeyboardService.initialCapitalization(info,0,0));
        info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        assertFalse(ChristmasKeyboardService.initialCapitalization(info,0,0));
    }
    @Test @Config(sdk=28) public void legacyEditorUsesExtractedSelectionForCorrectionAndCursorMovement(){
        Fixture f=new Fixture("");f.type("teh ");assertEquals("the ",f.text());
        f.engine.moveCursor(-1);assertEquals(3,f.cursor());f.engine.backspace();assertEquals("th ",f.text());
    }
}
