package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class RenderPolicyTest {
    @Test public void honorsQualityWithoutPromisingDisplayRate(){assertEquals(30,RenderPolicy.fps(0,false,false,0));assertEquals(60,RenderPolicy.fps(1,false,false,0));assertEquals(120,RenderPolicy.fps(2,false,false,0));}
    @Test public void previewsBatterySaverAndHeatAreBounded(){assertEquals(30,RenderPolicy.fps(2,true,false,0));assertEquals(30,RenderPolicy.fps(2,false,true,0));assertEquals(30,RenderPolicy.fps(2,false,false,2));assertEquals(15,RenderPolicy.fps(2,false,false,4));}
    @Test public void deadlinesAreNonNegativeAndFirstFrameImmediate(){assertEquals(0,RenderPolicy.delayMillis(0,10,30,120));assertEquals(0,RenderPolicy.delayMillis(10,1,30,120));assertTrue(RenderPolicy.delayMillis(1,2,30,120)>0);assertEquals(0,RenderPolicy.delayMillis(1,1_000_000_000,30,120));}
}
