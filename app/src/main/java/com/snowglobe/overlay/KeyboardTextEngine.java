package com.snowglobe.overlay;

import android.content.SharedPreferences;
import android.icu.text.BreakIterator;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded, in-memory editing state. Editor text is never logged, learned, or persisted. */
final class KeyboardTextEngine {
    interface ConnectionProvider {
        InputConnection current();
        EditorInfo editor();
        void candidates(List<String> values);
    }
    private static final int MAX_WORD=28, CONTEXT_LIMIT=128;
    private final ConnectionProvider host;
    private final SharedPreferences preferences;
    private final StringBuilder word=new StringBuilder(MAX_WORD);
    // Android can acknowledge several taps together, including repeated offsets after deletion.
    private final int[] pendingEnds=new int[64];
    private int pendingCount,selectionStart=-1,selectionEnd=-1,acknowledgedEnd=-1;
    private boolean active,suggestionsEnabled,compositionEnabled,autocorrectEnabled,sensitive;
    private boolean suppressedUntilBoundary;
    private String lastOriginal,lastReplacement;
    private int lastReplacementEnd=-1;
    private List<String> currentCandidates=Collections.emptyList();
    private BreakIterator characterBoundaries;

    KeyboardTextEngine(ConnectionProvider host,SharedPreferences preferences){
        this.host=host;this.preferences=preferences;
        KeyboardDictionary.warmUp();
    }
    static boolean privateEditor(EditorInfo info){
        if(info==null)return true;
        int input=info.inputType,kind=input&InputType.TYPE_MASK_CLASS;
        int variation=input&InputType.TYPE_MASK_VARIATION;
        return (kind==InputType.TYPE_CLASS_TEXT&&
                (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||
                 variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||
                 variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))||
               (kind==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)||
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
        return (input&(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))==0;
    }
    void begin(EditorInfo info){
        begin(info,info==null?-1:info.initialSelStart,info==null?-1:info.initialSelEnd);
    }
    void begin(EditorInfo info,int initialStart,int initialEnd){
        clearWord();pendingCount=0;active=true;
        selectionStart=initialStart;selectionEnd=initialEnd;
        acknowledgedEnd=initialStart==initialEnd?initialEnd:-1;
        sensitive=privateEditor(info);
        boolean ordinary=ordinaryText(info);
        suggestionsEnabled=ordinary&&preferences.getBoolean("keyboardSuggestions",true);
        autocorrectEnabled=ordinary&&preferences.getBoolean("keyboardAutoCorrect",true);
        compositionEnabled=suggestionsEnabled||autocorrectEnabled;
        publishCandidates(Collections.emptyList());
    }
    void finish(){
        active=false;clearWord();pendingCount=0;selectionStart=selectionEnd=acknowledgedEnd=-1;
        if(characterBoundaries!=null)characterBoundaries.setText("");
        publishCandidates(Collections.emptyList());
    }
    private void clearUndo(){lastOriginal=null;lastReplacement=null;lastReplacementEnd=-1;}
    private void clearWord(){word.setLength(0);suppressedUntilBoundary=false;clearUndo();}
    private void publishCandidates(List<String> values){currentCandidates=values;host.candidates(values);}
    private static boolean letter(String value){
        if(value.isEmpty())return false;
        for(int at=0;at<value.length();){
            int cp=value.codePointAt(at),type=Character.getType(cp);
            if(!Character.isLetter(cp)&&type!=Character.NON_SPACING_MARK&&type!=Character.COMBINING_SPACING_MARK)return false;
            at+=Character.charCount(cp);
        }
        return true;
    }
    private static boolean wordPart(int cp){
        int type=Character.getType(cp);
        return Character.isLetterOrDigit(cp)||cp=='\''||cp=='\u2019'||cp=='_'||
            type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK;
    }
    private static boolean whitespace(String value){
        for(int i=0;i<value.length();i++)if(!Character.isWhitespace(value.charAt(i)))return false;
        return !value.isEmpty();
    }
    private static boolean identifierPart(String value){
        if(value.codePointCount(0,value.length())!=1)return false;
        int cp=value.codePointAt(0);
        return Character.isDigit(cp)||cp=='_'||cp=='@'||cp=='/'||cp=='\\'||cp=='-';
    }
    private void expectCursor(int end){
        selectionStart=selectionEnd=end;
        if(end<0)return;
        if(pendingCount==pendingEnds.length){
            System.arraycopy(pendingEnds,1,pendingEnds,0,pendingEnds.length-1);pendingCount--;
        }
        pendingEnds[pendingCount++]=end;
    }
    private boolean commit(InputConnection ic,String value){
        int start=selectionStart<0||selectionEnd<0?-1:Math.min(selectionStart,selectionEnd);
        if(!ic.commitText(value,1)){clearWord();publishCandidates(Collections.emptyList());return false;}
        expectCursor(start<0?-1:start+value.length());return true;
    }
    void input(String value){
        if(!active||value==null||value.isEmpty())return;
        InputConnection ic=host.current();if(ic==null)return;
        if(selectionStart!=selectionEnd)clearWord();
        if(!compositionEnabled){commit(ic,value);return;}
        if(letter(value)||(("'".equals(value)||"\u2019".equals(value))&&word.length()>0)){
            if(suppressedUntilBoundary){commit(ic,value);return;}
            if(word.length()+value.length()>MAX_WORD){
                clearWord();suppressedUntilBoundary=true;commit(ic,value);publishCandidates(Collections.emptyList());return;
            }
            clearUndo();
            // No synchronous editor read or whole-word rewrite on the ordinary typing path.
            if(commit(ic,value)){word.append(value);updateCandidates();}
        }else if(identifierPart(value)){
            clearWord();suppressedUntilBoundary=true;commit(ic,value);publishCandidates(Collections.emptyList());
        }else{
            finishWord(value,ic);
            if(whitespace(value))suppressedUntilBoundary=false;
        }
    }
    private void finishWord(String suffix,InputConnection ic){
        if(word.length()==0){
            clearUndo();if(!suffix.isEmpty())commit(ic,suffix);
            publishCandidates(Collections.emptyList());return;
        }
        String typed=word.toString();
        // Most words need no correction, so even their separator does not trigger read IPC.
        String fixed=autocorrectEnabled?KeyboardDictionary.correct(typed):typed;
        boolean replace=!fixed.equals(typed)&&matchesTrackedWord(readContext(ic,typed.length()+2,2),typed);
        clearUndo();
        if(replace){
            ic.beginBatchEdit();
            try{
                if(ic.deleteSurroundingText(typed.length(),0)){
                    selectionStart=selectionEnd=Math.max(0,selectionEnd-typed.length());
                    if(commit(ic,fixed+suffix))rememberReplacement(typed,fixed+suffix);
                }else if(!suffix.isEmpty())commit(ic,suffix);
            }finally{ic.endBatchEdit();}
        }else if(!suffix.isEmpty())commit(ic,suffix);
        word.setLength(0);publishCandidates(Collections.emptyList());
    }
    private void rememberReplacement(String typed,String replacement){
        lastOriginal=typed;lastReplacement=replacement;lastReplacementEnd=selectionEnd;
    }
    void choose(String candidate){
        if(!active||!suggestionsEnabled||word.length()==0||candidate==null||!currentCandidates.contains(candidate))return;
        InputConnection ic=host.current();if(ic==null)return;
        String typed=word.toString();
        if(!matchesTrackedWord(readContext(ic,typed.length()+2,2),typed)){
            clearWord();publishCandidates(Collections.emptyList());return;
        }
        clearUndo();ic.beginBatchEdit();
        try{
            if(candidate.equals(typed))commit(ic," ");
            else if(ic.deleteSurroundingText(typed.length(),0)){
                selectionStart=selectionEnd=Math.max(0,selectionEnd-typed.length());
                if(commit(ic,candidate+" "))rememberReplacement(typed,candidate+" ");
            }
        }finally{ic.endBatchEdit();}
        word.setLength(0);publishCandidates(Collections.emptyList());
    }
    private boolean matchesTrackedWord(Snapshot context,String typed){
        if(context==null||!context.collapsed()||selectionStart!=selectionEnd||selectionEnd<0||
            context.absoluteEnd()!=selectionEnd||!context.endsWith(typed))return false;
        int begin=context.start-typed.length();
        if(begin<0||(begin==0&&context.offset!=0))return false; // Left boundary is not available.
        if(begin>0&&wordPart(Character.codePointBefore(context.text,begin)))return false;
        return context.end>=context.text.length()||!wordPart(Character.codePointAt(context.text,context.end));
    }
    void backspace(){
        if(!active)return;
        InputConnection ic=host.current();if(ic==null)return;
        if(selectionStart>=0&&selectionEnd>=0&&selectionStart!=selectionEnd){
            // Surrounding deletion explicitly excludes the selected text.
            clearWord();commit(ic,"");publishCandidates(Collections.emptyList());return;
        }
        Snapshot context=readContext(ic,CONTEXT_LIMIT,0);
        if(context==null){
            // A null query is not proof of an empty selection. Let the editor handle Delete.
            clearWord();forgetPosition();sendKey(ic,KeyEvent.KEYCODE_DEL);publishCandidates(Collections.emptyList());return;
        }
        if(context.absoluteStart()!=selectionStart||context.absoluteEnd()!=selectionEnd){
            clearWord();pendingCount=0;
        }
        selectionStart=context.absoluteStart();selectionEnd=context.absoluteEnd();
        if(!context.collapsed()){
            clearWord();commit(ic,"");publishCandidates(Collections.emptyList());return;
        }
        if(lastOriginal!=null&&lastReplacement!=null&&selectionEnd>=0&&selectionEnd==lastReplacementEnd&&context.endsWith(lastReplacement)){
            String original=lastOriginal,replaced=lastReplacement;clearUndo();
            boolean deleted=false;ic.beginBatchEdit();
            try{
                if(ic.deleteSurroundingText(replaced.length(),0)){
                    deleted=true;selectionStart=selectionEnd=Math.max(0,selectionEnd-replaced.length());
                    if(commit(ic,original))word.append(original);
                }
            }finally{ic.endBatchEdit();}
            if(deleted){updateCandidates();return;}
        }
        clearUndo();
        String before=context.before(CONTEXT_LIMIT);
        if(before.isEmpty()){word.setLength(0);publishCandidates(Collections.emptyList());return;}
        int chars=lastCharacterLength(before);
        if(chars==before.length()&&context.absoluteStart()>before.length()){
            // The bounded snapshot may begin inside an unusually long grapheme cluster.
            clearWord();forgetPosition();sendKey(ic,KeyEvent.KEYCODE_DEL);publishCandidates(Collections.emptyList());return;
        }
        boolean tracking=word.length()>=chars&&before.endsWith(word.toString());
        if(ic.deleteSurroundingText(chars,0)){
            expectCursor(selectionEnd<0?-1:Math.max(0,selectionEnd-chars));
            if(tracking)word.setLength(word.length()-chars);else word.setLength(0);
        }else{
            clearWord();forgetPosition();sendKey(ic,KeyEvent.KEYCODE_DEL);
        }
        updateCandidates();
    }
    private int lastCharacterLength(String before){
        char last=before.charAt(before.length()-1);
        if(last<=0x7f)return last=='\n'&&before.length()>1&&before.charAt(before.length()-2)=='\r'?2:1;
        if(characterBoundaries==null)characterBoundaries=BreakIterator.getCharacterInstance(Locale.ROOT);
        characterBoundaries.setText(before);
        try{
            int boundary=characterBoundaries.preceding(before.length());
            return boundary==BreakIterator.DONE?Character.charCount(before.codePointBefore(before.length())):before.length()-boundary;
        }finally{characterBoundaries.setText("");}
    }
    void enter(){
        if(!active)return;
        InputConnection ic=host.current();if(ic==null)return;
        if(word.length()>0)finishWord("",ic);
        EditorInfo info=host.editor();
        int action=info==null?EditorInfo.IME_ACTION_NONE:info.imeOptions&EditorInfo.IME_MASK_ACTION;
        boolean useAction=info!=null&&(info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0;
        clearUndo();suppressedUntilBoundary=false;
        if(useAction&&info.actionLabel!=null&&ic.performEditorAction(info.actionId))return;
        if(useAction&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED&&ic.performEditorAction(action))return;
        commit(ic,"\n");
    }
    void moveCursor(int codePoints){
        if(!active||codePoints==0)return;
        InputConnection ic=host.current();if(ic==null)return;
        int amount=Math.max(-32,Math.min(32,codePoints));
        clearWord();publishCandidates(Collections.emptyList());
        Snapshot context=readContext(ic,CONTEXT_LIMIT,CONTEXT_LIMIT);
        if(context==null||context.offset<0){
            forgetPosition();int key=amount<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
            for(int i=0;i<Math.abs(amount);i++)sendKey(ic,key);
            return;
        }
        int target=amount<0?context.start:context.end,remaining=Math.abs(amount);
        if(!context.collapsed())remaining--; // A first arrow collapses the current selection.
        while(remaining-->0){
            if(amount<0){
                if(target==0)break;
                target-=Character.charCount(Character.codePointBefore(context.text,target));
            }else{
                if(target>=context.text.length())break;
                target+=Character.charCount(Character.codePointAt(context.text,target));
            }
        }
        // An externally supplied caret can itself be inside a surrogate pair.
        if(target>0&&target<context.text.length()&&Character.isHighSurrogate(context.text.charAt(target-1))&&Character.isLowSurrogate(context.text.charAt(target)))
            target+=amount<0?-1:1;
        int absolute=context.offset+target;
        pendingCount=0;
        if(ic.setSelection(absolute,absolute))expectCursor(absolute);else forgetPosition();
    }
    private static void sendKey(InputConnection ic,int code){
        long now=SystemClock.uptimeMillis();
        int flags=KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE;
        ic.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,flags,InputDevice.SOURCE_KEYBOARD));
        ic.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,flags,InputDevice.SOURCE_KEYBOARD));
    }
    private void forgetPosition(){pendingCount=0;selectionStart=selectionEnd=acknowledgedEnd=-1;}
    /** Returns true for a caret move that was not an acknowledgement of our own editing. */
    boolean selectionChanged(int newStart,int newEnd){
        if(!active)return false;
        if(newStart==newEnd){
            // Match the newest offset: deleting and retyping can repeat earlier positions.
            for(int i=pendingCount-1;i>=0;i--)if(pendingEnds[i]==newEnd){
                int remaining=pendingCount-i-1;
                System.arraycopy(pendingEnds,i+1,pendingEnds,0,remaining);pendingCount=remaining;
                acknowledgedEnd=newEnd;
                if(remaining==0)selectionStart=selectionEnd=newEnd;
                return false;
            }
            if(newEnd==acknowledgedEnd&&pendingCount>0)return false;
            if(newEnd==selectionEnd&&selectionStart==selectionEnd){acknowledgedEnd=newEnd;return false;}
        }
        selectionStart=newStart;selectionEnd=newEnd;acknowledgedEnd=newStart==newEnd?newEnd:-1;
        pendingCount=0;clearWord();publishCandidates(Collections.emptyList());return true;
    }
    private Snapshot readContext(InputConnection ic,int before,int after){
        try{
            if(Build.VERSION.SDK_INT>=31){
                SurroundingText text=ic.getSurroundingText(before,after,0);
                return text==null?null:Snapshot.create(text.getText(),text.getSelectionStart(),text.getSelectionEnd(),text.getOffset());
            }
            ExtractedTextRequest request=new ExtractedTextRequest();
            request.hintMaxChars=before+after+MAX_WORD;request.hintMaxLines=3;
            ExtractedText text=ic.getExtractedText(request,0);
            if(text==null||text.partialStartOffset!=-1)return null;
            return Snapshot.create(text.text,text.selectionStart,text.selectionEnd,text.startOffset);
        }catch(RuntimeException unavailable){
            // A stale/custom editor must not crash the keyboard or authorize an unsafe rewrite.
            return null;
        }
    }
    private static final class Snapshot {
        final CharSequence text;
        final int start,end,offset;
        private Snapshot(CharSequence text,int start,int end,int offset){
            this.text=text;this.start=Math.min(start,end);this.end=Math.max(start,end);this.offset=offset;
        }
        static Snapshot create(CharSequence text,int start,int end,int offset){
            if(text==null||start<0||end<0||start>text.length()||end>text.length()||offset< -1||
                (offset>=0&&(long)offset+Math.max(start,end)>Integer.MAX_VALUE))return null;
            return new Snapshot(text,start,end,offset);
        }
        boolean collapsed(){return start==end;}
        int absoluteStart(){return offset<0?-1:offset+start;}
        int absoluteEnd(){return offset<0?-1:offset+end;}
        String before(int length){return text.subSequence(Math.max(0,start-length),start).toString();}
        boolean endsWith(String value){
            return start>=value.length()&&value.contentEquals(text.subSequence(start-value.length(),start));
        }
    }
    String activeWord(){return word.toString();}
    boolean isSensitive(){return sensitive;}
    private void updateCandidates(){
        publishCandidates(suggestionsEnabled?KeyboardDictionary.suggest(word.toString()):Collections.emptyList());
    }
}
