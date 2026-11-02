package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlaySafetyPolicyTest {
    @Test public void respectsSystemOpacityWithMargin(){assertEquals(.60f,OverlaySafetyPolicy.windowAlpha(.8f),.001);assertEquals(.35f,OverlaySafetyPolicy.windowAlpha(.4f),.001);}
    @Test public void rejectsInvalidThreshold(){assertEquals(0,OverlaySafetyPolicy.windowAlpha(Float.NaN),0);assertEquals(0,OverlaySafetyPolicy.windowAlpha(-1),0);assertEquals(0,OverlaySafetyPolicy.windowAlpha(Float.POSITIVE_INFINITY),0);}
    @Test public void noWindowInStudioLockWallpaperOrScreenOff(){
        assertTrue(OverlaySafetyPolicy.shouldAttach(true,true,false,true,false,false));
        assertFalse(OverlaySafetyPolicy.shouldAttach(true,true,true,true,false,false));
        assertFalse(OverlaySafetyPolicy.shouldAttach(true,true,false,false,false,false));
        assertFalse(OverlaySafetyPolicy.shouldAttach(true,true,false,true,true,false));
        assertFalse(OverlaySafetyPolicy.shouldAttach(true,true,false,true,false,true));
    }
    @Test public void pauseAndPermissionAlwaysWin(){assertFalse(OverlaySafetyPolicy.shouldAttach(false,true,false,true,false,false));assertFalse(OverlaySafetyPolicy.shouldAttach(true,false,false,true,false,false));assertEquals("paused",OverlaySafetyPolicy.state(false,true,false,true,false,false));}
}
