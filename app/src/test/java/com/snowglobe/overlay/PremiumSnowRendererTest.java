package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Native Canvas checks of the shipping snow, rather than a mock renderer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PremiumSnowRendererTest {
    private SharedPreferences prefs;
    @Before public void setup(){
        Context context=RuntimeEnvironment.getApplication();
        prefs=context.getSharedPreferences("premium-snow-renderer",Context.MODE_PRIVATE);
        prefs.edit().clear().putBoolean("snow",true).putBoolean("lights",false)
            .putBoolean("screenSparkles",false).putBoolean("edgeOnly",false)
            .putInt("density",90).putInt("size",62).putInt("snowVisibility",100).commit();
    }
    private SceneRenderer scene(int width,int height){
        SceneRenderer renderer=new SceneRenderer(1.5f,new SceneSettings(prefs));
        renderer.resize(width,height,42,30,1.5f);return renderer;
    }
    private Bitmap render(SceneRenderer renderer,int width,int height){
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(image),false);return image;
    }
    private int[] pixels(Bitmap image){
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return pixels;
    }
    private void assertEveryQuarter(Bitmap image){
        int[] values=pixels(image);int[] visible=new int[4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if((values[y*image.getWidth()+x]>>>24)>10)visible[Math.min(3,y*4/image.getHeight())]++;
        for(int q=0;q<4;q++)assertTrue("Visible snow through quarter "+q+": "+visible[q],visible[q]>40);
    }
    private void saveScene(SceneRenderer renderer,String name,int width,int height)throws Exception{
        File directory=new File("build/reports/snow-visuals");assertTrue(directory.isDirectory()||directory.mkdirs());
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(directory,name))){
            renderer.draw(new Canvas(image),true);assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));
        }finally{image.recycle();}
    }
    @Test public void everyFreeSnowStyleHasDistinctFullHeightArtwork()throws Exception{
        int[] fingerprint=new int[3];
        for(int style=0;style<3;style++){
            prefs.edit().putInt("snowStyle",style).putBoolean("reduceMotion",true).commit();
            SceneRenderer renderer=scene(540,1200);Bitmap image=render(renderer,540,1200);
            try{assertEveryQuarter(image);fingerprint[style]=Arrays.hashCode(pixels(image));}
            finally{image.recycle();}
            saveScene(renderer,"snow-style-"+style+"-portrait.png",540,1200);
        }
        assertNotEquals("Soft flakes and crystals need different artwork",fingerprint[0],fingerprint[1]);
        assertNotEquals("Crystals and stars need different artwork",fingerprint[1],fingerprint[2]);
        assertNotEquals(fingerprint[0],fingerprint[2]);
    }
    @Test public void resizedSceneAndSystemKeyboardBudgetRetainTheBottomQuarter()throws Exception{
        prefs.edit().putInt("snowStyle",1).putBoolean("reduceMotion",true).commit();
        SceneRenderer renderer=scene(540,1200);renderer.setTypingBudget(true);
        Bitmap portrait=render(renderer,540,1200);
        try{assertEveryQuarter(portrait);}finally{portrait.recycle();}
        renderer.resize(1200,540,36,42,1.5f);Bitmap landscape=render(renderer,1200,540);
        try{assertEveryQuarter(landscape);}finally{landscape.recycle();}
        renderer.setTypingBudget(false);
        saveScene(renderer,"snow-crystals-landscape.png",1200,540);
    }
    @Test public void charcoalChoiceRetintsTheSoftEdgesAsWellAsTheCore(){
        prefs.edit().putInt("snowStyle",0).putBoolean("reduceMotion",true).commit();
        SceneRenderer renderer=scene(540,1200);Bitmap light=render(renderer,540,1200);
        long lightLuma=weightedLuma(light);light.recycle();
        prefs.edit().putInt("snowShade",2).commit();renderer.apply(new SceneSettings(prefs));
        Bitmap dark=render(renderer,540,1200);
        try{
            assertEveryQuarter(dark);
            assertTrue("Charcoal must not keep a bright halo",weightedLuma(dark)<lightLuma/2);
        }finally{dark.recycle();}
    }
    private long weightedLuma(Bitmap image){
        long sum=0;
        for(int pixel:pixels(image))sum+=(long)(pixel>>>24)*(((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255));
        return sum;
    }
    @Test public void hiddenOrZeroDensitySnowDoesNotKeepAnimating(){
        prefs.edit().putInt("snowVisibility",0).commit();SceneRenderer hidden=scene(540,1200);
        assertFalse(hidden.animated());Bitmap image=render(hidden,540,1200);
        try{for(int pixel:pixels(image))assertEquals(0,pixel>>>24);}finally{image.recycle();}
        prefs.edit().putInt("snowVisibility",100).putInt("density",0).putBoolean("edgeOnly",true).commit();
        assertFalse("Even optional top-edge mode respects zero density",scene(540,1200).animated());
    }
    @Test public void resumingTheClockDoesNotTeleportTheScene(){
        SceneRenderer renderer=scene(540,1200);
        for(int frame=0;frame<30;frame++)renderer.advance(1_000_000_000L+frame*16_666_667L);
        Bitmap before=render(renderer,540,1200);
        renderer.resetClock();renderer.advance(90_000_000_000L);
        Bitmap after=render(renderer,540,1200);
        try{assertTrue("First resumed frame preserves the current snow positions",before.sameAs(after));}
        finally{before.recycle();after.recycle();}
    }
    @Test public void sameElapsedTimeHasConsistentTravelAt30And120Hz()throws Exception{
        SceneRenderer slow=scene(540,1200),fast=scene(540,1200);
        float[] initial=coordinates(slow,"y");long start=1_000_000_000L;
        slow.advance(start);fast.advance(start);
        for(int frame=1;frame<=90;frame++)slow.advance(start+Math.round(frame*1_000_000_000d/30));
        for(int frame=1;frame<=360;frame++)fast.advance(start+Math.round(frame*1_000_000_000d/120));
        float[] slowX=coordinates(slow,"x"),fastX=coordinates(fast,"x");
        float[] slowY=coordinates(slow,"y"),fastY=coordinates(fast,"y");int compared=0;
        // These particles cannot reach a wrap boundary in three seconds. Checking
        // them avoids conflating frame-quantized respawning with travel speed.
        for(int i=0;i<100;i++)if(initial[i]>.2f&&initial[i]<.55f){
            assertEquals("Horizontal drift at particle "+i,slowX[i],fastX[i],.00005f);
            assertEquals("Vertical fall at particle "+i,slowY[i],fastY[i],.00005f);
            assertTrue("Time must actually advance the fall",slowY[i]>initial[i]);compared++;
        }
        assertTrue(compared>=20);
    }
    private float[] coordinates(SceneRenderer renderer,String name)throws Exception{
        Field field=SceneRenderer.class.getDeclaredField(name);field.setAccessible(true);
        return ((float[])field.get(renderer)).clone();
    }
}
