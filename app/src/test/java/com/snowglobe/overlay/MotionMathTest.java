package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionMathTest {
    @Test public void allDirectionsAreUnitVectors(){for(int i=0;i<8;i++){float x=MotionMath.directionX(i),y=MotionMath.directionY(i);assertEquals(1f,x*x+y*y,.00001f);}}
    @Test public void upwardAndSidewaysAreActuallyDistinct(){assertEquals(-1f,MotionMath.directionY(5),0);assertEquals(0f,MotionMath.directionY(3),0);assertEquals(1f,MotionMath.directionX(3),0);}
    @Test public void firstFrameCannotJump(){assertEquals(0f,MotionMath.seconds(0,999999999L),0);}
    @Test public void resumedFrameIsCapped(){assertEquals(.05f,MotionMath.seconds(1,9000000000L),.00001f);}
    @Test public void movementIsTimeBased(){assertEquals(MotionMath.seconds(1,16666667L),2*MotionMath.seconds(1,8333334L),.000001f);}
    @Test public void reducedMotionMakesAllBulbsSteady(){for(int m=0;m<5;m++)for(int t=0;t<30;t++)assertEquals(1f,MotionMath.brightness(m,t,3,.9f,true),0);}
    @Test public void allLightModesStayBounded(){for(int m=0;m<5;m++)for(int t=0;t<2000;t++){float a=MotionMath.brightness(m,t/60f,2,.4f,false);assertTrue(a>=0f&&a<=1f);}}
    @Test public void popupHasSoftEdges(){assertEquals(0f,MotionMath.envelope(0,4),0);assertEquals(0f,MotionMath.envelope(4,4),0);assertTrue(MotionMath.envelope(.1f,4)<.1f);assertEquals(1f,MotionMath.envelope(2,4),.00001f);}
    @Test public void saverCapsQuality(){assertEquals(30,MotionMath.fps(2,true));assertEquals(120,MotionMath.fps(2,false));assertEquals(60,MotionMath.fps(1,false));}
    @Test public void framePacingHonorsVsyncRate(){assertTrue(MotionMath.frameDue(1,8333334,120));assertFalse(MotionMath.frameDue(1,8333334,60));assertTrue(MotionMath.frameDue(1,16666667,60));}
    @Test public void corruptValuesAreClamped(){assertEquals(0,MotionMath.clamp(Float.NaN,0,1),0);assertEquals(1,MotionMath.clamp(999,0,1),0);}
}
