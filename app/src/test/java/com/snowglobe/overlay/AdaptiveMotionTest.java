package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdaptiveMotionTest {
    @Test public void autoRespectsActualDisplayAndHeat(){
        assertEquals(30,RenderPolicy.fps(3,false,false,0,60));
        assertEquals(60,RenderPolicy.fps(3,false,false,0,120));
        assertEquals(30,RenderPolicy.fps(3,false,false,0,75));
        assertEquals(30,RenderPolicy.fps(3,false,true,0,120));
        assertEquals(15,RenderPolicy.fps(3,false,false,4,120));
        assertEquals(30,RenderPolicy.fps(3,true,false,0,120));
        assertEquals(60,RenderPolicy.fps(1,false,false,0,120));
        assertEquals(120,RenderPolicy.fps(2,false,false,0,120));
    }
    @Test public void vintageGlowIsGentleAndTimeBounded(){
        float min=1f,max=0;
        for(int t=0;t<5000;t++){
            float b=MotionMath.brightness(5,t*.012f,3f,.45f,false);
            assertTrue(b>=.74f&&b<=1.001f);
            min=Math.min(min,b);max=Math.max(max,b);
        }
        assertTrue(max-min>.03f);
        assertEquals(1f,MotionMath.brightness(5,1,2,.5f,true),0.0001f);
    }
}