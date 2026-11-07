package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReleaseArtworkTest {
    @Test public void fullResolutionExportProducesRealPortraitPng() throws Exception {
        Bitmap art=WallpaperArt.render(0,WallpaperArt.WIDTH,WallpaperArt.HEIGHT);
        try{
            assertEquals(2160,art.getWidth());assertEquals(3840,art.getHeight());
            assertNotEquals(art.getPixel(1080,80),art.getPixel(1080,3600));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            assertTrue(art.compress(Bitmap.CompressFormat.PNG,100,bytes));
            assertTrue(bytes.size()>20000);
            byte[] encoded=bytes.toByteArray();
            assertEquals(0x89,encoded[0]&255);assertEquals('P',encoded[1]);assertEquals('N',encoded[2]);assertEquals('G',encoded[3]);
        }finally{art.recycle();}
    }
    @Test public void collectionHasDistinctActualArtworkAndSafeIcon() throws Exception {
        File output=new File("build/reports/release-artwork");assertTrue(output.isDirectory()||output.mkdirs());
        int previous=0;
        for(int i=0;i<4;i++){
            Bitmap art=WallpaperArt.render(i,360,640);
            try{
                int sky=art.getPixel(180,20);if(i>0)assertNotEquals(previous,sky);previous=sky;
                try(FileOutputStream file=new FileOutputStream(new File(output,"wallpaper-"+i+".png"))){assertTrue(art.compress(Bitmap.CompressFormat.PNG,100,file));}
                assertEquals(i>=2,HolidayAccess.plusWallpaper(i));
            }finally{art.recycle();}
        }
        Context context=RuntimeEnvironment.getApplication();Drawable icon=context.getDrawable(R.mipmap.ic_launcher);
        assertNotNull(icon);Bitmap bitmap=Bitmap.createBitmap(324,324,Bitmap.Config.ARGB_8888);
        try{
            icon.setBounds(0,0,324,324);icon.draw(new Canvas(bitmap));
            assertNotEquals(0,bitmap.getPixel(162,162));
            try(FileOutputStream file=new FileOutputStream(new File(output,"launcher-icon.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,file));}
        }finally{bitmap.recycle();}
    }
    @Test public void exportRejectsUnboundedDimensions(){
        try{WallpaperArt.render(0,2161,3840);fail("Unbounded allocation");}catch(IllegalArgumentException expected){}
        try{WallpaperArt.render(4,360,640);fail("Unknown design");}catch(IllegalArgumentException expected){}
    }
}
