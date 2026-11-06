package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Restored snow coverage must be reversible: a user safety choice wins thereafter. */
@RunWith(AndroidJUnit4.class)
public final class FullSnowCoverageTest {
    @Test public void previousTopBandDefaultMigratesToFullScreenOnce(){
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("coverage-migration-test",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try{
            prefs.edit().putBoolean("edgeOnly",true).commit();
            OverlayCoverageMigration.apply(prefs);
            assertFalse("Old installs must receive full-height snowfall again",
                prefs.getBoolean("edgeOnly",true));
            assertTrue(prefs.getBoolean("_coverageRestored0215",false));
            prefs.edit().putBoolean("edgeOnly",true).commit();
            OverlayCoverageMigration.apply(prefs);
            assertTrue("Users may explicitly restore safe top-edge coverage",
                prefs.getBoolean("edgeOnly",false));
        }finally{prefs.edit().clear().commit();}
    }
    @Test public void fullSnowUsesAllParticlesRatherThanTopBandSubset(){
        SharedPreferences p=ApplicationProvider.getApplicationContext()
            .getSharedPreferences("coverage-policy-test",Context.MODE_PRIVATE);
        p.edit().clear().commit();
        try{
            SceneSettings settings=new SceneSettings(p);
            assertFalse("Scene must default to full coverage",settings.edgeOnly);
            assertEquals(-1,OverlayWindowPolicy.height(2500,3.0f,settings.edgeOnly));
            assertTrue(OverlayWindowPolicy.height(2500,3.0f,true)<1000);
        }finally{p.edit().clear().commit();}
    }
}
