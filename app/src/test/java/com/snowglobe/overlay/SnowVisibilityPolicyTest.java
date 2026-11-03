package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class SnowVisibilityPolicyTest {
    @Test public void snowVisibilityCanHideOrHighlightWithoutTouchingWindowAlpha(){
        assertEquals(0,SnowVisualPolicy.alpha(200,0f));
        assertEquals(100,SnowVisualPolicy.alpha(200,.5f));
        assertEquals(200,SnowVisualPolicy.alpha(200,1f));
        assertEquals(200,SnowVisualPolicy.alpha(200,2f));
        assertEquals(0,SnowVisualPolicy.alpha(200,Float.NaN));
        assertEquals(.60f,OverlaySafetyPolicy.windowAlpha(.8f),.001f);
    }
    @Test public void visibilityChoicesOfferRealLuminanceContrast(){
        int white=SnowVisualPolicy.color(0),silver=SnowVisualPolicy.color(1),dark=SnowVisualPolicy.color(2);
        assertTrue(((white>>16)&255)>((silver>>16)&255));
        assertTrue(((silver>>16)&255)>((dark>>16)&255));
        assertNotEquals(white,dark);
    }
    @Test public void drawsOnlyNecessaryParticles(){
        assertEquals(0,SnowVisualPolicy.particleCount(120,false,false,false,false,false));
        assertEquals(120,SnowVisualPolicy.particleCount(120,true,false,false,false,false));
        assertEquals(54,SnowVisualPolicy.particleCount(120,true,false,false,false,true));
        assertEquals(48,SnowVisualPolicy.particleCount(0,false,false,true,false,false));
        assertEquals(64,SnowVisualPolicy.particleCount(0,false,false,false,true,false));
    }
    @Test public void bandGeometryIsWithinWindowAndDoesNotMutateSystemThreshold(){
        assertEquals(-1,OverlayWindowPolicy.height(2400,3f,false));
        assertEquals(600,OverlayWindowPolicy.height(2400,3f,true));
        assertEquals(468,OverlayWindowPolicy.height(700,3f,true));
        assertEquals(200,OverlayWindowPolicy.height(200,3f,true));
        assertEquals(-1,OverlayWindowPolicy.height(0,3f,true));
    }
}