package com.snowglobe.overlay;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SceneCatalogTest {
    @Test public void fiveDistinctCuratedScenesWithStableNames(){
        assertEquals(5,SceneCatalog.presets().size());
        Set<String> names=new HashSet<>();
        for(SceneCatalog.Preset p:SceneCatalog.presets()){
            assertTrue(names.add(p.name));
            assertNotNull(SceneCatalog.find(p.name));
            assertFalse(p.description.isEmpty());
            assertEquals(SceneSnapshot.capture(p.visualValues()).values(),p.visualValues());
        }
    }
    @Test public void switchingRecipesDoesNotTouchSafetyBillingOrWallpaper(){
        String[] forbidden={"effectsEnabled","_serviceState","qualityMode","gentleMotion","reduceMotion",
            "wallpaperSnow","wakeSnow","pixelColors","premium","entitlement","paywall"};
        for(SceneCatalog.Preset p:SceneCatalog.presets())for(String key:forbidden){
            assertFalse(p.name+" contained "+key,p.visualValues().containsKey(key));
        }
    }
    @Test public void recipesActuallyDifferAndPartyAddsDecor(){
        assertNotEquals(SceneCatalog.find("Arctic Night").visualValues(),SceneCatalog.find("Vintage Christmas").visualValues());
        assertEquals(Boolean.TRUE,SceneCatalog.find("North Pole Party").visualValues().get("gifts"));
        assertEquals(5,SceneCatalog.find("Christmas Eve").visualValues().get("lightMotion"));
    }
    @Test public void invalidPresetIsNeverApplied(){assertNull(SceneCatalog.find(null));assertNull(SceneCatalog.find("not a real scene"));}
}