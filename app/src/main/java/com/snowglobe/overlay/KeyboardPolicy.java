package com.snowglobe.overlay;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure keyboard input/privacy decisions, separate from its View and service lifecycle. */
public final class KeyboardPolicy {
    public enum Layout { LETTERS, NUMBER, PHONE, EMAIL, URL }
    private static final String[] WORDS = {
        "about","after","again","all","also","always","amazing","and","another","app","are",
        "beautiful","because","before","best","better","birthday","but","can","christmas",
        "cold","come","cool","could","day","dear","did","do","doing","done","don't",
        "easy","enjoy","even","everything","family","feel","festive","first","for",
        "friend","friends","from","fun","get","gift","good","great","happy","have",
        "hello","help","holiday","home","hope","how","ice","is","it","just","keep",
        "keyboard","kind","know","let","light","lights","like","little","look","love",
        "magic","make","many","merry","message","more","morning","much","my","need",
        "new","nice","night","not","now","of","on","one","our","out","perfect",
        "please","ready","really","right","season","see","send","snow","snowflake",
        "snowglobe","some","soon","so","sparkle","special","still","thank","thanks",
        "that","the","their","them","there","these","they","this","time","today",
        "tomorrow","too","try","very","want","warm","was","we","welcome","what",
        "when","where","which","will","winter","wish","with","wonderful","work",
        "would","yes","you","your"
    };

    private KeyboardPolicy(){}
    public static boolean sensitive(int inputType) {
        final int kind=inputType & InputType.TYPE_MASK_CLASS;
        final int variation=inputType & InputType.TYPE_MASK_VARIATION;
        return (kind==InputType.TYPE_CLASS_TEXT &&
            (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
             (kind==InputType.TYPE_CLASS_NUMBER &&
              variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
    public static Layout layout(int inputType){
        int kind=inputType&InputType.TYPE_MASK_CLASS;
        int variation=inputType&InputType.TYPE_MASK_VARIATION;
        if(kind==InputType.TYPE_CLASS_PHONE)return Layout.PHONE;
        if(kind==InputType.TYPE_CLASS_NUMBER||kind==InputType.TYPE_CLASS_DATETIME)return Layout.NUMBER;
        if(kind==InputType.TYPE_CLASS_TEXT &&
            (variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
             variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))return Layout.EMAIL;
        if(kind==InputType.TYPE_CLASS_TEXT&&variation==InputType.TYPE_TEXT_VARIATION_URI)return Layout.URL;
        return Layout.LETTERS;
    }
    public static boolean suggestionsAllowed(EditorInfo info){
        if(info==null||sensitive(info.inputType))return false;
        if((info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0)return false;
        if((info.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0)return false;
        return layout(info.inputType)==Layout.LETTERS;
    }
    public static boolean assistantAllowed(EditorInfo info){
        return info!=null&&!sensitive(info.inputType)&&
            (info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0;
    }
    /** Offline completions only. No logging, networking, personalization or persistence. */
    public static List<String> complete(String prefix) {
        List<String> found=new ArrayList<>(3);
        if(prefix==null||prefix.length()<2||prefix.length()>24)return found;
        String s=prefix.toLowerCase(Locale.ROOT);
        for(String word:WORDS){
            if(word.startsWith(s)&&!word.equals(s)){
                found.add(word);
                if(found.size()==3)break;
            }
        }
        return found;
    }
    public static int bound(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    public static long animationDelayMs(int speed,boolean saver) {
        return saver?0:Math.max(130,420-bound(speed,0,100)*2L);
    }
}
