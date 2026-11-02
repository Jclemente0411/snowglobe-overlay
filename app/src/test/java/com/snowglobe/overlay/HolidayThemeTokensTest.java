package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class HolidayThemeTokensTest {
    @Test public void christmasIsTheFreshDefault(){assertEquals(HolidayThemeTokens.CLASSIC,HolidayThemeTokens.initial(null,false));}
    @Test public void honorsExplicitLegacyPixelChoice(){assertEquals(HolidayThemeTokens.PIXEL,HolidayThemeTokens.initial(null,true));assertEquals(HolidayThemeTokens.CANDLELIGHT,HolidayThemeTokens.initial("candlelight",true));}
    @Test public void unknownThemesFallBackSafely(){assertEquals("classic",HolidayThemeTokens.normalize(""));assertEquals("classic",HolidayThemeTokens.normalize(null));assertEquals("classic",HolidayThemeTokens.normalize("made-up"));}
    @Test public void creamIsLightAndChristmasIsDark(){assertFalse(HolidayThemeTokens.palette("candlelight").dark);assertTrue(HolidayThemeTokens.palette("classic").dark);assertTrue(HolidayThemeTokens.palette("evergreen").dark);}
    @Test public void allCustomTextRolesMeetFourPointFiveContrast(){
        for(String id:new String[]{"classic","evergreen","candlelight"}){
            HolidayThemeTokens.Palette p=HolidayThemeTokens.palette(id);
            check(id+" text/background",p.text,p.background);check(id+" text/surface",p.text,p.surface);
            check(id+" muted/surface",p.muted,p.surface);check(id+" muted/raised",p.muted,p.raised);
            check(id+" primary/button",p.onPrimary,p.primary);check(id+" secondary/button",p.onSecondary,p.secondary);
            check(id+" selected container",p.onContainer,p.container);
        }
    }
    @Test public void contrastFormulaHasKnownEndpoints(){assertEquals(21d,HolidayThemeTokens.contrast(0xff000000,0xffffffff),.001);assertEquals(1d,HolidayThemeTokens.contrast(0xff123456,0xff123456),.001);}
    private void check(String message,int fg,int bg){assertTrue(message+": "+HolidayThemeTokens.contrast(fg,bg),HolidayThemeTokens.contrast(fg,bg)>=4.5);}
}
