package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class PixelSetupStatusTest {
    @Test public void permissionDoesNotPretendEffectsAreRunning(){
        assertEquals("Permission needed",PixelSetupStatus.overlay(false,false,true,""));
        assertEquals("Ready · tap Start",PixelSetupStatus.overlay(true,false,true,""));
        assertEquals("Paused · tap Start",PixelSetupStatus.overlay(true,false,false,""));
        assertEquals("On · hidden while editing",PixelSetupStatus.overlay(true,true,true,""));
        assertEquals("Start failed · retry in Studio",PixelSetupStatus.overlay(true,false,true,"error"));
    }
    @Test public void lockSelectionIsNeverInferredOnOldSystem(){
        assertEquals("Check in Android wallpaper settings",PixelSetupStatus.wallpaper(false,true));
        assertEquals("Choose lock-screen wallpaper",PixelSetupStatus.wallpaper(true,false));
        assertEquals("Selected for lock screen",PixelSetupStatus.wallpaper(true,true));
    }
    @Test public void pixelSignalIsInformationalNotDeviceLock(){
        assertTrue(PixelSetupStatus.likelyPixel("Google","Pixel 11 Pro XL"));
        assertFalse(PixelSetupStatus.likelyPixel("Samsung","Pixel 11 Pro XL"));
        assertFalse(PixelSetupStatus.likelyPixel(null,"Pixel 11"));
    }
}