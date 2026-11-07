package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class RenderPolicyTest {
    @Test public void honorsQualityWithoutPromisingDisplayRate(){assertEquals(30,RenderPolicy.fps(0,false,false,0));assertEquals(60,RenderPolicy.fps(1,false,false,0));assertEquals(120,RenderPolicy.fps(2,false,false,0));}
    @Test public void previewsBatterySaverAndHeatAreBounded(){assertEquals(30,RenderPolicy.fps(2,true,false,0));assertEquals(30,RenderPolicy.fps(2,false,true,0));assertEquals(30,RenderPolicy.fps(2,false,false,2));assertEquals(15,RenderPolicy.fps(2,false,false,4));}
    @Test public void deadlinesAreNonNegativeAndFirstFrameImmediate(){assertEquals(0,RenderPolicy.delayMillis(0,10,30,120));assertEquals(0,RenderPolicy.delayMillis(10,1,30,120));assertTrue(RenderPolicy.delayMillis(1,2,30,120)>0);assertEquals(0,RenderPolicy.delayMillis(1,1_000_000_000,30,120));}
    @Test public void cadenceFitsTheActualDisplay(){
        assertEquals(60,RenderPolicy.fps(2,false,false,0,60));
        assertEquals(90,RenderPolicy.fps(2,false,false,0,90));
        assertEquals(120,RenderPolicy.fps(2,false,false,0,120));
        assertEquals(45,RenderPolicy.fps(1,false,false,0,90));
        assertEquals(60,RenderPolicy.fps(1,false,false,0,119.88f));
        assertEquals(30,RenderPolicy.fps(2,false,true,0,120));
    }
    @Test public void invalidRefreshFallsBackToARealisticCadence(){
        assertEquals(60,RenderPolicy.fps(2,false,false,0,Float.NaN));
        assertEquals(60,RenderPolicy.fps(2,false,false,0,0));
        assertEquals(30,RenderPolicy.fps(3,false,false,0,Float.POSITIVE_INFINITY));
    }
    @Test public void fractionalLowRateDoesNotAddAnExtraRefreshOfDelay(){
        int fps=RenderPolicy.fps(2,false,false,4,144);
        long presented=1_000_000_000L;
        assertFalse(MotionMath.frameDue(presented,presented+62_500_000L,fps)); // Nine refreshes.
        assertTrue(MotionMath.frameDue(presented,presented+69_444_444L,fps));  // Ten refreshes.
    }
}
