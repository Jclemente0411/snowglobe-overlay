package com.snowglobe.overlay;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class OverlayContentTest {
    private SharedPreferences prefs;
    @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("overlay-content-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
    @Test public void invisibleSceneMustNotLeaveAnObscuringWindow(){
        prefs.edit().putInt("snowVisibility",0).putBoolean("lights",false).commit();
        assertFalse(new SceneSettings(prefs,false).hasOverlayContent());
        prefs.edit().putInt("snowVisibility",100).putInt("density",0).commit();
        assertFalse(new SceneSettings(prefs,false).hasOverlayContent());
    }
    @Test public void visibleLightsAndPaidDecorationsStillNeedAWindow(){
        prefs.edit().putBoolean("snow",false).putBoolean("lights",true).commit();
        assertTrue(new SceneSettings(prefs,false).hasOverlayContent());
        prefs.edit().putBoolean("lights",false).putBoolean("candy",true).commit();
        assertFalse(new SceneSettings(prefs,false).hasOverlayContent());
        assertTrue(new SceneSettings(prefs,true).hasOverlayContent());
        prefs.edit().putInt("density",0).commit();
        assertFalse(new SceneSettings(prefs,true).hasOverlayContent());
    }
}
