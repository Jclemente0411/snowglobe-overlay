package com.snowglobe.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardDictionaryTest {
    @Test public void commonTyposAreCorrectedConservatively(){
        assertEquals("the",KeyboardDictionary.correct("teh"));
        assertEquals("The",KeyboardDictionary.correct("Teh"));
        assertEquals("THE",KeyboardDictionary.correct("TEH"));
        assertEquals("keyboard",KeyboardDictionary.correct("keybord"));
        assertEquals("beautiful",KeyboardDictionary.correct("beutiful"));
        assertEquals("the",KeyboardDictionary.correct("the"));
        assertEquals("don\'t",KeyboardDictionary.correct("dont"));
        assertEquals("I\'M",KeyboardDictionary.correct("IM"));
        assertEquals("its",KeyboardDictionary.correct("its"));
        assertEquals("snowglobe",KeyboardDictionary.correct("snowglobe"));
        assertEquals("nasa",KeyboardDictionary.correct("nasa"));
    }
    @Test public void smallVocabularyMustNotRewriteValidUnknownWordsOrMixedCaseNames(){
        for(String word:new String[]{"eat","art","met","ham","gel","bill","Ted","pleas","colours","iPhone","eBay","tEh"})
            assertEquals("Preserve intentional spelling: "+word,word,KeyboardDictionary.correct(word));
    }
    @Test public void showSuggestionsWithoutAutoReplacingTypedWord(){
        assertFalse(KeyboardDictionary.suggest("hel").isEmpty());
        assertEquals("hel",KeyboardDictionary.suggest("hel").get(0));
        assertTrue(KeyboardDictionary.suggest("").isEmpty());
    }
    @Test public void singleEditRecognitionIsBounded(){
        assertEquals(0,KeyboardDictionary.distanceOne("help","help"));
        assertEquals(1,KeyboardDictionary.distanceOne("helo","hello"));
        assertEquals(1,KeyboardDictionary.distanceOne("hte","the"));
        assertEquals(2,KeyboardDictionary.distanceOne("apple","winter"));
    }
}
