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
    // Exercise all visual features without changing release purchase storage.
    private SceneRenderer scene(){SceneRenderer r=new SceneRenderer(1,new SceneSettings(prefs,true));r.resize(360,800,0,0,1);return r;}
    private int visiblePixels(SceneRenderer r){
        Bitmap b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);
        try{r.draw(new Canvas(b),false);int visible=0;for(int y=0;y<800;y+=2)for(int x=0;x<360;x+=2)if((b.getPixel(x,y)>>>24)>10)visible++;return visible;}
        finally{b.recycle();}
    }
    @Test public void wallpaperSnowDoesNotDependOnOverlayOrAttachedView(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).putBoolean("effectsEnabled",false).putBoolean("wallpaperSnow",true).commit();SceneRenderer r=scene();r.setWallpaper(true);assertTrue(visiblePixels(r)>100);}
    @Test public void everyBulbShapeRenders(){for(int shape=0;shape<3;shape++){prefs.edit().putBoolean("snow",false).putInt("bulbShape",shape).putInt("lightBrightness",100).commit();assertTrue(visiblePixels(scene())>100);}}
    @Test public void randomLightsAppearWithoutSnowOrStrand(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).putBoolean("screenSparkles",true).putInt("sparkleDensity",100).commit();SceneRenderer r=scene();for(int i=1;i<500;i++)r.advance(i*16666667L);assertTrue(visiblePixels(r)>20);}
    @Test public void allTravelDirectionsRemainDrawable(){for(int dir=0;dir<8;dir++){prefs.edit().putInt("particleDirection",dir).putBoolean("lights",false).commit();SceneRenderer r=scene();for(int i=1;i<1200;i++)r.advance(i*16666667L);assertTrue("direction "+dir,visiblePixels(r)>30);}}
    @Test public void reducingMotionStopsAnActiveTouchBurst(){prefs.edit().putBoolean("snow",false).putBoolean("lights",false).commit();SceneRenderer r=scene();r.touch(180,400);assertTrue(r.animated());prefs.edit().putBoolean("reduceMotion",true).commit();r.apply(new SceneSettings(prefs,true));assertFalse(r.animated());}
    @Test public void typingKeepsSnowInEveryQuarterWithLessDrawingWork(){
        prefs.edit().putBoolean("lights",false).putBoolean("screenSparkles",false)
            .putBoolean("reduceMotion",true).putInt("density",100).putInt("snowStyle",0).commit();
        SceneRenderer r=scene();int full=visiblePixels(r);r.setTypingBudget(true);int typing=visiblePixels(r);
        assertTrue("Typing should retain a visible snow scene",typing>20);
        assertTrue("Typing should draw fewer particles",typing<full);
        Bitmap image=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);
        try{
            r.draw(new Canvas(image),false);
            for(int quarter=0;quarter<4;quarter++){
                int visible=0;
                for(int y=quarter*200;y<(quarter+1)*200;y++)for(int x=0;x<360;x++)
                    if((image.getPixel(x,y)>>>24)>10)visible++;
                assertTrue("Typing must not crop quarter "+quarter,visible>20);
            }
        }finally{image.recycle();}
        r.setTypingBudget(false);assertEquals("The full scene returns after typing",full,visiblePixels(r));
    }
    @Test public void hiddenSparklesDoNotScheduleFramesDuringTyping(){
        prefs.edit().putBoolean("snow",false).putBoolean("lights",false)
            .putBoolean("wallpaperSnow",false).putBoolean("screenSparkles",true)
            .putInt("sparkleDensity",100).commit();
        SceneRenderer r=scene();assertTrue(r.animated());
        r.setTypingBudget(true);assertFalse(r.animated());assertEquals(0,visiblePixels(r));
        r.setTypingBudget(false);assertTrue(r.animated());
    }
}
