package com.snowglobe.overlay;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.Collections;
import java.util.List;

/**
 * Keeps only the word currently being composed, never logging or persisting it.
 * Uses InputConnection's native composing APIs so text editors own caret/selection.
 */
final class KeyboardTextEngine {
    interface ConnectionProvider {
        InputConnection current();
        EditorInfo editor();
        void candidates(List<String> values);
    }
    private final ConnectionProvider host;
    private final SharedPreferences preferences;
    private final StringBuilder word=new StringBuilder(28);
    private boolean suggestionsEnabled;
    private boolean compositionEnabled;
    private boolean autocorrectEnabled;
    private boolean sensitive;
    private String lastOriginal=null,lastCorrection=null;
    private String lastDelimiter=null;

    KeyboardTextEngine(ConnectionProvider host,SharedPreferences preferences){
        this.host=host;this.preferences=preferences;
    }
    static boolean privateEditor(EditorInfo info){
        if(info==null)return true;
        int input=info.inputType;
        int kind=input&InputType.TYPE_MASK_CLASS;
        int variation=input&InputType.TYPE_MASK_VARIATION;
        return (kind==InputType.TYPE_CLASS_TEXT &&
                (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||
                 variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||
                 variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
               (kind==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
               (info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0;
    }
    static boolean ordinaryText(EditorInfo info){
        if(info==null||privateEditor(info))return false;
        int input=info.inputType;
        if((input&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)return false;
        int variation=input&InputType.TYPE_MASK_VARIATION;
        if(variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||
            variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS||
            variation==InputType.TYPE_TEXT_VARIATION_URI||
            variation==InputType.TYPE_TEXT_VARIATION_FILTER)return false;
        return (input&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)==0;
    }
    void begin(EditorInfo info){
        word.setLength(0);lastOriginal=null;lastCorrection=null;lastDelimiter=null;
        sensitive=privateEditor(info);
        boolean ordinary=ordinaryText(info);
        suggestionsEnabled=ordinary&&preferences.getBoolean("keyboardSuggestions",true);
        autocorrectEnabled=ordinary&&preferences.getBoolean("keyboardAutoCorrect",true);
        compositionEnabled=suggestionsEnabled||autocorrectEnabled;
        host.candidates(Collections.emptyList());
    }
    void finish(){
        // Individual character commits leave no composing span to finish.
        word.setLength(0);lastOriginal=null;lastCorrection=null;lastDelimiter=null;
        host.candidates(Collections.emptyList());
    }
    private static boolean letter(String value){
        return value!=null&&value.length()==1&&Character.isLetter(value.charAt(0));
    }
    void input(String value){
        InputConnection ic=host.current();
        if(ic==null||value==null||value.isEmpty())return;
        if(!compositionEnabled){
            ic.commitText(value,1);
            return;
        }
        if(letter(value)&&word.length()<28){
            // Fast path: commit just one character, no composition span rewrite.
            // This keeps input latency independent of the length of the active word.
            lastOriginal=null;lastCorrection=null;lastDelimiter=null;
            if(ic.commitText(value,1)){
                word.append(value);
                updateCandidates();
            }else{
                word.setLength(0);
                host.candidates(Collections.emptyList());
            }
        }else{
            finishWord(value,ic);
        }
    }
    private void finishWord(String suffix,InputConnection ic){
        if(word.length()>0){
            String typed=word.toString();
            // A moved caret must never cause replacement of unrelated editor text.
            CharSequence before=ic.getTextBeforeCursor(typed.length(),0);
            boolean matches=before!=null&&typed.contentEquals(before);
            String fixed=matches&&autocorrectEnabled?KeyboardDictionary.correct(typed):typed;
            boolean corrected=matches&&!fixed.equals(typed);
            lastOriginal=corrected?typed:null;
            lastCorrection=corrected?fixed:null;
            lastDelimiter=corrected?suffix:null;
            if(corrected){
                ic.beginBatchEdit();
                try{
                    ic.deleteSurroundingText(typed.length(),0);
                    ic.commitText(fixed+suffix,1);
                }finally{ic.endBatchEdit();}
            }else ic.commitText(suffix,1);
            word.setLength(0);
        }else{
            lastOriginal=null;lastCorrection=null;lastDelimiter=null;
            ic.commitText(suffix,1);
        }
        host.candidates(Collections.emptyList());
    }
    void choose(String candidate){
        if(!suggestionsEnabled||word.length()==0||candidate==null||candidate.isEmpty())return;
        InputConnection ic=host.current();if(ic==null)return;
        String typed=word.toString();
        CharSequence before=ic.getTextBeforeCursor(typed.length(),0);
        if(before==null||!typed.contentEquals(before)){
            word.setLength(0);host.candidates(Collections.emptyList());return;
        }
        ic.beginBatchEdit();
        try{
            ic.deleteSurroundingText(typed.length(),0);
            ic.commitText(candidate+" ",1);
        }finally{ic.endBatchEdit();}
        lastOriginal=!candidate.equals(typed)?typed:null;
        lastCorrection=!candidate.equals(typed)?candidate:null;
        lastDelimiter=!candidate.equals(typed)?" ":null;
        word.setLength(0);host.candidates(Collections.emptyList());
    }
    void backspace(){
        InputConnection ic=host.current();if(ic==null)return;
        if(word.length()>0){
            ic.deleteSurroundingText(1,0);
            word.deleteCharAt(word.length()-1);
            updateCandidates();
            return;
        }
        if(lastOriginal!=null&&lastCorrection!=null&&lastDelimiter!=null){
            final String raw=lastOriginal;
            final int n=lastCorrection.length()+lastDelimiter.length();
            lastOriginal=null;lastCorrection=null;lastDelimiter=null;
            ic.beginBatchEdit();
            try{
                ic.deleteSurroundingText(n,0);
                ic.commitText(raw,1);
            }finally{ic.endBatchEdit();}
            word.append(raw);
            updateCandidates();
            return;
        }
        ic.deleteSurroundingText(1,0);
        host.candidates(Collections.emptyList());
    }
    void enter(){
        InputConnection ic=host.current();if(ic==null)return;
        if(word.length()>0)finishWord("",ic);
        EditorInfo info=host.editor();
        int action=info==null?EditorInfo.IME_ACTION_NONE:info.imeOptions&EditorInfo.IME_MASK_ACTION;
        if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){
            if(ic.performEditorAction(action))return;
        }
        ic.commitText("\n",1);
        lastOriginal=null;lastCorrection=null;lastDelimiter=null;
    }
    void externalCursorChanged(int selectionEnd,int composingEnd){
        // No composing region: typed letters have already been committed.
        // Suggestions are discarded if the editor moves or selects text.
        if(composingEnd!=-1&&selectionEnd!=composingEnd){
            word.setLength(0);host.candidates(Collections.emptyList());
        }
    }
    String activeWord(){return word.toString();}
    boolean isSensitive(){return sensitive;}
    private void updateCandidates(){
        host.candidates(suggestionsEnabled?KeyboardDictionary.suggest(word.toString()):Collections.emptyList());
    }
}
