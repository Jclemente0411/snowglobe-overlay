package com.snowglobe.overlay;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SceneRenderTest {
    private SharedPreferences prefs;
    @Before public void setup(){Context c=ApplicationProvider.getApplicationContext();prefs=c.getSharedPreferences("render-test",0);prefs.edit().clear().commit();}
    private SceneRenderer scene(){SceneRenderer r=new SceneRenderer(1,new SceneSettings(prefs));r.resize(360,800,0,0,1);return r;}
    private int visiblePixels(SceneRenderer r){Bitmap b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);r.draw(new Canvas(b),false);int visible=0;for(int y=0;y<800;y+=2)for(int x=0;x<360;x+=2)if((b.getPixel(x,y)>>>24)>10)visible++;return visible;}
    @Test public void wallpaperSnowDoesNotDependOnOverlayOrAttachedView(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).putBoolean("effectsEnabled",false).putBoolean("wallpaperSnow",true).commit();SceneRenderer r=scene();r.setWallpaper(true);assertTrue(visiblePixels(r)>100);}
    @Test public void everyBulbShapeRenders(){for(int shape=0;shape<3;shape++){prefs.edit().putBoolean("snow",false).putInt("bulbShape",shape).putInt("lightBrightness",100).commit();assertTrue(visiblePixels(scene())>100);}}
    @Test public void randomLightsAppearWithoutSnowOrStrand(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).putBoolean("screenSparkles",true).putInt("sparkleDensity",100).commit();SceneRenderer r=scene();for(int i=1;i<500;i++)r.advance(i*16666667L);assertTrue(visiblePixels(r)>20);}
    @Test public void allTravelDirectionsRemainDrawable(){for(int dir=0;dir<8;dir++){prefs.edit().putInt("particleDirection",dir).putBoolean("lights",false).commit();SceneRenderer r=scene();for(int i=1;i<1200;i++)r.advance(i*16666667L);assertTrue("direction "+dir,visiblePixels(r)>30);}}
    @Test public void reducingMotionStopsAnActiveTouchBurst(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).commit();SceneRenderer r=scene();r.touch(180,400);assertTrue(r.animated());prefs.edit().putBoolean("reduceMotion",true).commit();r.apply(new SceneSettings(prefs));assertFalse(r.animated());}
}
